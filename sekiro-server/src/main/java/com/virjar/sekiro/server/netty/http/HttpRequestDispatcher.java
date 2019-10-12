package com.virjar.sekiro.server.netty.http;


import com.virjar.sekiro.server.netty.http.msg.DefaultHttpResponse;

import org.apache.commons.lang3.StringUtils;

import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.HttpMethod;

public class HttpRequestDispatcher extends SimpleChannelInboundHandler<DefaultFullHttpRequest> {


    @Override
    protected void channelRead0(ChannelHandlerContext channelHandlerContext, DefaultFullHttpRequest request) throws Exception {

        String uri = request.getUri();
        HttpMethod method = request.getMethod();


        String url = uri;
        if (uri.contains("?")) {
            int index = uri.indexOf("?");
            url = uri.substring(0, index);
            String query = uri.substring(index);
            //   httpSekiroRequest.setParameters(Multimap.parseQuery(query));
        } else {
            //  httpSekiroRequest.setParameters(Multimap.emptyMap());
        }

        if (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }
        if (!url.startsWith("/")) {
            url = "/" + url;
        }

        //sekiro的NIO http，只支持invoke接口，其他接口请走springBoot
        if (!StringUtils.equalsAnyIgnoreCase(url, "/invoke")) {
            //404
            Channel channel = channelHandlerContext.channel();
            channel.write(DefaultHttpResponse.notFound);
            channel.writeAndFlush(DefaultHttpResponse.notFound.contentByteData).addListener(ChannelFutureListener.CLOSE);
            return;
        }


        //create a request
        ContentType contentType = ContentType.from(request.headers().get(HeaderNameValue.CONTENT_TYPE));
        if (contentType == null && !method.equals(HttpMethod.GET)) {
            //不识别的请求类型
            Channel channel = channelHandlerContext.channel();
            channel.write(DefaultHttpResponse.badRequest);
            channel.writeAndFlush(DefaultHttpResponse.badRequest.contentByteData).addListener(ChannelFutureListener.CLOSE);
            return;
        }

        //application/x-www-form-urlencoded
        //application/json
        if (contentType != null) {
            if (!"application/x-www-form-urlencoded".equalsIgnoreCase(contentType.getMimeType())
                    && !"application/json".equalsIgnoreCase(contentType.getMimeType())) {
                //  httpSekiroResponse.failed("sekiro framework only support contentType:application/x-www-form-urlencoded | application/json, now is: " + contentType.getMimeType());
                return;
            }
        }

        //now build request
        //request.

    }
}
