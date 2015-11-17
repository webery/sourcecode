'use strict'

/**
 * Expose compositor.
 */

module.exports = compose;

/**
 * Compose `middleware` returning
 * a fully valid middleware comprised
 * of all those which are passed.
 * 
 * @param {Array} middleware
 * @return {Function}
 * @api public
 */
//把多个中间件封装成一个中间件集合
function compose(middleware){
  if (!Array.isArray(middleware)) throw new TypeError('Middleware stack must be an array!')
  for (const fn of middleware) {//中间件必须全部是函数
    if (typeof fn !== 'function') throw new TypeError('Middleware must be composed of functions!')
  }

  /**
   * @param {Object} context
   * @return {Promise}
   * @api public
   */
  //Promise.resolve(obj)把obj转换成promise对象
  return function (context, next) {
    // last called middleware #
    let index = -1
    return dispatch(0) 
    function dispatch(i) {
      if (i <= index) return Promise.reject(new Error('next() called multiple times'))
      index = i
      const fn = middleware[i] || next
      if (!fn) return Promise.resolve()//中间件执行完毕，结束
      try {//玄机就在这里,调用中间件的时候,传入的是当前请求的context
        return Promise.resolve(fn(context, function next() {//在中间件调用next()，实际上执行的是dispatch()
          return dispatch(i + 1)
        }))
      } catch(err) {
        return Promise.reject(err);
      }
    }
  }
}
