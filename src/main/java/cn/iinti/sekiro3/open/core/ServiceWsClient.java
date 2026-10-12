package cn.iinti.sekiro3.open.core;

import cn.iinti.sekiro3.business.api.fastjson.JSONObject;
import cn.iinti.sekiro3.business.api.fastjson.parser.Feature;
import cn.iinti.sekiro3.business.api.protocol.SekiroPacket;
import cn.iinti.sekiro3.business.api.protocol.SekiroPacketType;
import cn.iinti.sekiro3.business.api.util.Constants;
import cn.iinti.sekiro3.business.netty.buffer.ByteBuf;
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
    // 跨分片按字节聚合，收到最后一个分片后统一 UTF-8 解码。
    // 原实现逐分片调用 frame.text()（每片独立解码），多字节字符跨分片时
    // 各片独立解码出 U+FFFD 且无法恢复（实验：切分点落在汉字内部 13/16 乱码）。
    private ByteBuf frameBuffer;

    private final Recorder recorder;
    private ChannelHandlerContext context;

    public ServiceWsClient(NettyClient nettyClient, WebSocketServerHandshaker handshaker) {
        this.nettyClient = nettyClient;
        this.handshaker = handshaker;
        this.recorder = nettyClient.getRecorder();
    }

    private void error(String msg) {
        releaseBuffer();
        recorder.recordEvent(() -> msg);
        handshaker.close(context.channel(), new CloseWebSocketFrame(400, msg)).addListener(ChannelFutureListener.CLOSE);
    }

    private void releaseBuffer() {
        if (frameBuffer != null) {
            frameBuffer.release();
            frameBuffer = null;
        }
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
            // 消息起始分片：重建字节缓冲（上一条消息异常中断时先释放，防泄漏）
            releaseBuffer();
            frameBuffer = ctx.alloc().buffer();
            frameBuffer.writeBytes(frame.content());
        } else if (frame instanceof ContinuationWebSocketFrame) {
            if (frameBuffer != null) {
                // 续帧：仅追加原始字节，不做任何字符解码
                frameBuffer.writeBytes(frame.content());
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
        // 最后一个分片：对完整字节流做一次性 UTF-8 解码，
        // 多字节字符跨分片不再产生 U+FFFD
        String response = frameBuffer.toString(StandardCharsets.UTF_8);
        releaseBuffer();
        JSONObject jsonObject = JSONObject.parseObject(response, Feature.OrderedField);

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
        releaseBuffer();
        nettyClient.getRecorder().recordEvent("exceptionCaught ", cause);
    }

    @Override
    public void handlerRemoved(ChannelHandlerContext ctx) throws Exception {
        // 连接关闭时仍有未完成分片的消息：释放缓冲防内存泄漏
        releaseBuffer();
        super.handlerRemoved(ctx);
    }
}
