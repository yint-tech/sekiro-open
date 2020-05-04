/*
  Copyright (C) 2020 virjar <virjar@virjar.com> for https://github.com/virjar/sekiro

  Redistribution and use in source and binary forms, with or without
  modification, are permitted provided that the following conditions are met:

    * Redistributions of source code must retain the above copyright
      notice, this list of conditions and the following disclaimer.
    * Redistributions in binary form must reproduce the above copyright
      notice, this list of conditions and the following disclaimer in the
      documentation and/or other materials provided with the distribution.

  THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
  AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
  IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
  ARE DISCLAIMED. IN NO EVENT SHALL <COPYRIGHT HOLDER> BE LIABLE FOR ANY
  DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
  (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
  LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
  ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
  (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF
  THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
*/

function SekiroClient (wsURL) {
  this.wsURL = wsURL
  this.handlers = {}
  this.socket = {}
  // check
  if (!wsURL) {
    throw new Error('wsURL can not be empty!!')
  }
  this.WebSocket = window.WebSocket ? window.WebSocket : window.MozWebSocket
  this.connect()
}

SekiroClient.prototype.connect = function () {
  console.log('begin of connect to wsURL: ' + this.wsURL)
  this.socket = new this.WebSocket(this.wsURL);
  var _this = this;
  this.socket.onmessage = function (event) {
    _this.handleSekiroRequest(event.data)
  }

  this.socket.onopen = function (event) {
    console.log('open a sekiro client connection')
  }

  this.socket.onclose = function (event) {
    console.log('connection disconnected ,reconnection after 20s')
    setTimeout(function () {
      _this.connect()
    }, 2000)
  }
}

SekiroClient.prototype.handleSekiroRequest = function (requestJson) {
  var request = JSON.parse(requestJson)
  var seq  = request['__sekiro_seq__'];

  if (!request['action']) {
    this.sendFailed(seq,'need request param {action}')
    return
  }
  var action = request['action']
  if (!this.handlers[action]) {
    this.sendFailed(seq,'no action handler: ' + action + ' defined')
    return
  }
  
  var theHandler = this.handlers[action]
  var _this = this
  theHandler(request, function (response) {
    _this.sendSuccess(seq,response)
  }, function (errorMessage) {
    _this.sendFailed(seq,errorMessage)
  })
}

SekiroClient.prototype.sendSuccess = function (seq,response) {
  var responseJson ;
  if(typeof response == 'string'){
    try{
      responseJson = JSON.parse(response);
    }catch(e){
      responseJson = {};
      responseJson['data'] = response;
    }
  }
  else if(typeof response=='object'){
    responseJson = response;
  }else{
    responseJson = {};
    responseJson['data'] = response;
  }
  
  if(responseJson['code']){
    responseJson['code'] =0;
  }else if(responseJson['status']){
    responseJson['status'] =0;
  }else{
    responseJson['status'] =0;
  }
  responseJson['__sekiro_seq__'] = seq;
  var responseText = JSON.stringify(responseJson);
  console.log("response :"+ responseText);
  this.socket.send(responseText)
}

SekiroClient.prototype.sendFailed = function (seq,errorMessage) {
  if(typeof errorMessage !='string'){
    errorMessage = JSON.stringify(errorMessage);
  }
  var responseJson = {};
  responseJson['message'] = errorMessage;
  responseJson['status'] = -1;
  responseJson['__sekiro_seq__'] = seq;
  var responseText = JSON.stringify(responseJson);
  console.log("response :"+ responseText);
  this.socket.send(responseText)
}

SekiroClient.prototype.registerAction = function(action,handler){
  if(typeof action !=='string'){
      throw new Error("a action must be string");
  }
  if(typeof handler !=='function'){
    throw new Error("a action must be function");
}
  this.handlers[action] = handler;
  return this;
}

//window.SekiroClient = SekiroClient();