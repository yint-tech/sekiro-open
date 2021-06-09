package com.virjar.sekiro.business.netty.routers;

import com.virjar.sekiro.business.netty.channel.Channel;
import com.virjar.sekiro.business.netty.channel.ChannelFutureListener;
import com.virjar.sekiro.business.netty.channel.ChannelHandlerContext;
import com.virjar.sekiro.business.netty.channel.ChannelInboundHandlerAdapter;
import com.virjar.sekiro.business.netty.handler.codec.http.DefaultFullHttpResponse;
import com.virjar.sekiro.business.netty.handler.codec.http.FullHttpRequest;
import com.virjar.sekiro.business.netty.handler.codec.http.HttpResponseStatus;
import com.virjar.sekiro.business.netty.handler.codec.http.HttpVersion;
import com.virjar.sekiro.business.netty.http.DefaultHtmlHttpResponse;
import com.virjar.sekiro.business.netty.http.HttpNettyUtil;
import lombok.extern.slf4j.Slf4j;

import java.net.URI;
import java.net.URISyntaxException;

/**
 * 路由http相关协议，包括
 * <p>
 * {@link ChannelType#INVOKER_HTTP}
 * {@link ChannelType#INVOKER_WS}
 * {@link ChannelType#CLIENT_WS}
 * <p>
 * HttpRouter的职责是区分如上三种协议
 */
@Slf4j
public class RouterHttp extends ChannelInboundHandlerAdapter {

    private String urlPath;
    private ChannelHandlerContext ctx;
    private FullHttpRequest req;

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (!(msg instanceof FullHttpRequest)) {
            ctx.close();
            return;
        }
        this.ctx = ctx;
        this.req = (FullHttpRequest) msg;
        if (!req.getDecoderResult().isSuccess()) {
            DefaultFullHttpResponse defaultFullHttpResponse = new DefaultFullHttpResponse(
                    HttpVersion.HTTP_1_1, HttpResponseStatus.BAD_REQUEST);
            HttpNettyUtil.sendHttpResponse(ctx, req, defaultFullHttpResponse);
            return;
        }
        URI uri;
        try {
            uri = new URI(req.getUri());
        } catch (URISyntaxException e) {
            Channel channel = ctx.channel();
            channel.writeAndFlush(DefaultHtmlHttpResponse.badRequest()).addListener(ChannelFutureListener.CLOSE);
            return;
        }

        urlPath = uri.getPath();

        if ("websocket".equals(req.headers().get("Upgrade"))) {
            handleWebsocketInit();
        } else {
            handleHttpRequest();
        }
    }

    private void handleWebsocketInit() {
        if (urlPath.equals("/register")) {
            ctx.pipeline().remove(this);
            ChannelType.CLIENT_WS.setup(ctx.channel());
            new ChannelTypeClientWebSocket(urlPath, req, ctx);
            return;
        }
        if (urlPath.equals("/business/invoke")) {
            ctx.pipeline().remove(this);
            ChannelType.INVOKER_WS.setup(ctx.channel());
            new ChannelTypeInvokerWebsocket(urlPath, req, ctx);
            return;
        }
        String errorMessage = "error websocket url:" + urlPath;
        DefaultHtmlHttpResponse contentTypeNotSupportMessage = new DefaultHtmlHttpResponse(errorMessage);
        Channel channel = ctx.channel();
        channel.writeAndFlush(contentTypeNotSupportMessage).addListener(ChannelFutureListener.CLOSE);
    }

    private void handleHttpRequest() throws Exception {
        ctx.pipeline().addLast(new ChannelTypeInvokerHttp());
        ctx.pipeline().remove(this);
        ChannelType.INVOKER_HTTP.setup(ctx.channel());
        super.channelRead(ctx, req);
    }
}
