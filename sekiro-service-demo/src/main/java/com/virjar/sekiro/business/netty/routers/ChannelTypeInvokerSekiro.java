package com.virjar.sekiro.business.netty.routers;

import com.virjar.sekiro.business.api.fastjson.JSONObject;
import com.virjar.sekiro.business.api.protocol.SekiroPacket;
import com.virjar.sekiro.business.api.protocol.SekiroPacketType;
import com.virjar.sekiro.business.api.util.Constants;
import com.virjar.sekiro.business.netty.channel.Channel;
import com.virjar.sekiro.business.netty.channel.ChannelHandlerContext;
import com.virjar.sekiro.business.netty.channel.SimpleChannelInboundHandler;
import com.virjar.sekiro.business.netty.util.CommonRes;

import java.nio.charset.StandardCharsets;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ChannelTypeInvokerSekiro extends SimpleChannelInboundHandler<SekiroPacket> {


    @Override
    protected void channelRead0(ChannelHandlerContext ctx, SekiroPacket msg) throws Exception {
        SekiroPacketType packetType = SekiroPacketType.getByCode(msg.getType());
        if (packetType == null) {
            log.error("unknown sekiro message type");
            return;
        }

        switch (packetType) {
            case TYPE_HEARTBEAT:
                log.info("receive heartbeat message:" + ctx.channel());
                break;
            case I_TYPE_CONNECT:
                ctx.writeAndFlush(SekiroPacketType.S_TYPE_INVOKE_CONNECTED.createPacket());
                break;
            case I_TYPE_INVOKE:
                handleInvokerInvoke(ctx, msg);
                break;
        }
    }

    private void handleInvokerInvoke(ChannelHandlerContext ctx, SekiroPacket msg) {
        byte[] data = msg.getData();
        if (data == null || data.length == 0) {
            log.error("empty invoker invoke");
            ctx.close();
            return;
        }

        int seq = msg.getSerialNumber();
        if (seq < 0) {
            ctx.close();
            return;
        }
        responseBeforeInvoke(CommonRes.failed("开源版本sekiro不支持SekiroInvoker"), ctx.channel(), seq);
    }

    private void responseBeforeInvoke(CommonRes<?> commonRes, Channel channel, int requestSeq) {
        SekiroPacket packet = SekiroPacketType.S_TYPE_INVOKE_RESPONSE.createPacket();
        packet.setSerialNumber(requestSeq);
        packet.setData(JSONObject.toJSONString(commonRes).getBytes(StandardCharsets.UTF_8));
        packet.addHeader(Constants.COMMON_HEADERS.HTTP_CONTENT_TYPE, Constants.COMMON_HEADERS.HTTP_CONTENT_TYPE_JSON);
        channel.writeAndFlush(packet);
    }
}
