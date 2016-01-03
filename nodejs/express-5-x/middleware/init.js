/*!
 * express
 * Copyright(c) 2009-2013 TJ Holowaychuk
 * Copyright(c) 2013 Roman Shtylman
 * Copyright(c) 2014-2015 Douglas Christopher Wilson
 * MIT Licensed
 */

'use strict';

/**
 * Initialization middleware, exposing the
 * request and response to each other, as well
 * as defaulting the X-Powered-By header field.
 *
 * @param {Function} app
 * @return {Function}
 * @api private
 */
//初始化中间件
//1.注入'x-powered-by'请求头
//2.拓展原生request和response对象
exports.init = function(app){
  return function expressInit(req, res, next){
	//如果开启了'x-powered-by',往请求头注入该请求头
    if (app.enabled('x-powered-by')) res.setHeader('X-Powered-By', 'Express');
    req.res = res;
    res.req = req;
    req.next = next;

	//使用原型引用拓展原生的request(IncomingMessage对象的实例)和response(ServerResponse函数对象的实例),
	//相当于把原生的request和response获得app中的request或者response对象的属性和方法
	//在Weber在"没有类的对象"里说过,非函数__proto__指向的是创建它的函数对象的prototype?
	//那这样的话,res不是不能访问到原来的IncomingMessage.prototype东西了吗
	//实际上,app.request是express内部定义的一个对象,它的__proto__指向IncomingMessage.prototype,
	//懂的原型链的小伙伴,应该懂了,详情请看request.js这个文件
    req.__proto__ = app.request;//
    res.__proto__ = app.response;//

    res.locals = res.locals || Object.create(null);

    next();
  };
};

