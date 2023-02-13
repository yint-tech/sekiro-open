package cn.iinti.sekiro3.open.core.client;

import cn.iinti.sekiro3.business.api.fastjson.JSONObject;
import cn.iinti.sekiro3.business.api.protocol.SekiroPacket;
import cn.iinti.sekiro3.business.api.protocol.SekiroPacketType;
import cn.iinti.sekiro3.business.api.util.Constants;
import cn.iinti.sekiro3.business.netty.channel.Channel;
import cn.iinti.sekiro3.business.netty.channel.ChannelFutureListener;
import cn.iinti.sekiro3.business.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import cn.iinti.sekiro3.open.Bootstrap;
import cn.iinti.sekiro3.open.core.CommonRes;
import cn.iinti.sekiro3.open.utils.ConsistentHashUtil;
import cn.iinti.sekiro3.open.framework.trace.EventRecordManager;
import cn.iinti.sekiro3.open.framework.trace.EventScene;
import cn.iinti.sekiro3.open.framework.trace.Recorder;
import lombok.Getter;
import org.apache.commons.lang3.StringUtils;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

public abstract class NettyClient {
    @Getter
    private final String clientId;
    @Getter
    private final String group;
    @Getter
    private final Channel channel;
    @Getter
    private final long consistentKey;

    private final AtomicInteger sequenceGenerator = new AtomicInteger(1);

    final ConcurrentMap<Integer, InvokeRecord> invokeRecordMap = new ConcurrentHashMap<>();

    boolean offline = false;

    private final NettySekiroGroup sekiroGroup;


    @Getter
    private final Recorder recorder;

    public boolean isDown() {
        return offline || !channel.isActive();
    }

    public NettyClient(Channel channel, String group, String clientId) {
        this.channel = channel;
        this.clientId = clientId;
        this.group = group;
        this.sekiroGroup = NettySekiroGroup.createOrGet(group);
        recorder = EventRecordManager.acquireRecorder(clientId, Bootstrap.isLocalDebug, EventScene.SEKIRO_CLIENT);
        consistentKey = ConsistentHashUtil.murHash(clientId);

        sekiroGroup.registerNettyClient(this);
        recorder.recordEvent("client register with type: " + getClass().getSimpleName());

        channel.closeFuture().addListener((ChannelFutureListener) future -> onClientDisconnected());

    }


    public void doOffline() {
        if (StringUtils.isBlank(clientId)) {
            recorder.recordEvent("offline client before client register??");
            channel.close();
            return;
        }
        sekiroGroup.offlineClient(this);
    }


    public void onClientDisconnected() {
        if (StringUtils.isBlank(clientId)) {
            return;
        }
        sekiroGroup.unregisterNettyClient(NettyClient.this);
        sekiroGroup.safeDo(() -> {
            recorder.recordEvent("client disconnect");
            // interrupt all connection
            for (Integer seq : invokeRecordMap.keySet()) {
                InvokeRecord record = invokeRecordMap.remove(seq);
                if (record == null) {
                    continue;
                }
                record.response(CommonRes.failed("sekiro upstream lose connection"));
            }
        });
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
            recorder.recordEvent("receive invoke response after user timeout seq:" + sekiroPacket.getSerialNumber());
            return;
        }
        record.response(sekiroPacket);
    }

    private int newSeq() {
        int seq = sequenceGenerator.incrementAndGet();
        if (seq > Integer.MAX_VALUE * 0.75) {
            // reset seq
            int now = sequenceGenerator.get();
            if (now > Integer.MAX_VALUE * 0.5
                    && sequenceGenerator.compareAndSet(now, 1)
            ) {
                seq = sequenceGenerator.incrementAndGet();
            }
        }
        return seq;
    }

    public void forwardInvoke(InvokeRecord invokeRecord) {
        int seq = newSeq();
        channel.writeAndFlush(buildNettyClientRequest(invokeRecord.getRequest(), seq))
                .addListener(future -> {
                    if (!future.isSuccess()) {
                        invokeRecord.response(CommonRes.failed("write request to broken upstream: " + future.cause()));
                        return;
                    }
                    invokeRecordMap.put(seq, invokeRecord);
                    invokeRecord.afterForward(this, seq);
                });

    }

    public abstract Object buildNettyClientRequest(JSONObject jsonObject, int seq);


    public static NettyClient newWsNettyClient(Channel channel, String group, String clientId) {
        return new NettyClient(channel, group, clientId) {
            @Override
            public Object buildNettyClientRequest(JSONObject jsonObject, int seq) {
                jsonObject.put(Constants.REVERSED_WORDS.WEB_SOCKET_SEQ_NUMBER, seq);
                return new TextWebSocketFrame(jsonObject.toJSONString());
            }
        };
    }

    public static NettyClient newNativeClient(Channel channel, String group, String clientId) {
        return new NettyClient(channel, group, clientId) {
            @Override
            public Object buildNettyClientRequest(JSONObject jsonObject, int seq) {
                SekiroPacket packet = SekiroPacketType.S_TYPE_INVOKE.createPacket();
                packet.setSerialNumber(seq);
                packet.setData(jsonObject.toJSONString().getBytes(StandardCharsets.UTF_8));
                return packet;
            }
        };
    }
}
