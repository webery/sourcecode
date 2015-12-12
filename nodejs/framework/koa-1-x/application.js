
'use strict';

/**
 * Module dependencies.
 */

const isGeneratorFunction = require('is-generator-function');
const debug = require('debug')('koa:application');
const onFinished = require('on-finished');
const response = require('./response');
const compose = require('koa-compose');
const isJSON = require('koa-is-json');
const context = require('./context');
const request = require('./request');
const statuses = require('statuses');
const Cookies = require('cookies');
const accepts = require('accepts');
const Emitter = require('events');
const assert = require('assert');
const Stream = require('stream');
const http = require('http');
const only = require('only');

/**

kao1.x的时候还是使用GeneratorFunction作为中间件,
现在是koa2.x, 依然兼容GF(使用co模块). 但是推荐ES7 的Sync wait

koa和express很大不同的是, 我们的中间件和控制器是(req, res, [next])这样的形式去写函数
但是在koa中完全不需要这样, 而是从context获取(request和Response包含在context中).

**/

/**
 * Expose `Application` class.
 * Inherits from `Emitter.prototype`.
 */

module.exports = class Application extends Emitter {

  /**
   * Initialize a new `Application`.
   *
   * @api public
   */

  constructor() {
    super();

    this.proxy = false;
    this.middleware = [];//中间件很简单,就是用个数组存着,不想express那样router封装
    this.subdomainOffset = 2;
    this.env = process.env.NODE_ENV || 'development';
    this.context = Object.create(context);//生成系统环境对象
    this.request = Object.create(request);//生成系统请求对象
    this.response = Object.create(response);//生成系统响应对象
  }

  /**
   * Shorthand for:
   *
   * @param {Mixed} ...
   * @return {Server}
   * @api public
   */
   //http.createServer(app.callback()).listen(...)生成服务器并启动服务器
  listen() {
    debug('listen');
    const server = http.createServer(this.callback());
    return server.listen.apply(server, arguments);
  }

  /**
   * Return JSON representation.
   * We only bother showing settings.
   *
   * @return {Object}
   * @api public
   */

  toJSON() {
    return only(this, [
      'subdomainOffset',
      'proxy',
      'env'
    ]);
  }

  /**
   * Inspect implementation.
   *
   * @return {Object}
   * @api public
   */

  inspect() {
    return this.toJSON();
  }

  /**
   * Use the given middleware `fn`.
   * 注入中间件(必须是函数)
   * @param {GeneratorFunction} fn
   * @return {Application} self
   * @api public
   */

  use(fn) {
    debug('use %s', fn._name || fn.name || '-');
    if (typeof fn !== 'function') throw new TypeError('middleware must be a function!');
    if (isGeneratorFunction(fn)) throw new TypeError('Support for generators has been removed. See the documentation for examples of how to convert old middleware https://github.com/koajs/koa#example-with-old-signature');
    this.middleware.push(fn);
    return this;
  }

  /**
   * Return a request handler callback
   * for node's native http server.
   * 
   * @return {Function}
   * @api public
   */
//生成一个服务器 request事件回调函数
  callback() {
    const fn = compose(this.middleware);//多个中间件封装成一个集合,请看koa-component模块

    if (!this.listeners('error').length) this.on('error', this.onerror);//注册默认系统异常处理事件

    return (req, res) => {
      res.statusCode = 404;
      const ctx = this.createContext(req, res);//根据当前请求req和res生成当前请求的context
      onFinished(res, ctx.onerror);//
      fn(ctx).then(() => respond(ctx)).catch(ctx.onerror);//执行中间件
    };
  }

  /**
   * Initialize a new context.
   * 每个请求都会根据系统原有的基础context生成新的context,context是整个请求过程的上下文
   * 包含了一个完整请求需要参数,例如req,res,cookie,session等.
   * @api private
   */

  createContext(req, res) {//基础context，基础request，基础Response把每个请求公用的代码都抽了出来，代码复用!
    const context = Object.create(this.context);
    const request = context.request = Object.create(this.request);
    const response = context.response = Object.create(this.response);
    context.app = request.app = response.app = this;//把koa对象加入context
    context.req = request.req = response.req = req;//加入原生request
    context.res = request.res = response.res = res;//加入原生Response
    request.ctx = response.ctx = context;
    request.response = response;
    response.request = request;
    context.onerror = context.onerror.bind(context);
    context.originalUrl = request.originalUrl = req.url;
    context.cookies = new Cookies(req, res, this.keys);
    context.accept = request.accept = accepts(req);
    context.state = {};
    return context;
  }

  /**
   * Default error handler.
   *
   * @param {Error} err
   * @api private
   */

  onerror(err) {
    assert(err instanceof Error, `non-error thrown: ${err}`);

    if (404 == err.status || err.expose) return;
    if (this.silent) return;

    const msg = err.stack || err.toString();
    console.error();
    console.error(msg.replace(/^/gm, '  '));
    console.error();
  }

};

/**
 * Response helper.
 */

function respond(ctx) {
  // allow bypassing koa
  if (false === ctx.respond) return;

  const res = ctx.res;
  if (res.headersSent || !ctx.writable) return;

  let body = ctx.body;
  const code = ctx.status;

  // ignore body
  if (statuses.empty[code]) {
    // strip headers
    ctx.body = null;
    return res.end();
  }

  if ('HEAD' == ctx.method) {
    if (isJSON(body)) ctx.length = Buffer.byteLength(JSON.stringify(body));
    return res.end();
  }

  // status body
  if (null == body) {
    ctx.type = 'text';
    body = ctx.message || String(code);
    ctx.length = Buffer.byteLength(body);
    return res.end(body);
  }

  // responses
  if (Buffer.isBuffer(body)) return res.end(body);
  if ('string' == typeof body) return res.end(body);
  if (body instanceof Stream) return body.pipe(res);

  // body: json
  body = JSON.stringify(body);
  ctx.length = Buffer.byteLength(body);
  res.end(body);
}
