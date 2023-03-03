package cn.iinti.sekiro3.open.handlers;

import cn.iinti.sekiro3.business.api.fastjson.JSONObject;
import cn.iinti.sekiro3.business.api.protocol.SekiroPacket;
import cn.iinti.sekiro3.business.api.protocol.SekiroPacketType;
import cn.iinti.sekiro3.business.api.util.Constants;
import cn.iinti.sekiro3.business.netty.channel.ChannelHandlerContext;
import cn.iinti.sekiro3.business.netty.handler.codec.MessageToMessageEncoder;
import cn.iinti.sekiro3.open.core.CommonRes;
import cn.iinti.sekiro3.open.utils.NettyUtils;

import java.nio.charset.StandardCharsets;
import java.util.List;

public class SekiroMsgEncoders {
    public static class CommonRes2SekiroEncoder extends MessageToMessageEncoder<CommonRes<?>> {
        @Override
        protected void encode(ChannelHandlerContext ctx, CommonRes<?> commonRes, List<Object> out) {
            SekiroPacket packet = SekiroPacketType.S_TYPE_INVOKE_RESPONSE.createPacket();
            packet.setSerialNumber(commonRes.getSeq());
            packet.setData(JSONObject.toJSONString(commonRes).getBytes(StandardCharsets.UTF_8));
            packet.addHeader(Constants.COMMON_HEADERS.HTTP_CONTENT_TYPE, Constants.COMMON_HEADERS.HTTP_CONTENT_TYPE_JSON);
            packet.addHeader(Constants.COMMON_HEADERS.HEADER_CLIENT_ID, commonRes.getClientId());
            packet.addHeader(Constants.REVERSED_WORDS.RESPONSE_STATUS, String.valueOf(commonRes.getStatus()));
            out.add(packet);
        }
    }


    public static class CommonRes2HttpEncoder extends MessageToMessageEncoder<CommonRes<?>> {
        @Override
        protected void encode(ChannelHandlerContext ctx, CommonRes<?> msg, List<Object> out) {
            out.add(NettyUtils.buildJSONHttpResponse(msg));
        }
    }


}
