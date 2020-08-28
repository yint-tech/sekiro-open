package com.virjar.sekiro.server.netty;

import com.google.common.base.Charsets;
import com.virjar.sekiro.Constants;
import com.virjar.sekiro.api.CommonRes;
import com.virjar.sekiro.netty.protocol.SekiroNatMessage;
import com.virjar.sekiro.server.netty.http.HeaderNameValue;
import com.virjar.sekiro.server.netty.nat.NettyInvokeRecord;
import com.virjar.sekiro.server.netty.nat.TaskRegistry;
import com.virjar.sekiro.server.util.ReturnUtil;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletResponse;

import external.com.alibaba.fastjson.JSONException;
import external.com.alibaba.fastjson.JSONObject;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;


@Slf4j
public class NatClient {

    public enum NatClientType {
        NORMAL,
        WS
    }

    @Getter
    private String group;

    @Getter
    private String clientId;
    @Getter
    private Channel cmdChannel;

    private NatClientType natClientType;

    private AtomicInteger timeOutCount = new AtomicInteger(0);

    private AtomicLong invokeSeqGenerator = new AtomicLong(1L);

    void migrateSeqGenerator(NatClient oldNatClient) {
        long oldId = oldNatClient.invokeSeqGenerator.get();
        if (oldId <= 0) {
            return;
        }
        long now = invokeSeqGenerator.addAndGet(oldId);
        if (now > Integer.MAX_VALUE * 2L) {
            invokeSeqGenerator.set(1L);
        }
    }

    public NatClient(String clientId, String group, Channel cmdChannel, NatClientType natClientType) {
        this.clientId = clientId;
        this.group = group;
        this.natClientType = natClientType;
        attachChannel(cmdChannel);
    }

    public void attachChannel(Channel channel) {
        Channel oldChannel = this.cmdChannel;
        if (oldChannel != null && oldChannel != channel) {
            //这里先让他给泄漏了。让客户端来close，服务器不负责close
            //oldChannel.close();
        }
        this.cmdChannel = channel;
        this.cmdChannel.attr(Constants.CLIENT_KEY).set(clientId);
        this.cmdChannel.attr(Constants.GROUP_KEY).set(group);
        timeOutCount.set(0);
    }

    private NettyInvokeRecord forwardInternal(String paramContent) {
        log.info("request body: {}   clientId:{} forward channel:{}", paramContent, clientId, cmdChannel);
        long invokeTaskId = invokeSeqGenerator.incrementAndGet();
        NettyInvokeRecord nettyInvokeRecord = new NettyInvokeRecord(clientId, group, invokeTaskId, paramContent);

        TaskRegistry.getInstance().registerTask(nettyInvokeRecord);

        if (natClientType == NatClientType.NORMAL) {
            SekiroNatMessage proxyMessage = new SekiroNatMessage();
            proxyMessage.setType(SekiroNatMessage.TYPE_INVOKE);
            proxyMessage.setSerialNumber(invokeTaskId);
            proxyMessage.setData(paramContent.getBytes(Charsets.UTF_8));
            cmdChannel.writeAndFlush(proxyMessage);
        } else {
            JSONObject jsonObject = JSONObject.parseObject(paramContent);
            jsonObject.put("__sekiro_seq__", invokeTaskId);
            TextWebSocketFrame textWebSocketFrame = new TextWebSocketFrame(jsonObject.toJSONString());
            cmdChannel.writeAndFlush(textWebSocketFrame);
        }

        return nettyInvokeRecord;
    }

    private void checkDisconnectForTimeout() {
        if (timeOutCount.get() > 4) {
            log.warn("连续4次调用超时，主动关闭连接...: {}", cmdChannel);
            sendSekiroSystemMessage("__sekiro_system_timeout", "timeout")
                    .addListener(ChannelFutureListener.CLOSE);

        }
    }

    @SuppressWarnings("all")
    private ChannelFuture sendSekiroSystemMessage(String action, String message) {
        JSONObject systemMessage = new JSONObject();
        systemMessage.put("action", "__sekiro_system_timeout");
        systemMessage.put("message", message);
        String paramContent = systemMessage.toJSONString();
        long invokeTaskId = invokeSeqGenerator.incrementAndGet();
        if (natClientType == NatClientType.NORMAL) {
            SekiroNatMessage proxyMessage = new SekiroNatMessage();
            proxyMessage.setType(SekiroNatMessage.TYPE_INVOKE);
            proxyMessage.setSerialNumber(invokeTaskId);
            proxyMessage.setData(paramContent.getBytes(Charsets.UTF_8));
            return cmdChannel.writeAndFlush(proxyMessage);
        } else {
            JSONObject jsonObject = JSONObject.parseObject(paramContent);
            jsonObject.put("__sekiro_seq__", invokeTaskId);
            TextWebSocketFrame textWebSocketFrame = new TextWebSocketFrame(jsonObject.toJSONString());
            return cmdChannel.writeAndFlush(textWebSocketFrame);
        }

    }

    public void forward(JSONObject requestJson, final Channel channel) {
        String invokeTimeoutString = requestJson.getString("invoke_timeout");
        int timeOut = NumberUtils.toInt(invokeTimeoutString);
        if (timeOut < 500) {
            //默认15s的超时时间
            timeOut = 15000;
        }

        final NettyInvokeRecord nettyInvokeRecord = forwardInternal(requestJson.toJSONString());
        channel.eventLoop().schedule(new Runnable() {
            @Override
            public void run() {
                TaskRegistry.getInstance().drop(nettyInvokeRecord);
            }
        }, timeOut, TimeUnit.MILLISECONDS);

        nettyInvokeRecord.setSekiroResponseEvent(new NettyInvokeRecord.SekiroResponseEvent() {
            @Override
            public void onSekiroResponse(SekiroNatMessage sekiroNatMessage) {
                if (sekiroNatMessage == null) {
                    CommonRes<Object> timeout = CommonRes.failed("timeout");
                    timeout.setClientId(clientId);
                    ReturnUtil.writeRes(channel, timeout);
                    timeOutCount.incrementAndGet();
                    checkDisconnectForTimeout();
                    return;
                }

                timeOutCount.set(0);
                byte[] data = sekiroNatMessage.getData();
                if (data == null) {
                    ReturnUtil.writeRes(channel, CommonRes.success(null));
                    return;
                }

                String responseContentType = sekiroNatMessage.getExtra();
                if (responseContentType == null) {
                    responseContentType = "text/plain;charset=utf8";
                }

                if (StringUtils.containsIgnoreCase(responseContentType, "application/json")) {
                    String responseJson = new String(sekiroNatMessage.getData(), StandardCharsets.UTF_8);
                    log.info("receive json response:{} from channel:{} ", responseJson, channel);
                    try {
                        JSONObject jsonObject = JSONObject.parseObject(responseJson);
                        ReturnUtil.writeRes(channel, ReturnUtil.from(jsonObject, clientId));
                        return;
                    } catch (JSONException e) {
                        log.warn("parse response failed", e);
                    }
                }

                DefaultFullHttpResponse httpResponse = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK, Unpooled.wrappedBuffer(data));
                httpResponse.headers().set(HeaderNameValue.CONTENT_TYPE, responseContentType);

                channel.writeAndFlush(httpResponse).addListener(ChannelFutureListener.CLOSE);
            }
        });
    }


    public void forward(String paramContent, Integer timeOut, HttpServletResponse httpServletResponse) {
        NettyInvokeRecord nettyInvokeRecord = forwardInternal(paramContent);
        nettyInvokeRecord.waitCallback(timeOut);

        TaskRegistry.getInstance().drop(nettyInvokeRecord);

        SekiroNatMessage sekiroNatMessage = nettyInvokeRecord.finalResult();
        if (sekiroNatMessage == null) {
            CommonRes<Object> timeout = CommonRes.failed("timeout");
            timeout.setClientId(clientId);
            ReturnUtil.writeRes(httpServletResponse, timeout);
            timeOutCount.incrementAndGet();
            checkDisconnectForTimeout();
            return;
        }
        timeOutCount.set(0);

        byte[] data = sekiroNatMessage.getData();
        if (data == null) {
            ReturnUtil.writeRes(httpServletResponse, CommonRes.success(null));
            return;
        }

        String responseContentType = sekiroNatMessage.getExtra();
        if (responseContentType == null) {
            responseContentType = "text/plain;charset=utf8";
        }

        if (StringUtils.containsIgnoreCase(responseContentType, "application/json")) {
            String responseJson = new String(sekiroNatMessage.getData(), StandardCharsets.UTF_8);
            log.info("receive json response:{}", responseJson);
            try {
                JSONObject jsonObject = JSONObject.parseObject(responseJson);
                ReturnUtil.writeRes(httpServletResponse, ReturnUtil.from(jsonObject, clientId));
                return;
            } catch (JSONException e) {
                log.warn("parse response failed", e);
            }
        }

        httpServletResponse.setContentType(responseContentType);
        ServletOutputStream outputStream = null;
        try {
            outputStream = httpServletResponse.getOutputStream();
            outputStream.write(sekiroNatMessage.getData());
            outputStream.close();
        } catch (IOException e) {
            log.warn("write response failed");
        } finally {
            IOUtils.closeQuietly(outputStream);
        }

    }
}
