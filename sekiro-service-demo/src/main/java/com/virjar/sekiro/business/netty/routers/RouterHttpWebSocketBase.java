package com.virjar.sekiro.business.netty.routers;

import com.virjar.sekiro.business.netty.channel.Channel;
import com.virjar.sekiro.business.netty.channel.ChannelFutureListener;
import com.virjar.sekiro.business.netty.channel.ChannelHandlerContext;
import com.virjar.sekiro.business.netty.channel.SimpleChannelInboundHandler;
import com.virjar.sekiro.business.netty.handler.codec.http.FullHttpRequest;
import com.virjar.sekiro.business.netty.handler.codec.http.HttpHeaders;
import com.virjar.sekiro.business.netty.handler.codec.http.websocketx.WebSocketFrame;
import com.virjar.sekiro.business.netty.handler.codec.http.websocketx.WebSocketServerHandshaker;
import com.virjar.sekiro.business.netty.handler.codec.http.websocketx.WebSocketServerHandshakerFactory;
import lombok.extern.slf4j.Slf4j;

/**
 * ws协议的公共逻辑，包括如下
 * <p>
 * {@link ChannelType#INVOKER_WS}
 * {@link ChannelType#CLIENT_WS}
 * <p>
 * HttpRouter的职责是区分如上三种协议
 */
@Slf4j
public abstract class RouterHttpWebSocketBase extends SimpleChannelInboundHandler<WebSocketFrame> {
    protected WebSocketServerHandshaker handshaker;

    protected FullHttpRequest req;
    protected ChannelHandlerContext ctx;

    public RouterHttpWebSocketBase(String urlPath, FullHttpRequest req, ChannelHandlerContext ctx) {
        this.req = req;
        this.ctx = ctx;
        if (!init()) {
            return;
        }
        WebSocketServerHandshakerFactory wsFactory = new WebSocketServerHandshakerFactory(
                "ws://" + req.headers().get(HttpHeaders.Names.HOST) + urlPath, null, false, 1 << 25);
        handshaker = wsFactory.newHandshaker(req);
        if (handshaker == null) {
            WebSocketServerHandshakerFactory
                    .sendUnsupportedVersionResponse(ctx.channel());
            return;
        }
        handshaker.handshake(ctx.channel(), req).addListener((ChannelFutureListener) future -> {
            if (!future.isSuccess()) {
                log.error("ws handle shark failed", future.cause());
                ctx.channel().close();
                return;
            }
            ctx.pipeline().addLast(this);
            onHandshakeSuccess(ctx.channel());
        });
    }

    abstract void onHandshakeSuccess(Channel channel);

    abstract boolean init();
}
