package com.virjar.sekiro.server.netty.http;

import com.virjar.sekiro.api.Multimap;

import io.netty.handler.codec.http.DefaultHttpRequest;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpVersion;

public class RBHttpRequest extends DefaultHttpRequest {
    public RBHttpRequest(HttpVersion httpVersion, HttpMethod method, String uri) {
        super(httpVersion, method, uri);
    }


    public RBHttpRequest(HttpVersion httpVersion, HttpMethod method, String uri, boolean validateHeaders) {
        super(httpVersion, method, uri, validateHeaders);
    }

    private Multimap parameters;

    public Multimap getParameters() {
        return parameters;
    }

    public void setParameters(Multimap parameters) {
        this.parameters = parameters;
    }
}
