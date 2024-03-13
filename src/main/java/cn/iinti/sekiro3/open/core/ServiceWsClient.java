package cn.iinti.sekiro3.open.core;

import cn.iinti.sekiro3.business.api.fastjson.JSONObject;
import cn.iinti.sekiro3.business.api.protocol.SekiroPacket;
import cn.iinti.sekiro3.business.api.protocol.SekiroPacketType;
import cn.iinti.sekiro3.business.api.util.Constants;
import cn.iinti.sekiro3.business.netty.channel.ChannelFutureListener;
import cn.iinti.sekiro3.business.netty.channel.ChannelHandlerContext;
import cn.iinti.sekiro3.business.netty.channel.SimpleChannelInboundHandler;
import cn.iinti.sekiro3.business.netty.handler.codec.http.websocketx.*;
import cn.iinti.sekiro3.open.framework.trace.Recorder;
import cn.iinti.sekiro3.open.core.client.NettyClient;

import java.nio.charset.StandardCharsets;

public class ServiceWsClient extends SimpleChannelInboundHandler<WebSocketFrame> {
    private final NettyClient nettyClient;
    private final WebSocketServerHandshaker handshaker;
    private StringBuilder frameBuffer;

    private final Recorder recorder;
    private ChannelHandlerContext context;

    public ServiceWsClient(NettyClient nettyClient, WebSocketServerHandshaker handshaker) {
        this.nettyClient = nettyClient;
        this.handshaker = handshaker;
        this.recorder = nettyClient.getRecorder();
    }

    private void error(String msg) {
        recorder.recordEvent(() -> msg);
        handshaker.close(context.channel(), new CloseWebSocketFrame(400, msg)).addListener(ChannelFutureListener.CLOSE);
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, WebSocketFrame frame) throws Exception {
        this.context = ctx;
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
                recorder.recordEvent("Continuation frame received without initial frame.");
            }
        } else if (frame instanceof PongWebSocketFrame) {
            recorder.recordEvent("Pong frame received");
            return;
        }
        else {
            error("error ws request: " + frame.getClass());
            return;
        }
        //可能有分片
        if (!frame.isFinalFragment()) {
            return;
        }
        String response = frameBuffer.toString();
        JSONObject jsonObject = JSONObject.parseObject(response);

        int serialNumber = jsonObject.getIntValue(Constants.REVERSED_WORDS.WEB_SOCKET_SEQ_NUMBER);
        if (serialNumber <= 0) {
            error("serial number not set for client!!");
            return;
        }
        jsonObject.remove(Constants.REVERSED_WORDS.WEB_SOCKET_SEQ_NUMBER);

        SekiroPacket packet = SekiroPacketType.S_TYPE_INVOKE_RESPONSE.createPacket();
        packet.setSerialNumber(serialNumber);
        packet.addHeader(Constants.PAYLOAD_CONTENT_TYPE.PAYLOAD_CONTENT_TYPE, "application/json;charset=utf8");
        packet.addHeader(Constants.REVERSED_WORDS.RESPONSE_STATUS, String.valueOf(jsonObject.getIntValue("status")));
        packet.setData(jsonObject.toJSONString().getBytes(StandardCharsets.UTF_8));

        nettyClient.response(packet);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        nettyClient.getRecorder().recordEvent("exceptionCaught ", cause);
    }
}
