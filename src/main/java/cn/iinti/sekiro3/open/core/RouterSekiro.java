package cn.iinti.sekiro3.open.core;

import cn.iinti.sekiro3.business.api.protocol.SekiroPacket;
import cn.iinti.sekiro3.business.api.protocol.SekiroPacketType;
import cn.iinti.sekiro3.business.api.util.Constants;
import cn.iinti.sekiro3.business.netty.channel.ChannelFutureListener;
import cn.iinti.sekiro3.business.netty.channel.ChannelHandlerContext;
import cn.iinti.sekiro3.business.netty.channel.ChannelInboundHandlerAdapter;
import cn.iinti.sekiro3.business.netty.channel.ChannelPipeline;
import cn.iinti.sekiro3.open.framework.trace.Recorder;
import cn.iinti.sekiro3.open.handlers.SekiroMsgEncoders;
import cn.iinti.sekiro3.open.core.client.NettyClient;
import org.apache.commons.lang3.StringUtils;


public class RouterSekiro extends ChannelInboundHandlerAdapter {

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        Recorder recorder = Session.get(ctx.channel()).getRecorder();

        if (!(msg instanceof SekiroPacket)) {
            recorder.recordEvent(RouterSekiro.class.getName() + ": can only handle ByteBuf message");
            ctx.close();
            return;
        }

        SekiroPacket sekiroPacket = (SekiroPacket) msg;
        SekiroPacketType packetType = SekiroPacketType.getByCode(sekiroPacket.getType());
        if (packetType == null) {
            recorder.recordEvent("unknown sekiro message type");
            return;
        }
        switch (packetType) {
            case TYPE_HEARTBEAT:
                // do nothing for heartbeat
                break;
            case C_TYPE_REGISTER:
                handleClientRegister(ctx, sekiroPacket);
                break;
            case I_TYPE_CONNECT:
                handleInvokerConnect(ctx);
                break;
            default:
                recorder.recordEvent("receive error packet from sekiroRouter:" + packetType);
                ctx.close();
        }
    }

    private void handleInvokerConnect(ChannelHandlerContext ctx) {
        ctx.writeAndFlush(SekiroPacketType.S_TYPE_INVOKE_CONNECTED.createPacket()).addListener((ChannelFutureListener) future -> {
            if (!future.isSuccess()) {
                return;
            }
            ChannelPipeline pipeline = ctx.pipeline();
            pipeline.addLast(new SekiroMsgEncoders.CommonRes2SekiroEncoder(), new ServiceSekiroInvoker());
            ctx.pipeline().remove(RouterSekiro.this);
        });
    }

    private void handleClientRegister(ChannelHandlerContext ctx, SekiroPacket sekiroPacket) {
        Recorder recorder = Session.get(ctx.channel()).getRecorder();
        String sekiroGroup = sekiroPacket.getHeader(Constants.COMMON_HEADERS.HEADER_SEKIRO_GROUP);
        String clientId = sekiroPacket.getHeader(Constants.COMMON_HEADERS.HEADER_CLIENT_ID);
        if (StringUtils.isBlank(sekiroGroup) || StringUtils.isBlank(clientId)) {
            recorder.recordEvent("group or clientId header is empty when register sekiro client");
            ctx.close();
            return;
        }

        ChannelPipeline pipeline = ctx.pipeline();
        pipeline.addLast(new ServiceSekiroClient(NettyClient.newNativeClient(ctx.channel(), sekiroGroup, clientId)));
        pipeline.remove(this);
    }
}
