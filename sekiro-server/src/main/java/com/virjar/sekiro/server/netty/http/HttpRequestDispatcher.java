package com.virjar.sekiro.server.netty.http;


import com.virjar.sekiro.api.Multimap;
import com.virjar.sekiro.server.netty.ChannelRegistry;
import com.virjar.sekiro.server.netty.NatClient;
import com.virjar.sekiro.server.netty.http.msg.DefaultHtmlHttpResponse;
import com.virjar.sekiro.server.util.ReturnUtil;

import org.apache.commons.lang3.StringUtils;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import external.com.alibaba.fastjson.JSONException;
import external.com.alibaba.fastjson.JSONObject;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpMethod;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class HttpRequestDispatcher extends SimpleChannelInboundHandler<FullHttpRequest> {


    @Override
    protected void channelRead0(ChannelHandlerContext channelHandlerContext, FullHttpRequest request) throws Exception {
        doHandle(channelHandlerContext, request);

    }

    private void doHandle(ChannelHandlerContext channelHandlerContext, FullHttpRequest request) {
        String uri = request.getUri();
        HttpMethod method = request.getMethod();


        String url = uri;
        String query = null;
        if (uri.contains("?")) {
            int index = uri.indexOf("?");
            url = uri.substring(0, index);
            //排除?
            query = uri.substring(index + 1);
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
            channel.writeAndFlush(DefaultHtmlHttpResponse.notFound()).addListener(ChannelFutureListener.CLOSE);
            return;
        }


        //create a request
        ContentType contentType = ContentType.from(request.headers().get(HeaderNameValue.CONTENT_TYPE));
        if (contentType == null && !method.equals(HttpMethod.GET)) {
            //不识别的请求类型
            Channel channel = channelHandlerContext.channel();
            channel.writeAndFlush(DefaultHtmlHttpResponse.badRequest()).addListener(ChannelFutureListener.CLOSE);
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
            DefaultHtmlHttpResponse contentTypeNotSupportMessage = new DefaultHtmlHttpResponse(errorMessage);

            Channel channel = channelHandlerContext.channel();
            channel.writeAndFlush(contentTypeNotSupportMessage).addListener(ChannelFutureListener.CLOSE);
            return;
            //  httpSekiroResponse.failed("sekiro framework only support contentType:application/x-www-form-urlencoded | application/json, now is: " + contentType.getMimeType());
            // return;
        }


        //now build request
        JSONObject requestJson = new JSONObject();
        if (StringUtils.isNotBlank(query)) {
            for (Map.Entry<String, List<String>> entry : Multimap.parseUrlEncoded(query).entrySet()) {
                if (entry.getValue() == null || entry.getValue().size() == 0) {
                    continue;
                }
                requestJson.put(entry.getKey(), entry.getValue().get(0));
            }
        }

        if (method.equals(HttpMethod.POST)) {

            String charset = contentType.getCharset();

            if (charset == null) {
                charset = StandardCharsets.UTF_8.name();
            }
            String postBody = request.content().toString(Charset.forName(charset));

            try {
                requestJson.putAll(JSONObject.parseObject(postBody));
            } catch (JSONException e) {
                for (Map.Entry<String, List<String>> entry : Multimap.parseUrlEncoded(postBody).entrySet()) {
                    if (entry.getValue() == null || entry.getValue().size() == 0) {
                        continue;
                    }
                    requestJson.put(entry.getKey(), entry.getValue().get(0));
                }
            }

        }

        String group = requestJson.getString("group");
        String bindClient = requestJson.getString("bindClient");

        if (StringUtils.isBlank(group)) {
            ReturnUtil.writeRes(channelHandlerContext.channel(), ReturnUtil.failed("the param {group} not presented"));
            return;
        }

        NatClient natClient;
        if (StringUtils.isNotBlank(bindClient)) {
            natClient = ChannelRegistry.getInstance().queryByClient(group, bindClient);
            if (natClient == null || !natClient.getCmdChannel().isActive()) {
                ReturnUtil.writeRes(channelHandlerContext.channel(), ReturnUtil.failed("device offline"));
                return;
            }
        } else {
            natClient = ChannelRegistry.getInstance().allocateOne(group);
        }
        if (natClient == null) {
            ReturnUtil.writeRes(channelHandlerContext.channel(), ReturnUtil.failed("no device online"));
            return;
        }
        natClient.forward(requestJson, channelHandlerContext.channel());
    }
}
