/*!
 * connect
 * Copyright(c) 2010 Sencha Inc.
 * Copyright(c) 2011 TJ Holowaychuk
 * Copyright(c) 2015 Douglas Christopher Wilson
 * MIT Licensed
 */

'use strict';

/**
 * Module dependencies.
 * @private
 */

var debug = require('debug')('connect:dispatcher');
var EventEmitter = require('events').EventEmitter;
var finalhandler = require('finalhandler');//Node.js function to invoke as the final step to respond to HTTP request
var http = require('http');
var merge = require('utils-merge');
var parseUrl = require('parseurl');

/**
 * Module exports.
 * @public
 */

module.exports = createServer;

/**
 * Module variables.
 * @private
 */

var env = process.env.NODE_ENV || 'development';
var proto = {};

/* istanbul ignore next */
//八卦一下process.nextTick 和setImmediate http://www.oschina.net/translate/understanding-process-next-tick?print
var defer = typeof setImmediate === 'function'
  ? setImmediate
  : function(fn){ process.nextTick(fn.bind.apply(fn, arguments)) }

/**
 * Create a new connect server.
 *
 * @return {function}
 * @public
 */
//对外接口,返回的是一个名为app的闭包
function createServer() {
  function app(req, res, next){ app.handle(req, res, next); }//app最后注册为Server的request事件监听函数
  merge(app, proto);//把proto的原型拷贝到app(相当于继承一样)
  merge(app, EventEmitter.prototype);//相当于app继承了EventEmitter
  app.route = '/';//app拦截的URL路径,'/'表示拦截所有的请求路径
  app.stack = [];//中间件存储数组, 以layer的封装形式保存,后面会看到layer就是{path,handler}的结构
  return app;
}

/**
 * Utilize the given middleware `handle` to the given `route`,
 * defaulting to _/_. This "route" is the mount-point for the
 * middleware, when given a value other than _/_ the middleware
 * is only effective when that segment is present in the request's
 * pathname.
 *
 * For example if we were to mount a function at _/admin_, it would
 * be invoked on _/admin_, and _/admin/settings_, however it would
 * not be invoked for _/_, or _/posts_.
 *
 * @param {String|Function|Server} route, callback or server
 * @param {Function|Server} callback or server
 * @return {Server} for chaining
 * @public
 */
//注册中间件
proto.use = function use(route, fn) {
  var handle = fn;
  var path = route;

  // default route to '/'
  if (typeof route !== 'string') {
    handle = route;//中间件的默认拦截路径为'/'根路径
    path = '/';
  }

  // wrap sub-apps
  if (typeof handle.handle === 'function') {
    var server = handle;
    server.route = path;
    handle = function (req, res, next) {
      server.handle(req, res, next);
    };
  }

  // wrap vanilla http.Servers
  if (handle instanceof http.Server) {
    handle = handle.listeners('request')[0];
  }

  // strip trailing slash
  //如果中间件的路径最后一个字符是'/'就把它去掉
  if (path[path.length - 1] === '/') {
    path = path.slice(0, -1);
  }

  // add the middleware
  debug('use %s %s', path || '/', handle.name || 'anonymous');
  //保存layer中间件,layer包含两个属性,route:也就是路径,handle也就是处理器
  //多个路径对应一个处理器
  //需要注意的是:这里的path不能达到复杂的匹配,它是字符串,不是正则表达式,不能实现/user/:id这样的restful匹配
  //我们可以使用一个路由器中间件注入app来实现类似SpringMCV的DispatcherServlet的功能
  //其实,这就是再处理URL和控制器映射的问题
  //所以,中间件是根据path路径一个个的遍历数组,如果请求路径和path匹配的话,就会调用这个中间件,否则跳过去
  this.stack.push({ route: path, handle: handle });

  return this;
};

/**
 * Handle server requests, punting them down
 * the middleware stack.
 *
 * @private
 */
//处理http请求,handle其实就是中间件的调度起点和中心,由它调用第一个next,
//每一个next表示了一个中间件,然后next会一个接着一个执行,最终执行完成之后,把控制权交回app,也就是done来处理
proto.handle = function handle(req, res, out) {
  var index = 0;//
  var protohost = getProtohost(req.url) || '';//获取协议和域名
  var removed = '';
  var slashAdded = false;
  var stack = this.stack;

  // final function handler
  //当所有的中间件执行完成,done就被执行
  var done = out || finalhandler(req, res, {
    env: env,
    onerror: logerror
  });

  // store the original URL
  //保存原生的请求url
  req.originalUrl = req.originalUrl || req.url;
  
  //next闭包,相当于一个layer中间件的封装.其实它的功能很简单,首先,取出下一个要处理的中间件,
  //每次next表示处理一个路由，或者完成所有路由，交给APP处理
  //然后从layer中间件把路径和处理器拿出来,
  //然后委托call函数调用处理器.然后呢......用过express的孩子知道,我们经常会在Controller执行完成后,
  //调用next(),这样就会调用下一个中间件了
  //next()的调用机制,有点像Java中的FilterChain或者Tomcat中的管道.
  //JS是单线程的，不必担心性能问题
  function next(err) {
    if (slashAdded) {
      req.url = req.url.substr(1);
      slashAdded = false;
    }

    if (removed.length !== 0) {
      req.url = protohost + removed + req.url.substr(protohost.length);
      removed = '';
    }

    // next callback
	//this.stack存储在这个数组中,所以会根据数组的下标去遍历每一个中间件,这样就实现了过滤器的效果
    var layer = stack[index++];

    // all done
	//若stack中的中间件全部执行完成,就交给app接着处理
	//为什么要异步调用嗯
    if (!layer) {
      defer(done, err);
      return;
    }

    // route data
    var path = parseUrl(req).pathname || '/';
    var route = layer.route;

    // skip this layer if the route doesn't match
	//和我们前面说的一样,如果请求路径和中间件的路径不一致就跳过去
	//connect的路由，只能简单的匹配路径
    if (path.toLowerCase().substr(0, route.length) !== route.toLowerCase()) {
      return next(err);
    }

    // skip if route match does not border "/", ".", or end
    var c = path[route.length];
    if (c !== undefined && '/' !== c && '.' !== c) {
      return next(err);
    }

    // trim off the part of the url that matches the route
    if (route.length !== 0 && route !== '/') {
      removed = route;
      req.url = protohost + req.url.substr(protohost.length + removed.length);

      // ensure leading slash
      if (!protohost && req.url[0] !== '/') {
        req.url = '/' + req.url;
        slashAdded = true;
      }
    }

    // call the layer handle
	//委托call来调用中间件的处理器
    call(layer.handle, route, err, req, res, next);
  }

  next();
};

/**
 * Listen for connections.
 *
 * This method takes the same arguments
 * as node's `http.Server#listen()`.
 *
 * HTTP and HTTPS:
 *
 * If you run your application both as HTTP
 * and HTTPS you may wrap them individually,
 * since your Connect "server" is really just
 * a JavaScript `Function`.
 *
 *      var connect = require('connect')
 *        , http = require('http')
 *        , https = require('https');
 *
 *      var app = connect();
 *
 *      http.createServer(app).listen(80);
 *      https.createServer(options, app).listen(443);
 *
 * @return {http.Server}
 * @api public
 */

proto.listen = function listen() {
	//把app注入真正的http server对象中function app(req, res, next){ app.handle(req, res, next); }
	//app其实就是一个回调函数,函数对象就是这么抓狂!又能new对象又能做函数^_^
  var server = http.createServer(this);
  return server.listen.apply(server, arguments);//arguments被最新的ES标准抛弃了...
};

/**
 * Invoke a route handle.
 * @private
 */
//next委托call调用中间件,中间件分为：异常处理中间件和非异常中间件。
function call(handle, route, err, req, res, next) {
  var arity = handle.length;
  var error = err;
  var hasError = Boolean(err);

  debug('%s %s : %s', handle.name || '<anonymous>', route, req.originalUrl);

  try {
    if (hasError && arity === 4) {
      // error-handling middleware
	  //异常处理中间件
      handle(err, req, res, next);
      return;
    } else if (!hasError && arity < 4) {
      // request-handling middleware
	  //非异常中间件
      handle(req, res, next);
      return;
    }
  } catch (e) {
    // replace the error
    error = e;//记录异常，交给下一个中间件，直道遇到异常中间件，或者新的异常会覆盖新的异常
  }

  // continue
  next(error);//执行下一个中间件
}

/**
 * Log error using console.error.
 *
 * @param {Error} err
 * @private
 */
//打印异常日志
function logerror(err) {
  if (env !== 'test') console.error(err.stack || err.toString());
}

/**
 * Get get protocol + host for a URL.
 *
 * @param {string} url
 * @private
 */
//获取URL的协议和服务器域名,例如https://www.google.com
function getProtohost(url) {
  if (url.length === 0 || url[0] === '/') {
    return undefined;
  }

  var searchIndex = url.indexOf('?');
  var pathLength = searchIndex !== -1
    ? searchIndex
    : url.length;
  var fqdnIndex = url.substr(0, pathLength).indexOf('://');

  return fqdnIndex !== -1
    ? url.substr(0, url.indexOf('/', 3 + fqdnIndex))
    : undefined;
}
