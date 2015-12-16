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

function compose(middleware){
  if (!Array.isArray(middleware)) throw new TypeError('Middleware stack must be an array!')
  for (const fn of middleware) {
    if (typeof fn !== 'function') throw new TypeError('Middleware must be composed of functions!')
  }

  /**
   * @param {Object} context
   * @return {Promise}
   * @api public
   */
   //
  return function (context, next) {
    // last called middleware #
    let index = -1
    return dispatch(0)
    function dispatch(i) {
      if (i <= index) return Promise.reject(new Error('next() called multiple times'))
      index = i
      const fn = middleware[i] || next
      if (!fn) return Promise.resolve()
      try {
		//1.执行完中间件fn,next作为参数，如果其中一个中间件木有执行next,那就会忽略后面的中间件。
		//2.
        return Promise.resolve(fn(context, function next() {
          return dispatch(i + 1)
        }))
      } catch(err) {
        return Promise.reject(err);
      }
    }
  }
}
