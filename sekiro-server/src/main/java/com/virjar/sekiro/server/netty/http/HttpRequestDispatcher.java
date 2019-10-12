package com.virjar.sekiro.server.netty.http;


import com.virjar.sekiro.api.Multimap;
import com.virjar.sekiro.server.netty.ChannelRegistry;
import com.virjar.sekiro.server.netty.NatClient;
import com.virjar.sekiro.server.netty.http.msg.DefaultHttpResponse;
import com.virjar.sekiro.server.util.CommonUtil;

import org.apache.commons.lang3.StringUtils;

import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;

import external.com.alibaba.fastjson.JSONException;
import external.com.alibaba.fastjson.JSONObject;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.HttpMethod;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class HttpRequestDispatcher extends SimpleChannelInboundHandler<DefaultFullHttpRequest> {


    @Override
    protected void channelRead0(ChannelHandlerContext channelHandlerContext, DefaultFullHttpRequest request) throws Exception {

        String uri = request.getUri();
        HttpMethod method = request.getMethod();


        String url = uri;
        String query = null;
        if (uri.contains("?")) {
            int index = uri.indexOf("?");
            url = uri.substring(0, index);
            query = uri.substring(index);
        }
        if (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }
        if (!url.startsWith("/")) {
            url = "/" + url;
        }

        //sekiro的NIO http，只支持invoke接口，其他接口请走springBoot
        if (!StringUtils.equalsAnyIgnoreCase(url, "/asyncInvoke")) {
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
        if (contentType == null) {
            contentType = ContentType.from("application/x-www-form-urlencoded;charset=utf8");
        }

        //application/x-www-form-urlencoded
        //application/json

        if (!"application/x-www-form-urlencoded".equalsIgnoreCase(contentType.getMimeType())
                && !"application/json".equalsIgnoreCase(contentType.getMimeType())) {
            String errorMessage = "sekiro framework only support contentType:application/x-www-form-urlencoded | application/json, now is: " + contentType.getMimeType();
            DefaultHttpResponse contentTypeNotSupportMessage = new DefaultHttpResponse(errorMessage);

            Channel channel = channelHandlerContext.channel();
            channel.write(contentTypeNotSupportMessage);
            channel.writeAndFlush(contentTypeNotSupportMessage.contentByteData).addListener(ChannelFutureListener.CLOSE);
            return;
            //  httpSekiroResponse.failed("sekiro framework only support contentType:application/x-www-form-urlencoded | application/json, now is: " + contentType.getMimeType());
            // return;
        }


        //now build request
        String postBody = null;
        if (method.equals(HttpMethod.POST)) {
            byte[] array = request.content().array();
            String charset = contentType.getCharset();

            if (charset == null) {
                charset = StandardCharsets.UTF_8.name();
            }

            if (array != null) {
                try {
                    postBody = new String(array, charset);
                } catch (UnsupportedEncodingException e) {
                    postBody = new String(array);
                }
            }
        }

        if (StringUtils.isBlank(postBody) && StringUtils.isBlank(query)) {
            //TODO
            log.warn("request body empty");
            Channel channel = channelHandlerContext.channel();
            channel.write(DefaultHttpResponse.badRequest);
            channel.writeAndFlush(DefaultHttpResponse.badRequest.contentByteData).addListener(ChannelFutureListener.CLOSE);
            return;
        }

        String group;
        String requestBody;
        String bindClient;
        if ("application/x-www-form-urlencoded".equalsIgnoreCase(contentType.getMimeType())) {
            Multimap nameValuePairs = Multimap.parseQuery(query);
            nameValuePairs.putAll(Multimap.parseQuery(postBody));
            requestBody = CommonUtil.joinListParam(nameValuePairs);
            group = nameValuePairs.getString("group");
            bindClient = nameValuePairs.getString("bindClient");
        } else {

            try {
                JSONObject jsonObject = JSONObject.parseObject(postBody);
                jsonObject.putAll(Multimap.parseQuery(query));
                group = jsonObject.getString("group");
                requestBody = jsonObject.toJSONString();
                bindClient = jsonObject.getString("bindClient");
            } catch (JSONException e) {
                //TODO
                log.warn("request body empty");
                Channel channel = channelHandlerContext.channel();
                channel.write(DefaultHttpResponse.badRequest);
                channel.writeAndFlush(DefaultHttpResponse.badRequest.contentByteData).addListener(ChannelFutureListener.CLOSE);
                return;
            }
        }

        NatClient natClient;
        if (StringUtils.isNotBlank(bindClient)) {
            natClient = ChannelRegistry.getInstance().queryByClient(group, bindClient);
            if (natClient == null || !natClient.getCmdChannel().isActive()) {
                //ReturnUtil.writeRes(httpServletResponse, ReturnUtil.failed("device offline"));
                //TODO
                return;
            }
        } else {
            natClient = ChannelRegistry.getInstance().allocateOne(group);
        }
        if (natClient == null) {
            //ReturnUtil.writeRes(httpServletResponse, ReturnUtil.failed("no device online"));
            //TOOD
            return;
        }
        natClient.forward(requestBody, channelHandlerContext.channel());
    }
}
