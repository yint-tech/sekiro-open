package cn.iinti.sekiro3.open.core;

import cn.iinti.sekiro3.business.api.fastjson.JSONException;
import cn.iinti.sekiro3.business.api.fastjson.JSONObject;
import cn.iinti.sekiro3.business.api.protocol.SekiroPacket;
import cn.iinti.sekiro3.business.api.protocol.SekiroPacketType;
import cn.iinti.sekiro3.business.api.util.Constants;
import cn.iinti.sekiro3.business.netty.channel.ChannelHandlerContext;
import cn.iinti.sekiro3.business.netty.channel.SimpleChannelInboundHandler;
import cn.iinti.sekiro3.open.framework.trace.Recorder;
import cn.iinti.sekiro3.open.core.client.InvokeRecord;
import cn.iinti.sekiro3.open.core.client.NettySekiroGroup;

import java.nio.charset.StandardCharsets;


public class ServiceSekiroInvoker extends SimpleChannelInboundHandler<SekiroPacket> {

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, SekiroPacket msg) {
        Recorder recorder = Session.get(ctx.channel()).getRecorder();
        SekiroPacketType packetType = SekiroPacketType.getByCode(msg.getType());
        if (packetType == SekiroPacketType.TYPE_HEARTBEAT) {
            recorder.recordEvent("invoker receive heartbeat msg");
            return;
        }
        if (packetType != SekiroPacketType.I_TYPE_INVOKE) {
            recorder.recordEvent(() -> "error sekiro message type:" + packetType);
            return;
        }
        byte[] data = msg.getData();
        if (data == null || data.length == 0) {
            recorder.recordEvent(() -> "empty invoker request");
            ctx.close();
            return;
        }

        int seq = msg.getSerialNumber();
        if (seq < 0) {
            recorder.recordEvent(() -> "empty invoker seq number");
            ctx.close();
            return;
        }

        JSONObject requestJson;
        try {
            requestJson = JSONObject.parseObject(new String(data, StandardCharsets.UTF_8));
        } catch (JSONException e) {
            recorder.recordEvent("error parse request json", e);
            ctx.write(CommonRes.failed("error to parse request").setSeq(seq));
            return;
        }

        requestJson.put(Constants.REVERSED_WORDS.DO_NOT_COMPRESS_FOR_SEKIRO_SEKIRO, "true");
        InvokeRecord invokeRecord = new InvokeRecord(requestJson, ctx.channel(), true);
        invokeRecord.invokerSeq = seq;

        NettySekiroGroup.accessAndAllocate(requestJson, value -> {
            if (!value.isSuccess()) {
                invokeRecord.response(CommonRes.failed(value.e.getMessage()));
                return;
            }
            value.v.forwardInvoke(invokeRecord);
        });


    }
}
