package com.virjar.sekiro.business.netty.routers;

import com.virjar.sekiro.business.api.log.SekiroGlobalLogger;
import com.virjar.sekiro.business.api.protocol.SekiroPacket;
import com.virjar.sekiro.business.api.protocol.SekiroPacketType;
import com.virjar.sekiro.business.api.util.CommomUtil;
import com.virjar.sekiro.business.api.util.Constants;
import com.virjar.sekiro.business.netty.channel.ChannelHandlerContext;
import com.virjar.sekiro.business.netty.channel.ChannelInboundHandlerAdapter;
import com.virjar.sekiro.business.netty.routers.client.NettyClient;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

/**
 * 路由sekiro私有协议类型，包括
 * <p>
 * {@link ChannelType#INVOKER_SEKIRO}
 * {@link ChannelType#CLIENT_SEKIRO}
 * <p>
 */
@Slf4j
public class RouterSekiro extends ChannelInboundHandlerAdapter {

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (!(msg instanceof SekiroPacket)) {
            log.error(Router.class.getName() + ": can only handle ByteBuf message");
            ctx.close();
            return;
        }

        SekiroPacket sekiroPacket = (SekiroPacket) msg;
        SekiroPacketType packetType = SekiroPacketType.getByCode(sekiroPacket.getType());
        if (packetType == null) {
            log.error("unknown sekiro message type");
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
                handlerInvokerConnect(ctx, sekiroPacket);
                break;
            default:
                log.error("receive error packet from sekiroRouter:" + packetType);
                ctx.close();
        }
    }

    private void handlerInvokerConnect(ChannelHandlerContext ctx, SekiroPacket sekiroPacket) {
        ChannelType.INVOKER_SEKIRO.setup(ctx.channel());
        ctx.pipeline().addLast(new ChannelTypeInvokerSekiro());
        ctx.pipeline().remove(this);
        ctx.fireChannelRead(sekiroPacket);
    }

    private void handleClientRegister(ChannelHandlerContext ctx, SekiroPacket sekiroPacket) {
        ChannelType.CLIENT_SEKIRO.setup(ctx.channel());
        NettyClient nettyClient = new NettyClient(ctx.channel(), NettyClient.NatClientType.NORMAL);
        ctx.channel().closeFuture()
                .addListener(future -> nettyClient.onClientDisconnected());

        CommomUtil.attachChannelSessionLogger(ctx.channel(), nettyClient.getLogger());
        // packet.addHeader(Constants.COMMON_HEADERS.HEADER_CLIENT_ID, clientId);
        // packet.addHeader(Constants.COMMON_HEADERS.HEADER_SEKIRO_GROUP, sekiroGroup);
        String sekiroGroup = sekiroPacket.getHeader(Constants.COMMON_HEADERS.HEADER_SEKIRO_GROUP);
        String clientId = sekiroPacket.getHeader(Constants.COMMON_HEADERS.HEADER_CLIENT_ID);
        if (StringUtils.isBlank(sekiroGroup) || StringUtils.isBlank(clientId)) {
            SekiroGlobalLogger.error("group or clientId header is empty when register sekiro client");
            ctx.close();
            return;
        }
        nettyClient.doRegister(sekiroGroup, clientId);
        ctx.pipeline().addLast(new ChannelTypeClientSekiro(nettyClient));
        ctx.pipeline().remove(this);
        ctx.fireChannelRead(sekiroPacket);
    }
}
