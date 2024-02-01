package cn.iinti.sekiro3.open.core;

import cn.iinti.sekiro3.business.api.fastjson.JSONException;
import cn.iinti.sekiro3.business.api.fastjson.JSONObject;
import cn.iinti.sekiro3.business.api.util.Constants;
import cn.iinti.sekiro3.business.netty.buffer.ByteBuf;
import cn.iinti.sekiro3.business.netty.buffer.Unpooled;
import cn.iinti.sekiro3.business.netty.channel.*;
import cn.iinti.sekiro3.business.netty.handler.codec.http.*;
import cn.iinti.sekiro3.business.netty.handler.codec.http.websocketx.WebSocketServerHandshaker;
import cn.iinti.sekiro3.business.netty.handler.codec.http.websocketx.WebSocketServerHandshakerFactory;
import cn.iinti.sekiro3.open.core.client.InvokeRecord;
import cn.iinti.sekiro3.open.core.client.NettyClient;
import cn.iinti.sekiro3.open.core.client.NettySekiroGroup;
import cn.iinti.sekiro3.open.framework.trace.Recorder;
import cn.iinti.sekiro3.open.handlers.SekiroMsgEncoders;
import cn.iinti.sekiro3.open.handlers.WsHeartbeatHandler;
import cn.iinti.sekiro3.open.utils.ContentType;
import cn.iinti.sekiro3.open.utils.DefaultHtmlHttpResponse;
import cn.iinti.sekiro3.open.utils.NettyUtils;
import org.apache.commons.lang3.StringUtils;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class ServiceHttp extends SimpleChannelInboundHandler<FullHttpRequest> {
    private String urlPath;
    private ChannelHandlerContext ctx;
    private QueryStringDecoder queryStringDecoder;
    private Recorder recorder;

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest fullHttpRequest) throws Exception {
        this.recorder = Session.get(ctx.channel()).getRecorder();
        this.ctx = ctx;
        if (!fullHttpRequest.getDecoderResult().isSuccess()) {
            DefaultFullHttpResponse defaultFullHttpResponse = new DefaultFullHttpResponse(
                    HttpVersion.HTTP_1_1, HttpResponseStatus.BAD_REQUEST);
            NettyUtils.sendHttpResponse(ctx, fullHttpRequest, defaultFullHttpResponse);
            return;
        }

        queryStringDecoder = new QueryStringDecoder(fullHttpRequest.getUri());
        urlPath = queryStringDecoder.path();
        recorder.recordEvent("access uri: " + urlPath);
        String host = fullHttpRequest.headers().get(HttpHeaders.Names.HOST);
        if ("websocket".equalsIgnoreCase(fullHttpRequest.headers().get("Upgrade"))) {
            recorder.recordEvent(() -> "this is websocket request");
            handleWebsocketInit(fullHttpRequest);
        } else {
            handleHttpRequest(fullHttpRequest);
        }
    }

    private void handleWebsocketInit(FullHttpRequest req) {
        WebSocketServerHandshakerFactory wsFactory = new WebSocketServerHandshakerFactory(
                "ws://" + req.headers().get(HttpHeaders.Names.HOST) + urlPath, null, false, 1 << 25);
        WebSocketServerHandshaker handshaker = wsFactory.newHandshaker(req);
        if (handshaker == null) {
            WebSocketServerHandshakerFactory.sendUnsupportedVersionResponse(ctx.channel());
            return;
        }
        handshaker.handshake(ctx.channel(), req).addListener((ChannelFutureListener) future -> {
            if (!future.isSuccess()) {
                recorder.recordEvent("ws handle shark failed", future.cause());
                ctx.channel().close();
                return;
            }

            if (urlPath.equals("/business/register")
                    || urlPath.equals("/register")
                    || urlPath.equals("/business-demo/register")
            ) {
                doWsClientRegister(req, handshaker);
                return;
            }

            if (urlPath.equals("/business/invoke")) {
                NettyUtils.httpResponseText(ctx.channel(), HttpResponseStatus.BAD_REQUEST, "ws invoker not impl now");
            } else {
                NettyUtils.httpResponseText(ctx.channel(), HttpResponseStatus.BAD_REQUEST, "error websocket url:" + urlPath);
            }
        });


    }

    private void doWsClientRegister(FullHttpRequest req, WebSocketServerHandshaker handshaker) {
        Map<String, List<String>> parameters = queryStringDecoder.parameters();
        String group = NettyUtils.getParam(parameters, "group");
        String clientId = NettyUtils.getParam(parameters, "clientId");
        if (StringUtils.isAnyBlank(group, clientId)) {
            String errorMessage = "{group} or {clientId} can not be empty!! demo url: ws://sekiro.iinti.cn:5612/register?group=ws-group&clientId=testClient";
            ByteBuf byteBuf = Unpooled.wrappedBuffer(errorMessage.getBytes());
            DefaultFullHttpResponse defaultFullHttpResponse = new DefaultFullHttpResponse(
                    HttpVersion.HTTP_1_1, HttpResponseStatus.BAD_REQUEST, byteBuf);
            NettyUtils.sendHttpResponse(ctx, req, defaultFullHttpResponse);
            return;
        }
        ChannelPipeline pipeline = ctx.pipeline();
        NettyClient nettyClient = NettyClient.newWsNettyClient(ctx.channel(), group, clientId);
        pipeline.addLast(
                new WsHeartbeatHandler(recorder),
                new ServiceWsClient(nettyClient, handshaker)
        );
        pipeline.remove(this);
    }

    private void handleHttpRequest(FullHttpRequest req) {
        ChannelPipeline pipeline = ctx.pipeline();

        SekiroMsgEncoders.CommonRes2HttpEncoder commonRes2HttpEncoder = pipeline.get(SekiroMsgEncoders.CommonRes2HttpEncoder.class);
        if (commonRes2HttpEncoder == null) {
            pipeline.addLast(new SekiroMsgEncoders.CommonRes2HttpEncoder());
        }

        Channel channel = ctx.channel();
        HttpMethod method = req.getMethod();
        ContentType contentType = ContentType.from(req.headers().get("Content-Type"));
        if (contentType == null && !method.equals(HttpMethod.GET)) {
            //不识别的请求类型
            channel.writeAndFlush(DefaultHtmlHttpResponse.badRequest()).addListener(ChannelFutureListener.CLOSE);
            return;
        }
        if (contentType == null) {
            contentType = ContentType.from("application/x-www-form-urlencoded;charset=utf8");
        }
        //application/x-www-form-urlencoded
        //application/json
        if (!StringUtils.equalsAnyIgnoreCase(contentType.getMimeType(), "application/x-www-form-urlencoded", "application/json")) {
            String errorMessage = "sekiro framework only support contentType:application/x-www-form-urlencoded | application/json, now is: " + contentType.getMimeType();
            recorder.recordEvent(errorMessage);
            NettyUtils.httpResponseText(channel, HttpResponseStatus.BAD_REQUEST, errorMessage);
            return;
        }

        JSONObject requestJson = buildRequestJson(req, method, contentType);
        recorder.recordEvent(() -> "the sekiro invoke request: " + requestJson);
        if (StringUtils.equalsAnyIgnoreCase(urlPath, "/business/invoke", "/business-demo/invoke")) {
            InvokeRecord invokeRecord = new InvokeRecord(requestJson, ctx.channel(), HttpHeaders.isKeepAlive(req));

            NettySekiroGroup.accessAndAllocate(requestJson, value -> {
                if (!value.isSuccess()) {
                    invokeRecord.response(CommonRes.failed(value.e.getMessage()));
                } else {
                    value.v.forwardInvoke(invokeRecord);
                }
            });
            return;
        }
        if (StringUtils.equalsAnyIgnoreCase(urlPath, "/business/groupList", "/business-demo/groupList")) {
            channel.writeAndFlush(CommonRes.success(NettySekiroGroup.groupList())).addListener(ChannelFutureListener.CLOSE);
            return;
        }
        if (StringUtils.equalsAnyIgnoreCase(urlPath, "/business/clientQueue", "/business-demo/clientQueue")) {
            String group = requestJson.getString(Constants.REVERSED_WORDS.GROUP);
            if (StringUtils.isBlank(group)) {
                channel.writeAndFlush(CommonRes.failed("the param {group} not presented")).addListener(ChannelFutureListener.CLOSE);
                return;
            }
            NettySekiroGroup.createOrGet(group)
                    .enabledQueue(value -> channel.writeAndFlush(
                            CommonRes.success(
                                    value.v.stream()
                                            .map(NettyClient::getClientId)
                                            .collect(Collectors.toList())
                            )).addListener(ChannelFutureListener.CLOSE));

            return;
        }

        channel.writeAndFlush(DefaultHtmlHttpResponse.notFound()).addListener(ChannelFutureListener.CLOSE);

    }

    private JSONObject buildRequestJson(FullHttpRequest req, HttpMethod method, ContentType contentType) {
        //now build request
        JSONObject requestJson = new JSONObject();
        for (Map.Entry<String, List<String>> entry : queryStringDecoder.parameters().entrySet()) {
            if (entry.getValue() == null || entry.getValue().size() == 0) {
                continue;
            }
            requestJson.put(entry.getKey(), entry.getValue().get(0));
        }

        if (method.equals(HttpMethod.POST)) {
            String charset = contentType.getCharset();
            if (charset == null) {
                charset = StandardCharsets.UTF_8.name();
            }
            String postBody = req.content().toString(Charset.forName(charset));
            try {
                requestJson.putAll(JSONObject.parseObject(postBody));
            } catch (JSONException e) {
                for (Map.Entry<String, List<String>> entry : new QueryStringDecoder(postBody, false).parameters().entrySet()) {
                    if (entry.getValue() == null || entry.getValue().size() == 0) {
                        continue;
                    }
                    requestJson.put(entry.getKey(), entry.getValue().get(0));
                }
            }
        }
        return requestJson;
    }


}
