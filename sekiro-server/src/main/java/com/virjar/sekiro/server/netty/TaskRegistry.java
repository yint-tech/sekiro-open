package com.virjar.sekiro.server.netty;

import com.google.common.base.Charsets;
import com.google.common.collect.Maps;
import com.virjar.sekiro.netty.protocol.SekiroNatMessage;

import lombok.extern.slf4j.Slf4j;

import java.util.Map;

@Slf4j
public class TaskRegistry {
    private Map<String, NettyInvokeRecord> doingTask = Maps.newConcurrentMap();

    private TaskRegistry() {
    }

    private static TaskRegistry instance = new TaskRegistry();

    public static TaskRegistry getInstance() {
        return instance;
    }

    private String genTaskItemKey(String clientId, long seq) {
        return clientId + "---" + seq;
    }

    public synchronized void registerTask(NettyInvokeRecord nettyInvokeRecord) {
        doingTask.put(genTaskItemKey(nettyInvokeRecord.getClientId(), nettyInvokeRecord.getTaskId()), nettyInvokeRecord);
    }

    public void forwardClientResponse(String clientId, Long taskId, SekiroNatMessage sekiroNatMessage) {
        NettyInvokeRecord nettyInvokeRecord = doingTask.remove(genTaskItemKey(clientId, taskId));
        if (nettyInvokeRecord == null) {
            log.error("can not find invoke record for client: {}  taskId:{}", clientId, taskId);
            return;
        }
        nettyInvokeRecord.notifyDataArrival(sekiroNatMessage);
    }
}
