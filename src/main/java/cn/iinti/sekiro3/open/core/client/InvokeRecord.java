package cn.iinti.sekiro3.open.core.client;

import cn.iinti.sekiro3.business.api.fastjson.JSONObject;
import cn.iinti.sekiro3.business.api.protocol.SekiroPacket;
import cn.iinti.sekiro3.business.api.protocol.SekiroPacketType;
import cn.iinti.sekiro3.business.api.util.Constants;
import cn.iinti.sekiro3.business.netty.channel.Channel;
import cn.iinti.sekiro3.business.netty.channel.ChannelFutureListener;
import cn.iinti.sekiro3.open.core.CommonRes;
import cn.iinti.sekiro3.open.core.Session;
import cn.iinti.sekiro3.open.framework.trace.Recorder;
import cn.iinti.sekiro3.open.handlers.SekiroMsgEncoders;
import cn.iinti.sekiro3.open.utils.NettyUtils;
import lombok.Getter;
import org.apache.commons.lang3.math.NumberUtils;

import java.util.concurrent.TimeUnit;

@Getter
public class InvokeRecord {
    private final JSONObject request;
    private final Channel requestChannel;
    private final boolean keepAlive;


    private final Recorder recorder;


    private int seq;
    private NettyClient nettyClient;


    public int timeout;
    public long mustResponseTimestamp;
    public boolean isSegmentResponse = false;
    public boolean hasResponseSegmentHttpHeader = false;


    public InvokeRecord(JSONObject request, Channel requestChannel, boolean keepAlive) {
        this.request = request;
        this.requestChannel = requestChannel;
        this.keepAlive = keepAlive;
        this.recorder = Session.get(requestChannel).getRecorder();
    }


    public void afterForward(NettyClient nettyClient, Integer seq) {
        this.seq = seq;
        this.nettyClient = nettyClient;

        recorder.recordEvent(() -> "forward invoke seq:" + seq + " request:" + request.toJSONString() + "to client: " + nettyClient.getClientId());
        String invokeTimeoutString = request.getString(Constants.REVERSED_WORDS.INVOKE_TIME_OUT);
        timeout = NumberUtils.toInt(invokeTimeoutString);
        if (timeout < 500) {
            // 默认15s的超时时间
            timeout = Constants.DEFAULT_INVOKE_TIMEOUT;
        }
        mustResponseTimestamp = System.currentTimeMillis() + timeout;

        scheduleTimeout();
    }


    public void response(CommonRes<?> commonRes) {
        if (!commonRes.isOk()) {
            recorder.recordEvent(() -> "response failed:" + commonRes.getMessage());
        }
        // if this request from invoker, we need sync invoker seq as response
        Integer originalSeq = request.getInteger(Constants.REVERSED_WORDS.WEB_SOCKET_SEQ_NUMBER);
        if (originalSeq != null) {
            commonRes.setSeq(originalSeq);
        } else {
            commonRes.setSeq(seq);
        }
        if (nettyClient != null) {
            commonRes.setClientId(nettyClient.getClientId());
        }
        doResponse(commonRes);

    }

    public void response(SekiroPacket sekiroPacket) {
        // if this request from invoker, we need sync invoker seq as response
        Integer originalSeq = request.getInteger(Constants.REVERSED_WORDS.WEB_SOCKET_SEQ_NUMBER);
        if (originalSeq != null) {
            sekiroPacket.setSerialNumber(originalSeq);
        }
        if (sekiroPacket.getType() == SekiroPacketType.C_TYPE_INVOKE_RESPONSE.getCode()) {
            // the sekiro v2 protocol,need compat with low level apis
            sekiroPacket.setType(SekiroPacketType.S_TYPE_INVOKE_RESPONSE.getCode());
        }
        boolean isHttpRequest = requestChannel.pipeline().get(SekiroMsgEncoders.CommonRes2HttpEncoder.class) != null;
        doResponse(isHttpRequest ? NettyUtils.buildHttpResponse(sekiroPacket, this) : sekiroPacket);

        // a response will trigger by SekiroPacket if this responded from remote endpoint
        // so we can record flow value


    }


    private void doResponse(Object res) {
        requestChannel.writeAndFlush(res).addListener((ChannelFutureListener) future -> {
            if (!future.isSuccess()) {
                recorder.recordEvent(() -> "write response to client failed", future.cause());
                return;
            }
            if (!keepAlive) {
                recorder.recordEvent(() -> "this is none keep alive request, close request channel");
                requestChannel.close();
            }
        });

    }

    public void scheduleTimeout() {
        requestChannel.eventLoop().schedule(() -> {
            if (isSegmentResponse) {
                // 分段传输模式，由于不知道返回时间，所以不进行timeout处理
                // 分段模式文件下载，ws长链接模拟等场景使用，由于我们的协议是整包完整投递，正常情况下一个包的数据需要完整到来之后才会转发（sekiro一搬面临不到1M的数据报文，正常是没有问题的）
                // 当需要投递大文件的时候，为了避免内存爆炸，需要提供分段传输模式
                if (System.currentTimeMillis() < mustResponseTimestamp) {
                    scheduleTimeout();
                    return;
                }
            }
            InvokeRecord invokeRecord = nettyClient.invokeRecordMap.remove(seq);
            if (invokeRecord == null) {
                // this invokeRecord has been response
                return;
            }
            recorder.recordEvent("invoke timeout for request: " + request.toJSONString());
            response(CommonRes.failed("timeout"));
        }, timeout, TimeUnit.MILLISECONDS);
    }
}
