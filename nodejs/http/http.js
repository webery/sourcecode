'use strict';

const util = require('util');
const internalUtil = require('internal/util');
const EventEmitter = require('events');

/**

http模块

主要包含http服务器Server，Request，Response

**/

//IncomingMessage是请求Message的封装,对应request
exports.IncomingMessage = require('_http_incoming').IncomingMessage;


const common = require('_http_common');
exports.METHODS = common.methods.slice().sort();//暴露http methods

//OutgoingMessage是响应Message的封装,对应Response
exports.OutgoingMessage = require('_http_outgoing').OutgoingMessage;


const server = require('_http_server');
exports.ServerResponse = server.ServerResponse;//这个Response就是我们得到的那个Response
exports.STATUS_CODES = server.STATUS_CODES;


const agent = require('_http_agent');
const Agent = exports.Agent = agent.Agent;//用于把套接字做成资源池，用于HTTP客户端请求
exports.globalAgent = agent.globalAgent;//超全局的代理实例，是http客户端的默认请求

const client = require('_http_client');
const ClientRequest = exports.ClientRequest = client.ClientRequest;//表示nodejs发送http请求的封装

//取代原来的Client
exports.request = function(options, cb) {
  return new ClientRequest(options, cb);
};

exports.get = function(options, cb) {
  var req = exports.request(options, cb);
  req.end();
  return req;
};

exports._connectionListener = server._connectionListener;
const Server = exports.Server = server.Server;

//生成http服务器
exports.createServer = function(requestListener) {
  return new Server(requestListener);
};


// Legacy Interface
//遗留的接口
function Client(port, host) {
  if (!(this instanceof Client)) return new Client(port, host);
  EventEmitter.call(this);

  host = host || 'localhost';
  port = port || 80;
  this.host = host;
  this.port = port;
  this.agent = new Agent({ host: host, port: port, maxSockets: 1 });
}
util.inherits(Client, EventEmitter);
Client.prototype.request = function(method, path, headers) {
  var self = this;
  var options = {};
  options.host = self.host;
  options.port = self.port;
  if (method[0] === '/') {
    headers = path;
    path = method;
    method = 'GET';
  }
  options.method = method;
  options.path = path;
  options.headers = headers;
  options.agent = self.agent;
  var c = new ClientRequest(options);
  c.on('error', function(e) {
    self.emit('error', e);
  });
  // The old Client interface emitted 'end' on socket end.
  // This doesn't map to how we want things to operate in the future
  // but it will get removed when we remove this legacy interface.
  c.on('socket', function(s) {
    s.on('end', function() {
      if (self._decoder) {
        var ret = self._decoder.end();
        if (ret)
          self.emit('data', ret);
      }
      self.emit('end');
    });
  });
  return c;
};
//下面的已经抛弃
exports.Client = internalUtil.deprecate(Client, 'http.Client is deprecated.');

exports.createClient = internalUtil.deprecate(function(port, host) {
  return new Client(port, host);
}, 'http.createClient is deprecated. Use http.request instead.');