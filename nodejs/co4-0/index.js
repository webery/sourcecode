
/**
co 用来控制Generator的流程, 当GeneratorFunction中有多个yield的时候,
需要手动的多次gen.next(), co模块帮助我们自动管理这个过程.
Generator是一个迭代器,ES6给出了迭代器的标准

使用教程,请看 http://www.ruanyifeng.com/blog/2015/05/co.html
**/


/**
 * slice() reference.
 */

var slice = Array.prototype.slice;

/**
 * Expose `co`.
 */

module.exports = co['default'] = co.co = co;

/**
 * Wrap the given generator `fn` into a
 * function that returns a promise.
 * This is a separate function so that
 * every `co()` call doesn't create a new,
 * unnecessary closure.
 * 把函数GeneratorFunction封装成一个可以返回Promise对象的函数
 * @param {GeneratorFunction} fn
 * @return {Function}
 * @api public
 */

co.wrap = function (fn) {
  createPromise.__generatorFunction__ = fn;
  return createPromise;
  function createPromise() {
    return co.call(this, fn.apply(this, arguments));//实际上使用的还是co函数
  }
};

/**
 * Execute the generator function or a generator
 * and return a promise.
 * 传入一个GeneratorFunction对象,co函数会自动迭代里面的yield
 * @param {Function} fn
 * @return {Promise}
 * @api public
 */
/*
把一个generator封装成一个Promise执行
*/
function co(gen) {
  var ctx = this;//保存this, co函数依然是使用闭包
  var args = slice.call(arguments, 1)//取出第一个参数以外的参数

  // we wrap everything in a promise to avoid promise chaining,
  // which leads to memory leak errors.
  // see https://github.com/tj/co/issues/180
  return new Promise(function(resolve, reject) {
    if (typeof gen === 'function') gen = gen.apply(ctx, args);//gen可以是一个普通函数,普通函数会直接调用
    if (!gen || typeof gen.next !== 'function') return resolve(gen);//如果gen不存在或者gen不是迭代器,直接执行

    onFulfilled();//第一次onFulfilled()调用！后面的调用由next()控制

    /**
     * @param {Mixed} res
     * @return {Promise}
     * @api private
     */
	 
	//一次调用，会执行一次gen.next(),并调用next()处理结果
    function onFulfilled(res) {
      var ret;
      try {
        ret = gen.next(res);
      } catch (e) {
        return reject(e);
      }
      next(ret);
    }

    /**
     * @param {Error} err
     * @return {Promise}
     * @api private
     */

    function onRejected(err) {
      var ret;
      try {
        ret = gen.throw(err);//往GeneratorFunction中抛异常
      } catch (e) {
        return reject(e);
      }
      next(ret);
    }

    /**
     * Get the next value in the generator,
     * return a promise.
     * 每个next, 对应于gen.next(), 也就是执行一个yield. 这个过程是不是有点像connect中的next()呢
     * @param {Object} ret
     * @return {Promise}
     * @api private
     */
	//一次onFulfilled调用表示，一次gen.next();
	//而一次next()表示处理上一次gen.next()的结果，如果gen遍历完成，执行resolve()
	//否则，调用onFulfilled执行下一次gen.next()。next()相当于gen遍历的控制器!
	//注意，第一次onFulfilled调用不是next()完成的,因为必须执行一次，gen.next()才会有返回值
    function next(ret) {
      if (ret.done) return resolve(ret.value);//遍历完成，执行resolve，表示该generator执行完成。
      var value = toPromise.call(ctx, ret.value);//把gen.next()返回的值转换成Promise对象
      if (value && isPromise(value)) return value.then(onFulfilled, onRejected);//使用Promise的方式，执行下一次onFulfilled
	  //如果gen.next()返回的不是下面的类型，抛出异常
      return onRejected(new TypeError('You may only yield a function, promise, generator, array, or object, '
        + 'but the following object was passed: "' + String(ret.value) + '"'));
    }
  });
}

/**
 * Convert a `yield`ed value into a promise.
 * 
 * @param {Mixed} obj
 * @return {Promise}
 * @api private
 */
//把obj转换成Promise. 这个函数集中了后面多种把obj转换成Promise的函数
//需要注意的是，除了if选中的情况外，其他情况是不会转换成Promise对象的。
function toPromise(obj) {
  if (!obj) return obj;
  if (isPromise(obj)) return obj;
  if (isGeneratorFunction(obj) || isGenerator(obj)) return co.call(this, obj);
  if ('function' == typeof obj) return thunkToPromise.call(this, obj);
  if (Array.isArray(obj)) return arrayToPromise.call(this, obj);
  if (isObject(obj)) return objectToPromise.call(this, obj);
  return obj;
}

/**
 * Convert a thunk to a promise.
 * 
 * @param {Function}
 * @return {Promise}
 * @api private
 */
//把thunk对象转换成Promise对象
function thunkToPromise(fn) {
  var ctx = this;
  return new Promise(function (resolve, reject) {
    fn.call(ctx, function (err, res) {
      if (err) return reject(err);
      if (arguments.length > 2) res = slice.call(arguments, 1);
      resolve(res);
    });
  });
}

/**
 * Convert an array of "yieldables" to a promise.
 * Uses `Promise.all()` internally.
 * 
 * @param {Array} obj
 * @return {Promise}
 * @api private
 */
//把数组中的全部元素转换成Promise对象
function arrayToPromise(obj) {
  return Promise.all(obj.map(toPromise, this));
}

/**
 * Convert an object of "yieldables" to a promise.
 * Uses `Promise.all()` internally.
 * 
 * @param {Object} obj
 * @return {Promise}
 * @api private
 */
//把普通对象转换成Promise对象
function objectToPromise(obj){
  var results = new obj.constructor();
  var keys = Object.keys(obj);
  var promises = [];
  for (var i = 0; i < keys.length; i++) {
    var key = keys[i];
    var promise = toPromise.call(this, obj[key]);
    if (promise && isPromise(promise)) defer(promise, key);
    else results[key] = obj[key];
  }
  return Promise.all(promises).then(function () {
    return results;
  });

  function defer(promise, key) {
    // predefine the key in the result
    results[key] = undefined;
    promises.push(promise.then(function (res) {
      results[key] = res;
    }));
  }
}

/**
 * Check if `obj` is a promise.
 * 
 * @param {Object} obj
 * @return {Boolean}
 * @api private
 */
//判断obj是不是一个Promise实例对象
function isPromise(obj) {
  return 'function' == typeof obj.then;
}

/**
 * Check if `obj` is a generator.
 * 
 * @param {Mixed} obj
 * @return {Boolean}
 * @api private
 */
//判断obj是不是生Generator实例对象
function isGenerator(obj) {
  return 'function' == typeof obj.next && 'function' == typeof obj.throw;
}

/**
 * Check if `obj` is a generator function.
 * 
 * @param {Mixed} obj
 * @return {Boolean}
 * @api private
 */
//判断obj是不是GeneratorFunction实例对象
function isGeneratorFunction(obj) {
  var constructor = obj.constructor;
  if (!constructor) return false;
  if ('GeneratorFunction' === constructor.name || 'GeneratorFunction' === constructor.displayName) return true;
  return isGenerator(constructor.prototype);
}

/**
 * Check for plain object.
 *
 * @param {Mixed} val
 * @return {Boolean}
 * @api private
 */
//判断val是不是Function Object的实例对象
//通过判断val原型链上的构造器是不是Object.
function isObject(val) {
  return Object == val.constructor;
}
