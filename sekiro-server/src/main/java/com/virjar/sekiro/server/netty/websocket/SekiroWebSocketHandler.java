package com.virjar.sekiro.server.netty.websocket;

import com.virjar.sekiro.Constants;
import com.virjar.sekiro.netty.protocol.SekiroNatMessage;
import com.virjar.sekiro.server.netty.ChannelRegistry;
import com.virjar.sekiro.server.netty.NatClient;
import com.virjar.sekiro.server.netty.nat.TaskRegistry;

import org.apache.commons.lang3.StringUtils;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import external.com.alibaba.fastjson.JSONObject;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.QueryStringDecoder;
import io.netty.handler.codec.http.websocketx.CloseWebSocketFrame;
import io.netty.handler.codec.http.websocketx.ContinuationWebSocketFrame;
import io.netty.handler.codec.http.websocketx.PingWebSocketFrame;
import io.netty.handler.codec.http.websocketx.PongWebSocketFrame;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketServerHandshaker;
import io.netty.handler.codec.http.websocketx.WebSocketServerHandshakerFactory;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class SekiroWebSocketHandler extends SimpleChannelInboundHandler<Object> {

    private WebSocketServerHandshaker handshaker;


    private StringBuilder frameBuffer = null;

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Object msg) throws Exception {
        //log.info("receive ws message: {}", msg);
        if (msg instanceof FullHttpRequest) {
            handleHttpRequest(ctx, (FullHttpRequest) msg);
        } else if (msg instanceof WebSocketFrame) {
            handlerWebSocketFrame(ctx, (WebSocketFrame) msg);
        } else {
            ctx.channel().close();
        }
    }

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
        ctx.flush();
    }


    private void handlerWebSocketFrame(ChannelHandlerContext ctx, WebSocketFrame frame) {

        if (frame instanceof CloseWebSocketFrame) {
            handshaker.close(ctx.channel(), (CloseWebSocketFrame) frame.retain());
            return;
        }
        if (frame instanceof PingWebSocketFrame) {
            ctx.channel().write(
                    new PongWebSocketFrame(frame.content().retain()));
            return;
        }

        if (frame instanceof TextWebSocketFrame) {
            frameBuffer = new StringBuilder();
            frameBuffer.append(((TextWebSocketFrame) frame).text());
        } else if (frame instanceof ContinuationWebSocketFrame) {
            if (frameBuffer != null) {
                frameBuffer.append(((ContinuationWebSocketFrame) frame).text());
            } else {
                log.warn("Continuation frame received without initial frame.");
            }
        } else {
            String errorMessage = "can not support this message";
            ByteBuf byteBuf = Unpooled.wrappedBuffer(errorMessage.getBytes());
            DefaultFullHttpResponse defaultFullHttpResponse = new DefaultFullHttpResponse(
                    HttpVersion.HTTP_1_1, HttpResponseStatus.BAD_REQUEST, byteBuf);
            ctx.channel().write(defaultFullHttpResponse);
            return;
        }

        //可能有分片
        if (!frame.isFinalFragment()) {
            return;
        }

        String response = frameBuffer.toString();
        JSONObject jsonObject;
        try {
            jsonObject = JSONObject.parseObject(response);
        } catch (Exception e) {
            log.error("the ws client response none json format data", e);
            // js的json好像和fastjson不一样
            log.warn("error response data: " + response);
            ctx.channel().close();
            return;
        }

        String clientId = ctx.channel().attr(Constants.CLIENT_KEY).get();
        if (StringUtils.isBlank(clientId)) {
            log.error("client id is lost");
            return;
        }
        String group = ctx.channel().attr(Constants.GROUP_KEY).get();
        if (StringUtils.isBlank(group)) {
            log.error("group  is lost");
            return;
        }

        long serialNumber = jsonObject.getLongValue("__sekiro_seq__");
        if (serialNumber <= 0) {
            log.error("serial number not set for client!!");
            return;
        }

        if (!jsonObject.getBooleanValue("__sekiro_is_frame")) {
            SekiroNatMessage sekiroNatMessage = new SekiroNatMessage();
            sekiroNatMessage.setType(SekiroNatMessage.TYPE_INVOKE);
            sekiroNatMessage.setSerialNumber(serialNumber);
            sekiroNatMessage.setExtra("application/json;charset=utf-8");
            sekiroNatMessage.setData(jsonObject.toJSONString().getBytes(StandardCharsets.UTF_8));
            TaskRegistry.getInstance().forwardClientResponse(clientId, group, serialNumber, sekiroNatMessage);
        } else {
            WebSocketMessageAggregator.onWebSocketFrame(clientId, group, serialNumber, jsonObject);
        }

    }

    private void handleHttpRequest(final ChannelHandlerContext ctx,
                                   FullHttpRequest req) {
        if (!req.getDecoderResult().isSuccess()
                || (!"websocket".equals(req.headers().get("Upgrade")))) {
            DefaultFullHttpResponse defaultFullHttpResponse = new DefaultFullHttpResponse(
                    HttpVersion.HTTP_1_1, HttpResponseStatus.BAD_REQUEST);
            sendHttpResponse(ctx, req, defaultFullHttpResponse);
            return;
        }


        // ws://sekiro.virjar.com:5603/websocket?group=ws-group&clientId=testClient
        QueryStringDecoder queryStringDecoder = new QueryStringDecoder(req.getUri());
        Map<String, List<String>> parameters = queryStringDecoder.parameters();
        final String group = getParam(parameters, "group");
        final String clientId = getParam(parameters, "clientId");
        if (StringUtils.isBlank(group)
                || StringUtils.isBlank(clientId)) {
            String errorMessage = "{group} or {clientId} can not be empty!! demo url: ws://sekiro.virjar.com:5603/websocket?group=ws-group&clientId=testClient";
            ByteBuf byteBuf = Unpooled.wrappedBuffer(errorMessage.getBytes());
            DefaultFullHttpResponse defaultFullHttpResponse = new DefaultFullHttpResponse(
                    HttpVersion.HTTP_1_1, HttpResponseStatus.BAD_REQUEST, byteBuf);
            sendHttpResponse(ctx, req, defaultFullHttpResponse);
            return;
        }

        //最大4M报文
        WebSocketServerHandshakerFactory wsFactory = new WebSocketServerHandshakerFactory(
                "ws://" + req.headers().get(HttpHeaders.Names.HOST) + "/websocket", null, false, 1 << 25);
        handshaker = wsFactory.newHandshaker(req);
        if (handshaker == null) {
            WebSocketServerHandshakerFactory
                    .sendUnsupportedVersionResponse(ctx.channel());
        } else {
            handshaker.handshake(ctx.channel(), req).addListener(new ChannelFutureListener() {

                @Override
                public void operationComplete(ChannelFuture future) {
                    if (!future.isSuccess()) {
                        log.error("ws handle shark failed", future.cause());
                        ctx.channel().close();
                        return;
                    }
                    ChannelRegistry.getInstance().registryClient(clientId + "@" + group, ctx.channel(), NatClient.NatClientType.WS);
                }
            });
        }
    }

    private static void sendHttpResponse(ChannelHandlerContext ctx, FullHttpRequest req, DefaultFullHttpResponse res) {

        if (res.getStatus().code() != 200) {
            ByteBuf buf = Unpooled.copiedBuffer(res.getStatus().toString(), StandardCharsets.UTF_8);
            res.content().writeBytes(buf);
            buf.release();
        }

        ChannelFuture f = ctx.channel().writeAndFlush(res);
        if (!HttpHeaders.isKeepAlive(req) || res.getStatus().code() != 200) {
            f.addListener(ChannelFutureListener.CLOSE);
        }
    }

    private String getParam(Map<String, List<String>> parameters, String key) {
        List<String> strings = parameters.get(key);
        if (strings == null) {
            return null;
        }
        if (strings.isEmpty()) {
            return null;
        }
        return strings.get(0);
    }
}
