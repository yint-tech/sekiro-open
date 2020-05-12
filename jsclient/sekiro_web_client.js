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


function SekiroClient(wsURL) {
    this.wsURL = wsURL;
    this.handlers = {};
    this.socket = {};
    // check
    if (!wsURL) {
        throw new Error('wsURL can not be empty!!')
    }
    this.webSocketFactory = this.resolveWebSocketFactory();
    this.connect()
}

SekiroClient.prototype.resolveWebSocketFactory = function () {
    if (typeof window === 'object') {
        var theWebSocket = window.WebSocket ? window.WebSocket : window.MozWebSocket;
        return function (wsURL) {

            function WindowWebSocketWrapper(wsURL) {
                this.mSocket = new theWebSocket(wsURL);
            }

            WindowWebSocketWrapper.prototype.close = function () {
                this.mSocket.close();
            };

            WindowWebSocketWrapper.prototype.onmessage = function (onMessageFunction) {
                this.mSocket.onmessage = onMessageFunction;
            };

            WindowWebSocketWrapper.prototype.onopen = function (onOpenFunction) {
                this.mSocket.onopen = onOpenFunction;
            };
            WindowWebSocketWrapper.prototype.onclose = function (onCloseFunction) {
                this.mSocket.onclose = onCloseFunction;
            };

            WindowWebSocketWrapper.prototype.send = function (message) {
                this.mSocket.send(message);
            };

            return new WindowWebSocketWrapper(wsURL);
        }
    }
    if (typeof weex === 'object') {
        // this is weex env : https://weex.apache.org/zh/docs/modules/websockets.html
        try {
            console.log("test webSocket for weex");
            var ws = weex.requireModule('webSocket');
            console.log("find webSocket for weex:" + ws);
            return function (wsURL) {
                try {
                    ws.close();
                } catch (e) {
                }
                ws.WebSocket(wsURL, '');
                return ws;
            }
        } catch (e) {
            console.log(e);
            //ignore
        }
    }
    //TODO support ReactNative
    if (typeof WebSocket === 'object') {
        return function (wsURL) {
            return new theWebSocket(wsURL);
        }
    }
    // weex 和 PC环境的websocket API不完全一致，所以做了抽象兼容
    throw new Error("the js environment do not support websocket");
};

SekiroClient.prototype.connect = function () {
    console.log('sekiro: begin of connect to wsURL: ' + this.wsURL);
    var _this = this;
    // 不check close，让
    // if (this.socket && this.socket.readyState === 1) {
    //     this.socket.close();
    // }
    try {
        this.socket = this.webSocketFactory(this.wsURL);
    } catch (e) {
        console.log("sekiro: create connection failed,reconnect after 2s");
        setTimeout(function () {
            _this.connect()
        }, 2000)
    }

    this.socket.onmessage(function (event) {
        _this.handleSekiroRequest(event.data)
    });

    this.socket.onopen(function (event) {
        console.log('sekiro: open a sekiro client connection')
    });

    this.socket.onclose(function (event) {
        console.log('sekiro: disconnected ,reconnection after 2s');
        setTimeout(function () {
            _this.connect()
        }, 2000)
    });
};

SekiroClient.prototype.handleSekiroRequest = function (requestJson) {
    console.log("receive sekiro request: " + requestJson);
    var request = JSON.parse(requestJson);
    var seq = request['__sekiro_seq__'];

    if (!request['action']) {
        this.sendFailed(seq, 'need request param {action}');
        return
    }
    var action = request['action'];
    if (!this.handlers[action]) {
        this.sendFailed(seq, 'no action handler: ' + action + ' defined');
        return
    }

    var theHandler = this.handlers[action];
    var _this = this;
    try {
        theHandler(request, function (response) {
            try {
                _this.sendSuccess(seq, response)
            } catch (e) {
                _this.sendFailed(seq, "e:" + e);
            }
        }, function (errorMessage) {
            _this.sendFailed(seq, errorMessage)
        })
    } catch (e) {
        console.log("error: " + e);
        _this.sendFailed(seq, ":" + e);
    }
};

SekiroClient.prototype.sendSuccess = function (seq, response) {
    var responseJson;
    if (typeof response == 'string') {
        try {
            responseJson = JSON.parse(response);
        } catch (e) {
            responseJson = {};
            responseJson['data'] = response;
        }
    } else if (typeof response == 'object') {
        responseJson = response;
    } else {
        responseJson = {};
        responseJson['data'] = response;
    }


    if (Array.isArray(responseJson)) {
        responseJson = {
            data: responseJson,
            code: 0
        }
    }

    if (responseJson['code']) {
        responseJson['code'] = 0;
    } else if (responseJson['status']) {
        responseJson['status'] = 0;
    } else {
        responseJson['status'] = 0;
    }
    responseJson['__sekiro_seq__'] = seq;
    var responseText = JSON.stringify(responseJson);
    console.log("response :" + responseText);


    if (responseText.length < 1024 * 6) {
        this.socket.send(responseText);
        return;
    }

    //大报文要分段传输
    var segmentSize = 1024 * 5;
    var i = 0, totalFrameIndex = Math.floor(responseText.length / segmentSize) + 1;

    for (; i < totalFrameIndex; i++) {
        var frameData = JSON.stringify({
                __sekiro_frame_total: totalFrameIndex,
                __sekiro_index: i,
                __sekiro_seq__: seq,
                __sekiro_is_frame: true,
                __sekiro_content: responseText.substring(i * segmentSize, (i + 1) * segmentSize)
            }
        );
        console.log("frame: " + frameData);
        this.socket.send(frameData);
    }

};

SekiroClient.prototype.sendFailed = function (seq, errorMessage) {
    if (typeof errorMessage != 'string') {
        errorMessage = JSON.stringify(errorMessage);
    }
    var responseJson = {};
    responseJson['message'] = errorMessage;
    responseJson['status'] = -1;
    responseJson['__sekiro_seq__'] = seq;
    var responseText = JSON.stringify(responseJson);
    console.log("sekiro: response :" + responseText);
    this.socket.send(responseText)
};

SekiroClient.prototype.registerAction = function (action, handler) {
    if (typeof action !== 'string') {
        throw new Error("an action must be string");
    }
    if (typeof handler !== 'function') {
        throw new Error("a handler must be function");
    }
    console.log("sekiro: register action: " + action);
    this.handlers[action] = handler;
    return this;
};
