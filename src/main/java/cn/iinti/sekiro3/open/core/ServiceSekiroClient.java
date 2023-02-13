package cn.iinti.sekiro3.open.core;

import cn.iinti.sekiro3.business.api.protocol.SekiroPacket;
import cn.iinti.sekiro3.business.api.protocol.SekiroPacketType;
import cn.iinti.sekiro3.business.netty.channel.ChannelHandlerContext;
import cn.iinti.sekiro3.business.netty.channel.SimpleChannelInboundHandler;
import cn.iinti.sekiro3.open.framework.trace.Recorder;
import cn.iinti.sekiro3.open.core.client.NettyClient;

public class ServiceSekiroClient extends SimpleChannelInboundHandler<SekiroPacket> {
    private final NettyClient nettyClient;

    public ServiceSekiroClient(NettyClient nettyClient) {
        this.nettyClient = nettyClient;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, SekiroPacket msg) {
        Recorder recorder = nettyClient.getRecorder();

        SekiroPacketType packetType = SekiroPacketType.getByCode(msg.getType());
        if (packetType == null) {
            recorder.recordEvent("unknown sekiro message type");
            return;
        }
        recorder.recordEvent("receive message from sekiro client:" + packetType);
        switch (packetType) {
            case TYPE_HEARTBEAT:
                recorder.recordEvent("receive heartbeat message:" + ctx.channel());
                break;
            case C_TYPE_INVOKE_RESPONSE:
                int seq = msg.getSerialNumber();
                if (seq < 0) {
                    recorder.recordEvent("the serial number not set");
                    ctx.close();
                    return;
                }
                nettyClient.response(msg);
                break;
            case C_TYPE_OFFLINE:
                nettyClient.doOffline();
                break;
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        nettyClient.getRecorder().recordEvent("exceptionCaught ", cause);
    }
}
