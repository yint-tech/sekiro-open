package com.virjar.sekiro.netty.client;

import com.virjar.sekiro.api.SekiroClient;
import com.virjar.sekiro.log.SekiroLogger;
import com.virjar.sekiro.netty.protocol.SekiroNatMessage;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;

public class ClientChannelHandler extends SimpleChannelInboundHandler<SekiroNatMessage> {

    private SekiroClient sekiroClient;

    public ClientChannelHandler(SekiroClient sekiroClient) {
        this.sekiroClient = sekiroClient;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext channelHandlerContext, SekiroNatMessage sekiroNatMessage) throws Exception {
        switch (sekiroNatMessage.getType()) {
            case SekiroNatMessage.TYPE_INVOKE:
                handleInvokeRequest(sekiroNatMessage, channelHandlerContext.channel());
                break;
        }
    }

    private void handleInvokeRequest(SekiroNatMessage sekiroNatMessage, Channel channel) {
        long serialNumber = sekiroNatMessage.getSerialNumber();
        if (serialNumber < 0) {
            throw new IllegalStateException("the serial number not set");

        }

        sekiroClient.getSekiroRequestHandlerManager().handleSekiroNatMessage(sekiroNatMessage, channel);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        super.channelInactive(ctx);
        Channel cmdChannel = sekiroClient.getCmdChannel();
        if (cmdChannel == ctx.channel()) {
            SekiroLogger.warn("channel inactive ,reconnect to nat server");
            sekiroClient.connectNatServer();
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        //System.out.println("exception caught: ", cause);
        cause.printStackTrace();
        SekiroLogger.error("exception caught", cause);
        super.exceptionCaught(ctx, cause);
    }
}
