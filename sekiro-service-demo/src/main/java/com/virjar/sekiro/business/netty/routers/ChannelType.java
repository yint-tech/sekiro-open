package com.virjar.sekiro.business.netty.routers;

import com.virjar.sekiro.business.api.core.SekiroFastJson;
import com.virjar.sekiro.business.api.fastjson.JSONObject;
import com.virjar.sekiro.business.api.protocol.SekiroPacket;
import com.virjar.sekiro.business.api.protocol.SekiroPacketType;
import com.virjar.sekiro.business.api.util.Constants;
import com.virjar.sekiro.business.netty.buffer.Unpooled;
import com.virjar.sekiro.business.netty.channel.Channel;
import com.virjar.sekiro.business.netty.channel.ChannelFuture;
import com.virjar.sekiro.business.netty.channel.ChannelFutureListener;
import com.virjar.sekiro.business.netty.handler.codec.http.*;
import com.virjar.sekiro.business.netty.http.ContentType;
import com.virjar.sekiro.business.netty.http.HttpNettyUtil;
import com.virjar.sekiro.business.netty.routers.client.InvokeRecord;
import com.virjar.sekiro.business.netty.util.AttributeKey;
import com.virjar.sekiro.business.netty.util.CommonRes;
import org.apache.commons.lang3.StringUtils;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import static com.virjar.sekiro.business.netty.handler.codec.http.HttpHeaders.Names.CONTENT_LENGTH;

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
        private void writeSekiroPacket(Channel channel, SekiroPacket sekiroPacket, InvokeRecord invokeRecord) {
            String contentType = sekiroPacket.getHeader(Constants.PAYLOAD_CONTENT_TYPE.PAYLOAD_CONTENT_TYPE);
            if (Constants.PAYLOAD_CONTENT_TYPE.CONTENT_TYPE_SEKIRO_FAST_JSON.equals(contentType)) {
                // 内部快速反序列化，可以避免服务器进行json解码，减少CPU计算
                SekiroFastJson.FastJson fastJson = SekiroFastJson.quickJsonDecode(sekiroPacket.getData(), invokeRecord.getClientId());
                String logMessage = "http_invoke_result_" + (fastJson.getStatus() == 0) + " : " + StringUtils.left(fastJson.getFinalJson(), 200);
                invokeRecord.getLogger().info(logMessage);
                HttpNettyUtil.writeJsonResponse(channel, fastJson.getFinalJson(), invokeRecord.isKeepAlive());
                return;
            }

            String isSegment = sekiroPacket.getHeader(Constants.PAYLOAD_CONTENT_TYPE.SEGMENT_STREAM_FLAG);
            boolean onceCall = !"true".equalsIgnoreCase(isSegment);
            if (!onceCall) {
                invokeRecord.isSegmentResponse = true;
            }

            if (onceCall && !invokeRecord.isSegmentResponse) {
                byte[] data = sekiroPacket.getData();
                ContentType from = ContentType.from(contentType);
                assert from != null;
                if (from.getMainType().equals("application") && from.getSubType().equals("json")) {
                    Charset charset = StandardCharsets.UTF_8;
                    if (StringUtils.isNotBlank(from.getCharset())) {
                        try {
                            charset = Charset.forName(from.getCharset());
                        } catch (Exception e) {
                            invokeRecord.getLogger().error("no charset: " + from.getCharset());
                        }
                    }
                    JSONObject jsonObject = JSONObject.parseObject(new String(data, charset));
                    jsonObject.put("clientId", invokeRecord.getClientId());
                    data = jsonObject.toJSONString().getBytes(charset);
                }

                DefaultFullHttpResponse httpResponse = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK,
                        Unpooled.wrappedBuffer(data));
                httpResponse.headers().set("Content-Type", contentType);
                httpResponse.headers().set(CONTENT_LENGTH, httpResponse.content().readableBytes());
                ChannelFuture channelFuture = channel.writeAndFlush(httpResponse);
                if (!invokeRecord.isKeepAlive()) {
                    channelFuture.addListener(ChannelFutureListener.CLOSE);
                }
                return;
            }

            // 分段传输
            if (!invokeRecord.hasResponseSegmentHttpHeader) {
                invokeRecord.hasResponseSegmentHttpHeader = true;

                DefaultHttpResponse resp = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
                //resp.setChunked(true);
                resp.headers().set("Content-Type", contentType);
                resp.headers().set(HttpHeaders.Names.TRANSFER_ENCODING, HttpHeaders.Values.CHUNKED);
                channel.write(resp);
            }


            if (onceCall || sekiroPacket.getData().length == 0) {
                ChannelFuture channelFuture = channel.writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT);
                if (!invokeRecord.isKeepAlive()) {
                    channelFuture.addListener(ChannelFutureListener.CLOSE);
                }
                return;
            }
            DefaultHttpContent trunkHttpContent = new DefaultHttpContent(Unpooled.wrappedBuffer(sekiroPacket.getData()));
            channel.writeAndFlush(trunkHttpContent);

            //延长超时时间，这样分段传输的时间可以拉长，避免大报文无法在规定时间内返回完成
            invokeRecord.mustResponseTimestamp = System.currentTimeMillis() + invokeRecord.timeout;
        }

        @Override
        void writeObjectInternal(Channel channel, Object bean, InvokeRecord invokeRecord) {
            if (bean instanceof SekiroPacket) {
                writeSekiroPacket(channel, (SekiroPacket) bean, invokeRecord);
            } else if (bean instanceof CommonRes) {
                HttpNettyUtil.writeRes(channel, (CommonRes) bean, invokeRecord.isKeepAlive());
            } else if (bean instanceof JSONObject) {
                HttpNettyUtil.writeJsonResponse(channel, ((JSONObject) bean).toJSONString(), invokeRecord.isKeepAlive());
            } else {
                invokeRecord.getLogger().error("write unknown message type:" + bean.getClass());
                channel.close();
            }
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
