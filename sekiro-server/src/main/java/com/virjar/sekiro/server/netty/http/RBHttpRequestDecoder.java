package com.virjar.sekiro.server.netty.http;

import io.netty.handler.codec.http.DefaultHttpRequest;
import io.netty.handler.codec.http.HttpMessage;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpRequestDecoder;
import io.netty.handler.codec.http.HttpVersion;

public class RBHttpRequestDecoder extends HttpRequestDecoder {
    @Override
    protected HttpMessage createMessage(String[] initialLine) throws Exception {
        //initialLine       GET     /uri    HTTP/1.1

        HttpMethod method = HttpMethod.valueOf(initialLine[0]);

        HttpVersion version = HttpVersion.valueOf(initialLine[2]);

        String uri = initialLine[1];

        return new DefaultHttpRequest(version, method, uri, validateHeaders);
    }
}
