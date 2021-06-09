package com.virjar.sekiro.business.netty.routers;

import com.virjar.sekiro.business.api.core.safethread.ValueCallback;
import com.virjar.sekiro.business.api.fastjson.JSONException;
import com.virjar.sekiro.business.api.fastjson.JSONObject;
import com.virjar.sekiro.business.api.util.Constants;
import com.virjar.sekiro.business.netty.Bootstrap;
import com.virjar.sekiro.business.netty.buffer.Unpooled;
import com.virjar.sekiro.business.netty.channel.Channel;
import com.virjar.sekiro.business.netty.channel.ChannelFuture;
import com.virjar.sekiro.business.netty.channel.ChannelFutureListener;
import com.virjar.sekiro.business.netty.channel.ChannelHandlerContext;
import com.virjar.sekiro.business.netty.channel.SimpleChannelInboundHandler;
import com.virjar.sekiro.business.netty.handler.codec.http.DefaultFullHttpResponse;
import com.virjar.sekiro.business.netty.handler.codec.http.FullHttpRequest;
import com.virjar.sekiro.business.netty.handler.codec.http.HttpHeaders;
import com.virjar.sekiro.business.netty.handler.codec.http.HttpMethod;
import com.virjar.sekiro.business.netty.handler.codec.http.HttpResponseStatus;
import com.virjar.sekiro.business.netty.handler.codec.http.HttpVersion;
import com.virjar.sekiro.business.netty.http.ContentType;
import com.virjar.sekiro.business.netty.http.DefaultHtmlHttpResponse;
import com.virjar.sekiro.business.netty.http.HttpNettyUtil;
import com.virjar.sekiro.business.netty.http.Multimap;
import com.virjar.sekiro.business.netty.routers.client.NettyClient;
import com.virjar.sekiro.business.netty.routers.client.NettySekiroGroup;
import com.virjar.sekiro.business.netty.util.CommonRes;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;

import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.virjar.sekiro.business.netty.handler.codec.http.HttpHeaders.Names.CONTENT_LENGTH;

/**
 * http业务请求，channelType为:
 * {@link ChannelType#INVOKER_HTTP}
 */
public class ChannelTypeInvokerHttp extends SimpleChannelInboundHandler<FullHttpRequest> {
    private String query;
    private HttpMethod method;
    private ContentType contentType;
    private ChannelHandlerContext channelHandlerContext;
    private FullHttpRequest request;

    @Override
    protected void channelRead0(ChannelHandlerContext channelHandlerContext, FullHttpRequest request) throws Exception {
        URI uri;
        try {
            uri = new URI(request.getUri());
        } catch (URISyntaxException e) {
            Channel channel = channelHandlerContext.channel();
            channel.writeAndFlush(DefaultHtmlHttpResponse.badRequest()).addListener(ChannelFutureListener.CLOSE);
            return;
        }
        method = request.getMethod();
        String urlPath = uri.getPath();
        query = uri.getQuery();

        this.channelHandlerContext = channelHandlerContext;
        this.request = request;


        parseContentType(channelHandlerContext, request);
        //application/x-www-form-urlencoded
        //application/json

        if (!"application/x-www-form-urlencoded".equalsIgnoreCase(contentType.getMimeType())
                && !"application/json".equalsIgnoreCase(contentType.getMimeType())) {
            String errorMessage = "sekiro framework only support contentType:application/x-www-form-urlencoded | application/json, now is: " + contentType.getMimeType();
            DefaultHtmlHttpResponse contentTypeNotSupportMessage = new DefaultHtmlHttpResponse(errorMessage);

            Channel channel = channelHandlerContext.channel();
            channel.writeAndFlush(contentTypeNotSupportMessage).addListener(ChannelFutureListener.CLOSE);
            return;
        }

        JSONObject requestJson = buildRequestJson(request);
        if (StringUtils.equalsAnyIgnoreCase(urlPath, "/business-demo/invoke")) {
            handleSekiroInvoke(requestJson, channelHandlerContext, request);
            return;
        }
        if (StringUtils.equalsAnyIgnoreCase(urlPath, "/business-demo/groupList")) {
            handleGroupList(channelHandlerContext.channel());
            return;
        }
        if (StringUtils.equalsAnyIgnoreCase(urlPath, "/business-demo/clientQueue")) {
            handleClientQueue(requestJson);
            return;
        }

        if (StringUtils.equalsAnyIgnoreCase(urlPath, "/business-demo/mock-allocate")) {
            handleMockAllocate(requestJson);
            return;
        }

        if (StringUtils.equalsAnyIgnoreCase(urlPath, "/favicon.ico")) {
            InputStream stream = ChannelTypeInvokerHttp.class.getClassLoader().getResourceAsStream("favicon.ico");
            if (stream != null) {
                byte[] bytes = IOUtils.toByteArray(stream);
                DefaultFullHttpResponse httpResponse = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1,
                        HttpResponseStatus.OK,
                        Unpooled.wrappedBuffer(bytes)
                );
                httpResponse.headers().set("Content-Type", "image/x-icon");
                ChannelFuture channelFuture = channelHandlerContext.channel().writeAndFlush(httpResponse);
                if (!HttpHeaders.isKeepAlive(request)) {
                    channelFuture.addListener(ChannelFutureListener.CLOSE);
                }
            }

        }

        //404
        Channel channel = channelHandlerContext.channel();
        channel.writeAndFlush(DefaultHtmlHttpResponse.notFound()).addListener(ChannelFutureListener.CLOSE);
    }

    private void handleMockAllocate(JSONObject requestJson) {
        String ip = requestJson.getString("ip");
        if (StringUtils.isBlank(ip)) {
            ip = HttpHeaders.getHost(request);
        }
        if (StringUtils.isBlank(ip)) {
            ip = ((InetSocketAddress) channelHandlerContext.channel().localAddress()).getHostString();
        }
        byte[] bytes = (ip + Bootstrap.getListenPort()).getBytes(StandardCharsets.UTF_8);
        DefaultFullHttpResponse httpResponse = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK, Unpooled.wrappedBuffer(bytes));
        httpResponse.headers().set("Content-Type", "text/plain;charset=utf8;");
        httpResponse.headers().set(CONTENT_LENGTH, httpResponse.content().readableBytes());
        channelHandlerContext.channel().writeAndFlush(httpResponse).addListener(ChannelFutureListener.CLOSE);
    }

    private void handleGroupList(Channel channel) {
        HttpNettyUtil.writeRes(channel, CommonRes.success(NettySekiroGroup.groupList()), false);
    }

    private void handleClientQueue(JSONObject requestJson) {
        String group = requestJson.getString(Constants.REVERSED_WORDS.GROUP);
        if (StringUtils.isBlank(group)) {
            HttpNettyUtil.writeRes(channelHandlerContext.channel(), CommonRes.failed("the param {group} not presented"), false);
            return;
        }
        NettySekiroGroup.createOrGet(group).queue(value ->
                channelHandlerContext.channel().eventLoop().execute(
                        () -> HttpNettyUtil.writeRes(
                                channelHandlerContext.channel(),
                                CommonRes.success(
                                        value
                                                .stream()
                                                .filter(NettyClient::isAlive)
                                                .map(NettyClient::getClientId)
                                                .collect(Collectors.toList())
                                )
                                , false
                        )
                )
        );
    }

    private void handleSekiroInvoke(JSONObject requestJson, ChannelHandlerContext channelHandlerContext, FullHttpRequest request) {
        String group = requestJson.getString(Constants.REVERSED_WORDS.GROUP);
        String bindClient = requestJson.getString(Constants.REVERSED_WORDS.BIND_CLIENT);
        String sekiroAllocateKey = requestJson.getString(Constants.REVERSED_WORDS.CONSTANT_INVOKE);

        if (StringUtils.isBlank(group)) {
            HttpNettyUtil.writeRes(channelHandlerContext.channel(), CommonRes.failed("the param {group} not presented"), false);
            return;
        }

        if (StringUtils.isNotBlank(bindClient)) {
            NettySekiroGroup.createOrGet(group).fetchByClientId(bindClient,
                    new InvokeForwardValueCallback("device offline", requestJson));
        } else if (StringUtils.isNotBlank(sekiroAllocateKey)) {
            NettySekiroGroup.createOrGet(group).constantAllocate(sekiroAllocateKey,
                    new InvokeForwardValueCallback("no device online", requestJson));
        } else {
            NettySekiroGroup.createOrGet(group).allocate(
                    new InvokeForwardValueCallback("no device online", requestJson)
            );
        }

    }

    private class InvokeForwardValueCallback implements ValueCallback<NettyClient> {
        private final String nullValueMsg;
        private final JSONObject requestJson;

        InvokeForwardValueCallback(String nullValueMsg, JSONObject requestJson) {
            this.nullValueMsg = nullValueMsg;
            this.requestJson = requestJson;
        }

        @Override
        public void onReceiveValue(NettyClient value) {
            if (value == null) {
                HttpNettyUtil.writeRes(channelHandlerContext.channel(), CommonRes.failed(nullValueMsg), HttpHeaders.isKeepAlive(request));
                return;
            }
            value.getLogger().info("forward invoke :" + requestJson + " to client:" + value.getClientId());
            value.forwardInvoke(requestJson, channelHandlerContext.channel(), HttpHeaders.isKeepAlive(request));
        }
    }


    private JSONObject buildRequestJson(FullHttpRequest request) {
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
        return requestJson;
    }


    private void parseContentType(ChannelHandlerContext channelHandlerContext, FullHttpRequest request) {
        //create a request
        contentType = ContentType.from(request.headers().get("Content-Type"));
        if (contentType == null && !method.equals(HttpMethod.GET)) {
            //不识别的请求类型
            Channel channel = channelHandlerContext.channel();
            channel.writeAndFlush(DefaultHtmlHttpResponse.badRequest()).addListener(ChannelFutureListener.CLOSE);
            return;
        }
        if (contentType == null) {
            contentType = ContentType.from("application/x-www-form-urlencoded;charset=utf8");
        }
    }
}
