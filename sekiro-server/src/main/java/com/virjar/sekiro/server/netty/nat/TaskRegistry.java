package com.virjar.sekiro.server.netty.nat;

import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.virjar.sekiro.netty.protocol.SekiroNatMessage;

import java.util.Map;
import java.util.Set;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class TaskRegistry {
    private Map<String, NettyInvokeRecord> doingTask = Maps.newConcurrentMap();

    private TaskRegistry() {
    }

    private static TaskRegistry instance = new TaskRegistry();

    public static TaskRegistry getInstance() {
        return instance;
    }

    private String genTaskItemKey(String clientId, String group, long seq) {
        return clientId + "---" + clientId + "---" + seq;
    }

    public synchronized void registerTask(NettyInvokeRecord nettyInvokeRecord) {
        doingTask.put(genTaskItemKey(nettyInvokeRecord.getClientId(), nettyInvokeRecord.getGroup(), nettyInvokeRecord.getTaskId()), nettyInvokeRecord);
    }

    public boolean hasTaskAttached(String clientId, String group, Long taskId) {
        return doingTask.containsKey(genTaskItemKey(clientId, group, taskId));
    }

    public void forwardClientResponse(String clientId, String group, Long taskId, SekiroNatMessage sekiroNatMessage) {
        NettyInvokeRecord nettyInvokeRecord = doingTask.remove(genTaskItemKey(clientId, group, taskId));
        if (nettyInvokeRecord == null) {
            log.error("can not find invoke record for client: {}  taskId:{}", clientId, taskId);
            return;
        }
        nettyInvokeRecord.notifyDataArrival(sekiroNatMessage);
    }

    public void cleanBefore(long before) {

        Set<String> needRemove = Sets.newHashSet();

        for (Map.Entry<String, NettyInvokeRecord> entry : doingTask.entrySet()) {
            NettyInvokeRecord nettyInvokeRecord = entry.getValue();
            if (nettyInvokeRecord.getTaskAddTimestamp() > before) {
                continue;
            }
            needRemove.add(entry.getKey());
        }

        for (String taskItemKey : needRemove) {
            NettyInvokeRecord nettyInvokeRecord = doingTask.remove(taskItemKey);
            if (nettyInvokeRecord == null) {
                continue;
            }
            log.warn("clean timeout task by task clean scheduler:{}", taskItemKey);
            nettyInvokeRecord.notifyDataArrival(null);
        }
    }
}
