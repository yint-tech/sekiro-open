package com.virjar.sekiro.business.netty.http;

import com.virjar.sekiro.business.api.fastjson.JSON;
import com.virjar.sekiro.business.netty.buffer.ByteBuf;
import com.virjar.sekiro.business.netty.buffer.Unpooled;
import com.virjar.sekiro.business.netty.channel.Channel;
import com.virjar.sekiro.business.netty.channel.ChannelFuture;
import com.virjar.sekiro.business.netty.channel.ChannelFutureListener;
import com.virjar.sekiro.business.netty.channel.ChannelHandlerContext;
import com.virjar.sekiro.business.netty.handler.codec.http.*;
import com.virjar.sekiro.business.netty.util.CommonRes;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static com.virjar.sekiro.business.netty.handler.codec.http.HttpHeaders.Names.CONTENT_LENGTH;

public class HttpNettyUtil {
    public static String getParam(Map<String, List<String>> parameters, String key) {
        List<String> strings = parameters.get(key);
        if (strings == null) {
            return null;
        }
        if (strings.isEmpty()) {
            return null;
        }
        return strings.get(0);
    }

    public static void sendHttpResponse(ChannelHandlerContext ctx, FullHttpRequest req, DefaultFullHttpResponse res) {
        res.headers().set(CONTENT_LENGTH, res.content().readableBytes());
        if (res.getStatus().code() != 200) {
            ByteBuf buf = Unpooled.copiedBuffer(res.getStatus().toString(), StandardCharsets.UTF_8);
            res.content().writeBytes(buf);
            buf.release();
        }

        ChannelFuture f = ctx.channel().writeAndFlush(res);
        if (!HttpHeaders.isKeepAlive(req) || res.getStatus().code() != 200) {
            f.addListener(ChannelFutureListener.CLOSE);
        }
    }

    public static void writeRes(Channel channel, CommonRes<?> commonRes, boolean keepAlive) {
        byte[] bytes = JSON.toJSONString(commonRes).getBytes(StandardCharsets.UTF_8);
        DefaultFullHttpResponse httpResponse = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK, Unpooled.wrappedBuffer(bytes));
        httpResponse.headers().set("Content-Type", "application/json;charset=utf8;");
        httpResponse.headers().set(CONTENT_LENGTH, httpResponse.content().readableBytes());
        ChannelFuture channelFuture = channel.writeAndFlush(httpResponse);
        if (!keepAlive) {
            channelFuture.addListener(ChannelFutureListener.CLOSE);
        }
    }


}
