package com.virjar.sekiro.business.netty.routers;

import com.virjar.sekiro.business.api.log.SekiroLogger;
import com.virjar.sekiro.business.api.protocol.SekiroPacket;
import com.virjar.sekiro.business.api.protocol.SekiroPacketType;
import com.virjar.sekiro.business.netty.channel.ChannelHandlerContext;
import com.virjar.sekiro.business.netty.channel.SimpleChannelInboundHandler;
import com.virjar.sekiro.business.netty.routers.client.NettyClient;

public class ChannelTypeClientSekiro extends SimpleChannelInboundHandler<SekiroPacket> {
    private final NettyClient nettyClient;
    private final SekiroLogger logger;

    public ChannelTypeClientSekiro(NettyClient nettyClient) {
        this.nettyClient = nettyClient;
        logger = nettyClient.getLogger();
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, SekiroPacket msg) throws Exception {
        SekiroPacketType packetType = SekiroPacketType.getByCode(msg.getType());
        if (packetType == null) {
            logger.error("unknown sekiro message type");
            return;
        }
        logger.info("receive message from sekiro client:" + packetType);
        switch (packetType) {
            case TYPE_HEARTBEAT:
                logger.info("receive heartbeat message:" + ctx.channel());
                break;
            case C_TYPE_REGISTER:
                // 这里不需要处理什么，因为已经被处理过了，在RouterSekiro
                break;
            case C_TYPE_INVOKE_RESPONSE:
                int seq = msg.getSerialNumber();
                if (seq < 0) {
                    logger.error("the serial number not set");
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
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        super.exceptionCaught(ctx, cause);
        logger.error("exceptionCaught ", cause);
    }
}
