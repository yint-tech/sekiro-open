package com.virjar.sekiro.server.netty.websocket;

import com.google.common.collect.Sets;
import com.virjar.sekiro.netty.protocol.SekiroNatMessage;
import com.virjar.sekiro.server.netty.nat.TaskRegistry;
import com.virjar.sekiro.server.util.Base64;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import external.com.alibaba.fastjson.JSONObject;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class WebSocketMessageAggregator {
    public static class WebSocketMessageFrame {
        public String clientId;
        public long seq;
        public int totalFrame;

        public ArrayList<String> contentList;
        private long firstFrameTime = 0;

        public static WebSocketMessageFrame create(String clientId, long seq, JSONObject jsonObject) {
            WebSocketMessageFrame webSocketMessageFrame = new WebSocketMessageFrame();
            webSocketMessageFrame.clientId = clientId;
            webSocketMessageFrame.seq = seq;
            webSocketMessageFrame.totalFrame = jsonObject.getIntValue("__sekiro_frame_total");
            webSocketMessageFrame.contentList = new ArrayList<>(webSocketMessageFrame.totalFrame);
            webSocketMessageFrame.firstFrameTime = System.currentTimeMillis();

            return webSocketMessageFrame;
        }


    }

    private static final Map<String, WebSocketMessageFrame> frameAggregatorData = new ConcurrentHashMap<>();


    static void onWebSocketFrame(String clientId, String group, long serialNumber,
                                 JSONObject content
    ) {
        if (!TaskRegistry.getInstance().hasTaskAttached(clientId, group, serialNumber)) {
            log.warn("the webSocket aggregator  task has been dropped!: {} ", content.toJSONString());
            return;
        }
        String frameGroupKey = group + "##" + clientId + "##" + serialNumber;
        WebSocketMessageFrame webSocketMessageFrame = frameAggregatorData.get(frameGroupKey);
        if (webSocketMessageFrame == null) {
            synchronized (WebSocketMessageAggregator.class) {
                webSocketMessageFrame = frameAggregatorData.get(frameGroupKey);
                if (webSocketMessageFrame == null) {
                    webSocketMessageFrame = WebSocketMessageFrame.create(clientId, serialNumber, content);
                    frameAggregatorData.put(frameGroupKey, webSocketMessageFrame);
                }
            }
        }

        synchronized (webSocketMessageFrame) {
            webSocketMessageFrame.contentList.add(content.getIntValue("__sekiro_index"), content.getString("__sekiro_content"));
            // not notify is revive all data
            if (webSocketMessageFrame.contentList.size() == webSocketMessageFrame.totalFrame) {
                StringBuilder stringBuilder = new StringBuilder();
                for (String str : webSocketMessageFrame.contentList) {
                    if (str == null) {
                        log.error("null content for websocket aggregator content: {}", JSONObject.toJSONString(webSocketMessageFrame.contentList));

                        return;
                    }
                    stringBuilder.append(str);
                }
                String aggregatorResponse = stringBuilder.toString();
                if (content.getBooleanValue("__sekiro_base64")) {
                    byte[] decode = Base64.decode(aggregatorResponse);
                    if (decode == null) {
                        //不应该发生，如果解码失败，那么暂时交给业务放自己解码
                        log.error("decode base64 failed");
                    } else {
                        aggregatorResponse = new String(decode);
                    }
                }

                log.info("aggregatorResponse: {}", aggregatorResponse);

                SekiroNatMessage sekiroNatMessage = new SekiroNatMessage();
                sekiroNatMessage.setType(SekiroNatMessage.TYPE_INVOKE);
                sekiroNatMessage.setSerialNumber(serialNumber);
                sekiroNatMessage.setExtra("application/json;charset=utf-8");
                sekiroNatMessage.setData(aggregatorResponse.getBytes(StandardCharsets.UTF_8));
                TaskRegistry.getInstance().forwardClientResponse(clientId, group, serialNumber, sekiroNatMessage);
                frameAggregatorData.remove(frameGroupKey);
            }
        }

    }

    public static void cleanBefore(long before) {

        Set<String> needRemove = Sets.newHashSet();

        for (Map.Entry<String, WebSocketMessageFrame> entry : frameAggregatorData.entrySet()) {
            WebSocketMessageFrame webSocketMessageFrame = entry.getValue();
            if (webSocketMessageFrame.firstFrameTime > before) {
                continue;
            }
            needRemove.add(entry.getKey());
        }

        for (String taskItemKey : needRemove) {
            WebSocketMessageFrame webSocketMessageFrame = frameAggregatorData.remove(taskItemKey);
            if (webSocketMessageFrame == null) {
                continue;
            }
            log.warn("clean timeout task by task clean scheduler:{}", taskItemKey);
            //nettyInvokeRecord.notifyDataArrival(null);
        }
    }
}
