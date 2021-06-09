package com.virjar.sekiro.business.netty.routers;

import com.virjar.sekiro.business.api.fastjson.JSONObject;
import com.virjar.sekiro.business.api.protocol.SekiroPacket;
import com.virjar.sekiro.business.api.protocol.SekiroPacketType;
import com.virjar.sekiro.business.api.util.Constants;
import com.virjar.sekiro.business.netty.channel.Channel;
import com.virjar.sekiro.business.netty.routers.client.InvokeRecord;
import com.virjar.sekiro.business.netty.util.AttributeKey;
import com.virjar.sekiro.business.netty.util.CommonRes;

import java.nio.charset.StandardCharsets;

/**
 * 按照sekiro业务划分，存在如下5种连接类型，他们占用同一个端口，用在不同业务下
 */
public enum ChannelType {
    //invoker指代业务调用方，实际调用（如抓取系统），为sekiro上游使用系统

    // 只狼私有协议调用，最先支持java，对齐sekiro最新最全特性，java环境下建议使用这个通道
    INVOKER_SEKIRO(1) {
        @Override
        void writeObjectInternal(Channel channel, Object bean, InvokeRecord invokeRecord) {

            SekiroPacket packet = SekiroPacketType.S_TYPE_INVOKE_RESPONSE.createPacket();
            int seq = invokeRecord.getRequest().getInteger(Constants.REVERSED_WORDS.WEB_SOCKET_SEQ_NUMBER);
            packet.setSerialNumber(seq);

            if (bean instanceof SekiroPacket) {
                SekiroPacket sekiroPacket = (SekiroPacket) bean;
                packet.migrateHeaders(sekiroPacket);
                packet.setData(sekiroPacket.getData());
                String status = sekiroPacket.getHeader(Constants.REVERSED_WORDS.RESPONSE_STATUS);
                //  invokeRecord.recordEvent("0".equals(status) ? HystrixRollingNumberEvent.SUCCESS : HystrixRollingNumberEvent.FAILED);
            } else if (bean instanceof CommonRes<?>) {
                CommonRes<?> commonRes = (CommonRes<?>) bean;
                commonRes.setClientId(invokeRecord.getClientId());
                packet.setData(JSONObject.toJSONString(commonRes).getBytes(StandardCharsets.UTF_8));
            } else if (bean instanceof String) {
                packet.setData(((String) bean).getBytes(StandardCharsets.UTF_8));
            } else {
                invokeRecord.getLogger().error("write unknown message type:" + bean.getClass());
                channel.close();
            }
            packet.addHeader(Constants.COMMON_HEADERS.HEADER_CLIENT_ID, invokeRecord.getClientId());
            channel.writeAndFlush(packet);

        }
    },
    // http调用通道，对于python等其他异构语言，可以通过标准的http协议调用只狼服务，请注意http需要保持keepAlive，减少tcp连接通道建立开销
    INVOKER_HTTP(1) {
        @Override
        void writeObjectInternal(Channel channel, Object bean, InvokeRecord invokeRecord) {
            throw new IllegalStateException("not support for demo version");
        }
    },
    // websocket调用通道，在需要实时要求场景下，使用websocket接受实时推流，可以实现远程投屏等实时交互功能，这个通道接入难度会大一些，一般要求使用者有二次开发的能力
    INVOKER_WS(1) {
        @Override
        void writeObjectInternal(Channel channel, Object bean, InvokeRecord invokeRecord) {
            //TODO
            throw new IllegalStateException("not support now");
        }
    },

    //CLIENT 指代设备接入方，如手机/pc/浏览器等多个终端资源，为sekiro下游系统。sekiro的职责是将上游调用转发到client终端资源上

    // 普通的二进制协议接入sekiro，和开源版对齐
    CLIENT_SEKIRO(2) {
        @Override
        void writeObjectInternal(Channel channel, Object bean, InvokeRecord invokeRecord) {
            // client端上暂时不考虑写入对象
            throw new IllegalStateException("can not write object message to client endpoint");
        }
    },
    // websocket方式接入sekiro，和开源版对齐，常用在weex、RN、pc浏览器等环境中
    CLIENT_WS(2) {
        @Override
        void writeObjectInternal(Channel channel, Object bean, InvokeRecord invokeRecord) {
            // client端上暂时不考虑写入对象
            throw new IllegalStateException("can not write object message to client endpoint");
        }
    };
    public static AttributeKey<ChannelType> CHANNEL_TYPE_KEY = AttributeKey.newInstance("CHANNEL_TYPE_KEY");

    private int type;

    ChannelType(int type) {
        this.type = type;
    }

    public void setup(Channel channel) {
        ChannelType channelType = channel.attr(CHANNEL_TYPE_KEY).get();
        if (channelType != null && channelType != this) {
            throw new IllegalStateException("set error channel type now:" + channel + " target:" + this);
        }
        channel.attr(CHANNEL_TYPE_KEY).set(this);
    }

    public static ChannelType getType(Channel channel) {
        return channel.attr(CHANNEL_TYPE_KEY).get();
    }

    public static void writeObject(Channel channel, Object bean, InvokeRecord invokeRecord) {
        getType(channel).writeObjectInternal(channel, bean, invokeRecord);
    }

    abstract void writeObjectInternal(Channel channel, Object bean, InvokeRecord invokeRecord);
}
