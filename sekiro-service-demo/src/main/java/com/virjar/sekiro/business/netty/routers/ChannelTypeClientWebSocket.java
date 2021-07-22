package com.virjar.sekiro.business.netty.routers;

import com.virjar.sekiro.business.api.fastjson.JSONObject;
import com.virjar.sekiro.business.api.protocol.SekiroPacket;
import com.virjar.sekiro.business.api.protocol.SekiroPacketType;
import com.virjar.sekiro.business.api.util.Constants;
import com.virjar.sekiro.business.netty.buffer.ByteBuf;
import com.virjar.sekiro.business.netty.buffer.Unpooled;
import com.virjar.sekiro.business.netty.channel.Channel;
import com.virjar.sekiro.business.netty.channel.ChannelHandlerContext;
import com.virjar.sekiro.business.netty.handler.codec.http.*;
import com.virjar.sekiro.business.netty.handler.codec.http.websocketx.*;
import com.virjar.sekiro.business.netty.http.HttpNettyUtil;
import com.virjar.sekiro.business.netty.routers.client.NettyClient;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

@Slf4j
public class ChannelTypeClientWebSocket extends RouterHttpWebSocketBase {
    private String group;
    private String clientId;
    private StringBuilder frameBuffer = null;

    public ChannelTypeClientWebSocket(String urlPath, FullHttpRequest req, ChannelHandlerContext ctx) {
        super(urlPath, req, ctx);
    }

    @Override
    void onHandshakeSuccess(Channel channel) {
        NettyClient nettyClient = new NettyClient(channel, NettyClient.NatClientType.WS)
                .doRegister(group, clientId);
        channel.closeFuture().addListener(future1 -> nettyClient.onClientDisconnected());
    }

    @Override
    boolean init() {
        QueryStringDecoder queryStringDecoder = new QueryStringDecoder(req.getUri());
        Map<String, List<String>> parameters = queryStringDecoder.parameters();
        group = HttpNettyUtil.getParam(parameters, "group");
        clientId = HttpNettyUtil.getParam(parameters, "clientId");
        if (StringUtils.isBlank(group)
                || StringUtils.isBlank(clientId)) {
            String errorMessage = "{group} or {clientId} can not be empty!! demo url: ws://sekiro.virjar.com/business-demo/register?group=ws-group&clientId=testClient";
            ByteBuf byteBuf = Unpooled.wrappedBuffer(errorMessage.getBytes());
            DefaultFullHttpResponse defaultFullHttpResponse = new DefaultFullHttpResponse(
                    HttpVersion.HTTP_1_1, HttpResponseStatus.BAD_REQUEST, byteBuf);
            HttpNettyUtil.sendHttpResponse(ctx, req, defaultFullHttpResponse);
            return false;
        }
        return true;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, WebSocketFrame frame) throws Exception {
        if (frame instanceof CloseWebSocketFrame) {
            if (handshaker != null) {
                handshaker.close(ctx.channel(), (CloseWebSocketFrame) frame.retain());
            } else {
                ctx.close();
            }
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
        JSONObject jsonObject = JSONObject.parseObject(response);

        int serialNumber = jsonObject.getIntValue("__sekiro_seq__");
        if (serialNumber <= 0) {
            log.error("serial number not set for client!!");
            ctx.close();
            return;
        }
        SekiroPacket packet = SekiroPacketType.S_TYPE_INVOKE.createPacket();
        packet.setSerialNumber(serialNumber);
        packet.addHeader(Constants.PAYLOAD_CONTENT_TYPE.PAYLOAD_CONTENT_TYPE, "application/json;charset=utf8");
        packet.setData(jsonObject.toJSONString().getBytes(StandardCharsets.UTF_8));

        NettyClient.getFromNatChannel(ctx.channel()).response(packet);
    }
}
