
/**
 * Module dependencies.
 */

var assert = require('assert');


/**
要聊thunkify模块,先了解thunk函数
关于thunk函数,请看这篇博客 http://www.ruanyifeng.com/blog/2015/05/thunk.html

thunkify用于Generator的自动执行. 类似的有co.js模块

把一个普通的函数转换成一个thunk函数

例如
function fn(arg1, arg2, arg3, callback) {
	//
}

var thunk = thunkify(fn);
thunk(arg1, arg2, arg3)(callback);
**/

/**
 * Expose `thunkify()`.
 */

module.exports = thunkify;

/**
 * Wrap a regular callback `fn` as a thunk.
 *
 * @param {Function} fn
 * @return {Function}
 * @api public
 */

function thunkify(fn){
  assert('function' == typeof fn, 'function required');

  return function(){
    var args = new Array(arguments.length);//记录参数,最后一个参数是最后的回调函数
    var ctx = this;//保存this,使得调用fn的时候,this还是一致的

    for(var i = 0; i < args.length; ++i) {
      args[i] = arguments[i];
    }

    return function(done){
      var called;

      args.push(function(){
        if (called) return;//done,回调函数只能执行一次
        called = true;
        done.apply(null, arguments);//为什么传入的是null?
      });

      try {
        fn.apply(ctx, args);//绕了一个弯,最终还是直接fn(参数)
      } catch (err) {
        done(err);
      }
    }
  }
};
