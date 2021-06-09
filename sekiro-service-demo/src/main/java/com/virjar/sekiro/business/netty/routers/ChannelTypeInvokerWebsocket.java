package com.virjar.sekiro.business.netty.routers;

import com.virjar.sekiro.business.netty.channel.Channel;
import com.virjar.sekiro.business.netty.channel.ChannelHandlerContext;
import com.virjar.sekiro.business.netty.handler.codec.http.FullHttpRequest;
import com.virjar.sekiro.business.netty.handler.codec.http.websocketx.WebSocketFrame;

/**
 * websocket推流主要在这这里实现
 */
public class ChannelTypeInvokerWebsocket extends RouterHttpWebSocketBase {
    public ChannelTypeInvokerWebsocket(String urlPath, FullHttpRequest req, ChannelHandlerContext ctx) {
        super(urlPath, req, ctx);

    }

    @Override
    void onHandshakeSuccess(Channel channel) {

    }

    @Override
    boolean init() {
        return false;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, WebSocketFrame msg) throws Exception {

    }
}
