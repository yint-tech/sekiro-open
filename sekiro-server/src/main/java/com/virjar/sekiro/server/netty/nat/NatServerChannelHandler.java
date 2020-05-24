package com.virjar.sekiro.server.netty.nat;


import com.virjar.sekiro.Constants;
import com.virjar.sekiro.netty.protocol.SekiroNatMessage;
import com.virjar.sekiro.server.netty.ChannelRegistry;
import com.virjar.sekiro.server.netty.NatClient;

import org.apache.commons.lang3.StringUtils;

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import lombok.extern.slf4j.Slf4j;

@ChannelHandler.Sharable
@Slf4j
public class NatServerChannelHandler extends SimpleChannelInboundHandler<SekiroNatMessage> {
    @Override
    protected void channelRead0(ChannelHandlerContext channelHandlerContext, SekiroNatMessage proxyMessage) throws Exception {
        log.info("recieved proxy message, type is:{} from channel:{}", proxyMessage.getTypeReadable(), channelHandlerContext.channel());
        switch (proxyMessage.getType()) {
            case SekiroNatMessage.TYPE_HEARTBEAT:
                handleHeartbeatMessage(channelHandlerContext, proxyMessage);
                break;
            case SekiroNatMessage.C_TYPE_REGISTER:
                handleRegisterMessage(channelHandlerContext, proxyMessage);
                break;
            case SekiroNatMessage.TYPE_INVOKE:
                handleInvokeResponseMessage(channelHandlerContext, proxyMessage);
                break;
            default:
                log.info("协议紊乱。。。关闭连接");
                channelHandlerContext.channel().close();
        }
    }

    private void handleInvokeResponseMessage(ChannelHandlerContext ctx, SekiroNatMessage proxyMessage) {
        String clientId = ctx.channel().attr(Constants.CLIENT_KEY).get();
        if (StringUtils.isBlank(clientId)) {
            log.error("client id is lost");
            return;
        }
        long serialNumber = proxyMessage.getSerialNumber();
        if (serialNumber <= 0) {
            log.error("serial number not set for client!!");
            return;
        }
        String group = ctx.channel().attr(Constants.GROUP_KEY).get();
        TaskRegistry.getInstance().forwardClientResponse(clientId, group, serialNumber, proxyMessage);
    }


    private void handleHeartbeatMessage(ChannelHandlerContext ctx, SekiroNatMessage proxyMessage) {
        SekiroNatMessage heartbeatMessage = new SekiroNatMessage();
        heartbeatMessage.setSerialNumber(proxyMessage.getSerialNumber());
        heartbeatMessage.setType(SekiroNatMessage.TYPE_HEARTBEAT);
        log.info("response heartbeat message {}", ctx.channel());
        ctx.channel().writeAndFlush(heartbeatMessage);

//        String clientId = ctx.channel().attr(Constants.CLIENT_KEY).get();
//        if (StringUtils.isBlank(clientId)) {
//            ctx.channel().close();
//        } else {
//            ChannelRegistry.getInstance().registryClient(clientId, ctx.channel());
//        }
    }

    private void handleRegisterMessage(ChannelHandlerContext ctx, SekiroNatMessage proxyMessage) {
        String clientIdAndGroup = proxyMessage.getExtra();
        if (StringUtils.isBlank(clientIdAndGroup)) {
            log.error("clientId can not empty");
            return;
        }
        ChannelRegistry.getInstance().registryClient(clientIdAndGroup, ctx.channel(), NatClient.NatClientType.NORMAL);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        log.error("error with channel:" + ctx.channel(), cause);
        super.exceptionCaught(ctx, cause);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        //TODO release all connect attached with this channel
        super.channelInactive(ctx);
    }
}
