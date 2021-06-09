package com.virjar.sekiro.business.netty.routers.client;

import com.google.common.collect.Maps;
import com.virjar.sekiro.business.api.core.Context;
import com.virjar.sekiro.business.api.fastjson.JSONObject;
import com.virjar.sekiro.business.api.protocol.SekiroPacket;
import com.virjar.sekiro.business.api.protocol.SekiroPacketType;
import com.virjar.sekiro.business.api.util.Constants;
import com.virjar.sekiro.business.netty.channel.Channel;
import com.virjar.sekiro.business.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import com.virjar.sekiro.business.netty.util.AttributeKey;
import com.virjar.sekiro.business.netty.util.ConstantHashUtil;

import lombok.Getter;
import org.apache.commons.lang3.StringUtils;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class NettyClient extends Context {


    public enum NatClientType {
        NORMAL,
        WS
    }

    private static final AttributeKey<NettyClient> attrNettyClientKey = AttributeKey.newInstance("attrNettyClientKey");

    private String clientId;

    @Getter
    private Channel channel;

    @Getter
    private long constantKey;

    private NatClientType natClientType;

    private final ReplaceableContext mParent;

    private final AtomicInteger sequenceGenerator = new AtomicInteger(1);


    final ConcurrentMap<Integer, InvokeRecord> invokeRecordMap = Maps.newConcurrentMap();

    boolean offline = false;

    public boolean isAlive() {
        return !offline && channel.isActive();
    }

    public NettyClient(Channel channel, NatClientType type) {
        super(new ReplaceableContext());
        this.channel = channel;
        natClientType = type;
        channel.attr(attrNettyClientKey).set(this);
        mParent = (ReplaceableContext) getParent();
    }

    public static NettyClient getFromNatChannel(Channel channel) {
        return channel.attr(attrNettyClientKey).get();
    }

    public NettyClient doRegister(String group, String clientId) {
        this.clientId = clientId;
        constantKey = ConstantHashUtil.murHash(clientId);
        mParent.setupParent(NettySekiroGroup.createOrGet(group));
        NettySekiroGroup.createOrGet(group).registerNettyClient(this);
        getLogger().info("client register");
        return this;
    }

    public void doOffline() {
        if (StringUtils.isBlank(clientId)) {
            getLogger().error("offline client before client register??");
            channel.close();
            return;
        }
        NettySekiroGroup.createOrGet(getSekiroGroup()).offlineClient(this);
    }

    @Override
    public String getClientId() {
        if (clientId == null) {
            return mParent.getClientId();
        }
        return clientId;
    }

    @Override
    public String getSekiroGroup() {
        return mParent.getSekiroGroup();
    }


    void overwrite(NettyClient newClient) {
        Channel historyChannel = this.channel;
        this.channel = newClient.channel;
        if (historyChannel.isActive()) {
            getLogger().error("duplicate client register old:" + historyChannel
                    + " new:" + channel);
            historyChannel.eventLoop().schedule((Runnable) historyChannel::close, 30, TimeUnit.SECONDS);
        }
        this.natClientType = newClient.natClientType;
    }

    public void onClientDisconnected() {
        NettySekiroGroup.createOrGet(getSekiroGroup())
                .safeDo(this::onClientDisconnected0);
    }

    private void onClientDisconnected0() {
        if (channel.isActive()) {
            return;
        }
        getLogger().info("client disconnect");
        NettySekiroGroup.createOrGet(getSekiroGroup())
                .unregisterNettyClient(NettyClient.this);
        // interrupt all connection
        for (Integer seq : invokeRecordMap.keySet()) {
            InvokeRecord record = invokeRecordMap.remove(seq);
            if (record == null) {
                continue;
            }
            record.onClientChannelInActive();
        }
    }

    public void response(SekiroPacket sekiroPacket) {
        String isSegment = sekiroPacket.getHeader(Constants.PAYLOAD_CONTENT_TYPE.SEGMENT_STREAM_FLAG);
        boolean onceCall = !"true".equalsIgnoreCase(isSegment);

        InvokeRecord record;
        if (onceCall) {
            record = invokeRecordMap.remove(sekiroPacket.getSerialNumber());
        } else {
            record = invokeRecordMap.get(sekiroPacket.getSerialNumber());
        }
        if (record == null) {
            getLogger().warn("receive invoke response after user timeout seq:" + sekiroPacket.getSerialNumber());
            return;
        }
        record.response(sekiroPacket);
    }

    public void forwardInvoke(JSONObject jsonObject, Channel useEndpointChannel, boolean keepAlive) {
        channel.eventLoop().execute(() -> forwardInvokeInNettyThread(jsonObject, useEndpointChannel, keepAlive));
    }

    private void forwardInvokeInNettyThread(JSONObject jsonObject, Channel useEndpointChannel, boolean keepAlive) {

        // 注意线程切换 forwardInvoke发生在我们定义的事件循环中，是单线程模型，
        // 而netty是线程是线程池，线程要多一些。请求转发存在序列化操作，所以我们切换下线程
        int seq = sequenceGenerator.getAndIncrement();
        if (seq > Integer.MAX_VALUE * 0.75) {
            // reset seq
            int now = sequenceGenerator.get();
            if (now > Integer.MAX_VALUE * 0.5
                    && sequenceGenerator.compareAndSet(now, 1)
            ) {
                seq = sequenceGenerator.getAndIncrement();
            }
        }


        InvokeRecord invokeRecord = new InvokeRecord(this, seq, jsonObject, useEndpointChannel, keepAlive);
        if (!channel.isActive()) {
            // 切换线程，那么有可能这个时候channel掉线了
            invokeRecord.onClientChannelInActive();
            return;
        }

        invokeRecordMap.put(seq, invokeRecord);
        Object requestPacket;
        if (natClientType == NatClientType.NORMAL) {
            SekiroPacket packet = SekiroPacketType.S_TYPE_INVOKE.createPacket();
            packet.setSerialNumber(seq);
            packet.setData(jsonObject.toJSONString().getBytes(StandardCharsets.UTF_8));
            requestPacket = packet;
        } else {
            jsonObject.put(Constants.REVERSED_WORDS.WEB_SOCKET_SEQ_NUMBER, seq);
            requestPacket = new TextWebSocketFrame(jsonObject.toJSONString());
        }
        channel.writeAndFlush(requestPacket).addListener(future -> {
            if (future.isSuccess()) {
                invokeRecord.requestSendSuccessful = true;
            }

        });

        invokeRecord.fireTimeoutTask();

    }


}
