package com.virjar.sekiro.business.netty.routers;

import com.virjar.sekiro.business.api.core.compress.CompressorManager;
import com.virjar.sekiro.business.api.core.compress.NettyCompressHandler;
import com.virjar.sekiro.business.api.core.compress.NettyDecompressHandler;
import com.virjar.sekiro.business.api.nat.ServerHeartBeatHandler;
import com.virjar.sekiro.business.api.protocol.PacketCommon;
import com.virjar.sekiro.business.api.protocol.SekiroPackerEncoder;
import com.virjar.sekiro.business.api.protocol.SekiroPacketDecoder;
import com.virjar.sekiro.business.netty.buffer.ByteBuf;
import com.virjar.sekiro.business.netty.channel.Channel;
import com.virjar.sekiro.business.netty.channel.ChannelHandlerContext;
import com.virjar.sekiro.business.netty.channel.ChannelInboundHandlerAdapter;
import com.virjar.sekiro.business.netty.handler.codec.http.HttpObjectAggregator;
import com.virjar.sekiro.business.netty.handler.codec.http.HttpServerCodec;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.TimeUnit;


/**
 * 实现5中协议的路由 {@link ChannelType}
 */
@Slf4j
public class Router extends ChannelInboundHandlerAdapter {

    private static final int ROUTER_MAGIC_HEADER_LENGTH = 8;

    private static final CompressorManager compressManager = new CompressorManager();

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (!(msg instanceof ByteBuf)) {
            log.error(Router.class.getName() + ": can only handle ByteBuf message");
            ctx.close();
            return;
        }

        ByteBuf byteBuf = (ByteBuf) msg;
        if (byteBuf.readableBytes() < ROUTER_MAGIC_HEADER_LENGTH) {
            checkTimeout(ctx);
            // not enough data
            return;
        }

        long magic = byteBuf.getLong(0);
        if (magic == PacketCommon.MAGIC) {
            routeToSekiro(ctx);
        } else {
            routeToHttp(ctx);
        }
        ctx.pipeline().remove(this);
        ctx.fireChannelRead(msg);
    }

    private void checkTimeout(ChannelHandlerContext ctx) {
        // 如果开始写数据，但是15秒停着没有数据到来，那么认为是攻击请求，此时关闭连接
        ctx.channel().eventLoop().schedule(() -> {
            Router router = ctx.pipeline().get(Router.class);
            if (router != null) {
                ctx.close();
            }

        }, 15, TimeUnit.SECONDS);
    }

    private void routeToSekiro(ChannelHandlerContext ctx) {
        Channel channel = ctx.channel();

        channel.pipeline()
                .addLast(new SekiroPackerEncoder())
                .addLast(new NettyCompressHandler(compressManager, true))

                // 这几行顺序非常重要
                .addLast(new SekiroPacketDecoder())
                .addLast(new NettyDecompressHandler(compressManager, true))
                .addLast(new ServerHeartBeatHandler())
                .addLast(new RouterSekiro())
                .addLast(new ExceptionCaughtHandler())
        ;
    }

    private void routeToHttp(ChannelHandlerContext ctx) {
        ctx.pipeline().addLast(new HttpServerCodec())
                //.addLast(new ChunkedWriteHandler())// 支持chunk
                //.addLast(new HttpContentCompressor()) // 自动压缩
                // 这里不需要压缩，正常情况sekiro不直接提供invoke http服务
                // 如果是http进来，那么需要经过ng转发。此时ng在内网网关
                // 走内网通信，所以内部不考虑带宽成本，反而考虑cpu计算资源
                // 所以不执行压缩
                .addLast(new HttpObjectAggregator(1 << 25))
                .addLast(new RouterHttp())
                .addLast(new ExceptionCaughtHandler())
        ;
    }
}
