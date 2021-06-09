package com.virjar.sekiro.business.netty.routers.client;

import com.virjar.sekiro.business.api.core.Context;
import com.virjar.sekiro.business.api.fastjson.JSONObject;
import com.virjar.sekiro.business.api.log.SekiroLogger;
import com.virjar.sekiro.business.api.protocol.SekiroPacket;
import com.virjar.sekiro.business.api.util.Constants;
import com.virjar.sekiro.business.netty.channel.Channel;
import com.virjar.sekiro.business.netty.routers.ChannelType;
import com.virjar.sekiro.business.netty.util.CommonRes;

import lombok.Getter;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;

import java.util.concurrent.TimeUnit;

@Getter
public class InvokeRecord extends Context {
    private final NettyClient nettyClient;
    private final String action;
    public int timeout;
    public long mustResponseTimestamp;
    private final int seq;
    private final JSONObject request;
    private final Channel useEndpointChannel;
    private final boolean keepAlive;
    public boolean isSegmentResponse = false;
    public boolean hasResponseSegmentHttpHeader = false;

    boolean requestSendSuccessful = false;

    InvokeRecord(NettyClient nettyClient, int seq, JSONObject request, Channel useEndpointChannel, boolean keepAlive) {
        super(nettyClient);
        this.seq = seq;
        this.request = request;
        this.useEndpointChannel = useEndpointChannel;
        this.keepAlive = keepAlive;
        this.nettyClient = nettyClient;
        this.action = request.getString(Constants.REVERSED_WORDS.ACTION);
        String traceId = request.getString(Constants.REVERSED_WORDS.INVOKE_TRACE_ID);
        if (StringUtils.isNotBlank(traceId)) {
            setExtra(SekiroLogger.KEY_REQUEST_TRACE, traceId);
        }
        if (StringUtils.isNotBlank(action)) {
            setExtra(Constants.REVERSED_WORDS.ACTION, action);
        }
        getLogger().info("forward invoke seq:" + seq + " request:" + request.toJSONString());
        resolveTimeout();
    }


    /**
     * 当对应的NettyClient意外中断的处理,返回502(Bad Gateway)
     */
    void onClientChannelInActive() {
        String message;
        if (requestSendSuccessful) {
            // 请求发送给了SekiroClient，但是还没有收到响应的时候，SekiroClient意外掉线了
            message = "sekiro upstream lose connection";
        } else {
            // 分配的时候SekiroClient处于上线状态，但是当往SekiroClient写入请求的过程中，SekiroClient意外掉线了
            message = "write request to broken upstream";
        }
        getLogger().warn(message);
        ChannelType.writeObject(useEndpointChannel, CommonRes.failed(message).setClientId(getClientId()), this);
    }

    void response(SekiroPacket sekiroPacket) {
        ChannelType.writeObject(useEndpointChannel, sekiroPacket, this);
    }

    void timeout() {
        if (isSegmentResponse) {
            // 分段传输模式，由于不知道返回时间，所以不进行timeout处理
            if (System.currentTimeMillis() < mustResponseTimestamp) {
                fireTimeoutTask();
                return;
            }

        }
        InvokeRecord invokeRecord = nettyClient.invokeRecordMap.remove(seq);
        if (invokeRecord == null) {
            return;
        }
        getLogger().warn("invoke timeout for request: " + request.toJSONString());
        CommonRes<Object> response = CommonRes.failed("timeout");
        response.setClientId(getClientId());
        ChannelType.writeObject(useEndpointChannel, response, this);
    }


    private void resolveTimeout() {
        String invokeTimeoutString = request.getString(Constants.REVERSED_WORDS.INVOKE_TIME_OUT);
        timeout = NumberUtils.toInt(invokeTimeoutString);
        if (timeout < 500) {
            //默认5s的超时时间
            timeout = Constants.DEFAULT_INVOKE_TIMEOUT;
        }
        mustResponseTimestamp = System.currentTimeMillis() + timeout;

    }

    void fireTimeoutTask() {
        useEndpointChannel.eventLoop().schedule(this::timeout, timeout, TimeUnit.MILLISECONDS);
    }

    @Override
    public String getClientId() {
        return getParent().getClientId();
    }

    @Override
    public String getSekiroGroup() {
        return getParent().getSekiroGroup();
    }
}
