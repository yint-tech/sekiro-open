package com.virjar.sekiro.netty.client;


import com.virjar.sekiro.Constants;
import com.virjar.sekiro.api.SekiroClient;
import com.virjar.sekiro.netty.protocol.SekiroNatMessage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.handler.timeout.IdleStateHandler;

public class ClientIdleCheckHandler extends IdleStateHandler {

    private SekiroClient sekiroClient;
    private static final Logger log = LoggerFactory.getLogger(ClientIdleCheckHandler.class);

    public ClientIdleCheckHandler(SekiroClient sekiroClient) {
        super(Constants.READ_IDLE_TIME, Constants.WRITE_IDLE_TIME, 0);
        this.sekiroClient = sekiroClient;
    }

    @Override
    protected void channelIdle(ChannelHandlerContext ctx, IdleStateEvent evt) throws Exception {
        Channel cmdChannel = sekiroClient.getCmdChannel();
        if (ctx.channel() == cmdChannel) {
            //如果不是cmd channel的事件，直接丢弃
            handleIdle(ctx, evt);
        } else {
            ctx.close();
        }
        super.channelIdle(ctx, evt);
    }

    private void handleIdle(ChannelHandlerContext ctx, IdleStateEvent evt) {
        log.info("idle event:{}", evt.state());
        if (IdleStateEvent.FIRST_WRITER_IDLE_STATE_EVENT == evt) {
            log.info("write idle, write a heartbeat message to server");
            SekiroNatMessage sekiroNatMessage = new SekiroNatMessage();
            sekiroNatMessage.setType(SekiroNatMessage.TYPE_HEARTBEAT);
            ctx.channel().writeAndFlush(sekiroNatMessage);
        } else if (IdleStateEvent.FIRST_READER_IDLE_STATE_EVENT == evt) {
            log.info("read timeout,close channel");
            ctx.channel().close();
            log.info("the cmd channel lost,restart client");
            sekiroClient.connectNatServer();
        }
    }
}
