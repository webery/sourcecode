/*!
 * express
 * Copyright(c) 2009-2013 TJ Holowaychuk
 * Copyright(c) 2013 Roman Shtylman
 * Copyright(c) 2014-2015 Douglas Christopher Wilson
 * MIT Licensed
 */

'use strict';

/**
lib文件夹下都是express框架的核心.
express.js是整个express框架的对外接口和整合
通过express.js对application.js request.js response.js和其他中间件的整合,
模块化思想吧..........

express的工作流程

app.handle => router.handle
1.middleware
2.route.dispatch -> middleware

**/

/**
 * Module dependencies.
 */

var EventEmitter = require('events').EventEmitter;
var mixin = require('merge-descriptors');
var proto = require('./application');
var Route = require('./router/route');
var Router = require('./router');
var req = require('./request');
var res = require('./response');

/**
 * Expose `createApplication()`.
 */

exports = module.exports = createApplication;

/**
 * Create an express application.
 *
 * @return {Function}
 * @api public
 */
//最早的时候express其实是使用connect模块的,但是现在express把connect代码"直接融入"了
//核心架构风格还是差不多滴
function createApplication() {
  var app = function(req, res, next) {
    app.handle(req, res, next);
  };

  //这个和merge不一样,不会覆盖
  mixin(app, EventEmitter.prototype, false);//拓展事件功能
  mixin(app, proto, false);//proto就是application.js中的app对象

  app.request = { __proto__: req, app: app };
  app.response = { __proto__: res, app: app };
  app.init();//初始化
  return app;
}

/**
 * Expose the prototypes.
 */

exports.application = proto;
exports.request = req;
exports.response = res;

/**
 * Expose constructors.
 */
//暴露路由器
exports.Route = Route;
exports.Router = Router;

/**
 * Expose middleware
 */

exports.query = require('./middleware/query');
exports.static = require('serve-static');

/**
 * Replace removed middleware with an appropriate error message.
 */

[
  'json',
  'urlencoded',
  'bodyParser',
  'compress',
  'cookieSession',
  'session',
  'logger',
  'cookieParser',
  'favicon',
  'responseTime',
  'errorHandler',
  'timeout',
  'methodOverride',
  'vhost',
  'csrf',
  'directory',
  'limit',
  'multipart',
  'staticCache',
].forEach(function (name) {
  Object.defineProperty(exports, name, {
    get: function () {
      throw new Error('Most middleware (like ' + name + ') is no longer bundled with Express and must be installed separately. Please see https://github.com/senchalabs/connect#middleware.');
    },
    configurable: true
  });
});
