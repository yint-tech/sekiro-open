package com.virjar.sekiro.business.netty.routers;

import com.virjar.sekiro.business.netty.channel.ChannelHandlerContext;
import com.virjar.sekiro.business.netty.channel.ChannelInboundHandlerAdapter;
import lombok.extern.slf4j.Slf4j;

/**
 * @author Tsaiilin
 * @version 1.0
 * @since 2021/5/8 15:24
 */
@Slf4j
public class ExceptionCaughtHandler extends ChannelInboundHandlerAdapter {
    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        log.warn(this.getClass().getSimpleName() + ": " + cause.getMessage());
        ctx.close();
    }

}
