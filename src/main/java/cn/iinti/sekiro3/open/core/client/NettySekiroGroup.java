package cn.iinti.sekiro3.open.core.client;

import cn.iinti.sekiro3.business.api.fastjson.JSONObject;
import cn.iinti.sekiro3.business.api.util.Constants;
import cn.iinti.sekiro3.business.netty.channel.Channel;
import cn.iinti.sekiro3.open.utils.ConsistentHashUtil;
import cn.iinti.sekiro3.open.framework.safethread.Looper;
import cn.iinti.sekiro3.open.framework.safethread.ValueCallback;
import cn.iinti.sekiro3.open.framework.trace.EventRecordManager;
import cn.iinti.sekiro3.open.framework.trace.EventScene;
import cn.iinti.sekiro3.open.framework.trace.Recorder;
import lombok.Getter;
import org.apache.commons.lang3.StringUtils;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;

public class NettySekiroGroup {

    private static final ConcurrentMap<String, NettySekiroGroup> sGroup = new ConcurrentHashMap<>();

    @Getter
    private final String sekiroGroup;

    /**
     * 由于多线程操作这个队列(包括rpc入口、admin管理、netty客户端线程)，
     * 两个client容器容易状态不同步，所以这里单独抽象，使用单线程事件循环方式避免资源状态问题<br>
     * 开源版这块使用的是加锁方法，代码极其丑陋
     */
    private final Looper looper;

    /**
     * 原则，clientList下线不remove，因为对他的遍历是耗时的。当client掉线之后，只有在sekiro的lb调度过程发现状态不OK才进行删除
     */
    private final LinkedList<NettyClient> clientList = new LinkedList<>();
    private final TreeMap<Long, NettyClient> consistentTreeMap = new TreeMap<>();
    private final TreeMap<String, NettyClient> offlineClients = new TreeMap<>();
    private final Recorder recorder;


    public NettySekiroGroup(String sekiroGroup) {
        this.sekiroGroup = sekiroGroup;
        this.recorder = EventRecordManager.acquireRecorder(sekiroGroup, EventScene.SEKIRO_GROUP);
        looper = new Looper("group-thread-" + sekiroGroup).startLoop();
    }

    public static List<String> groupList() {
        return new ArrayList<>(new TreeSet<>(sGroup.keySet()));
    }

    public void safeDo(Runnable runnable) {
        looper.post(runnable);
    }


    public void offlineClient(NettyClient nettyClient) {
        if (nettyClient == null) {
            return;
        }
        safeDo(() -> {
            NettyClient remove = consistentTreeMap.remove(nettyClient.getConsistentKey());
            if (remove == null) {
                return;
            }
            if (remove.isDown()) {
                return;
            }
            if (remove != nettyClient) {
                consistentTreeMap.put(remove.getConsistentKey(), remove);
                return;
            }
            nettyClient.offline = true;
            offlineClients.put(nettyClient.getClientId(), nettyClient);
        });
    }


    public void consistentAllocate(String key, ValueCallback<NettyClient> callback) {
        safeDo(() -> {
            long targetHash = ConsistentHashUtil.murHash(key);
            SortedMap<Long, NettyClient> subMap = consistentTreeMap.tailMap(targetHash);
            NettyClient natClient = fetchFromSortedMap(subMap);
            if (natClient == null) {
                natClient = fetchFromSortedMap(consistentTreeMap);
            }

            if (natClient == null) {
                ValueCallback.failed(wrapWithEmptyDelay(callback), "no device");
            } else {
                ValueCallback.success(wrapWithEmptyDelay(callback), natClient);
            }
        });
    }


    private NettyClient fetchFromSortedMap(SortedMap<Long, NettyClient> subMap) {
        looper.checkLooper();
        NettyClient ret = null;
        for (Long index : subMap.keySet()) {
            NettyClient natClient = subMap.get(index);
            if (!natClient.getChannel().isActive()
                    || natClient.offline) {
                consistentTreeMap.remove(index);
                continue;
            }
            ret = natClient;
            break;
        }
        return ret;
    }

    public void fetchByClientId(String clientId, ValueCallback<NettyClient> valueCallback) {
        safeDo(() -> {
            NettyClient nettyClient = consistentTreeMap.get(ConsistentHashUtil.murHash(clientId));
            if (nettyClient == null) {
                String msg = "no device found for clientId:" + clientId;
                recorder.recordEvent(msg);
                ValueCallback.failed(valueCallback, msg);
            } else if (!nettyClient.getClientId().equals(clientId)) {
                String msg = "hash conflict!! ？？ clientId1："
                        + clientId + " clientId2:" + nettyClient.getClientId()
                        + " murHash:" + ConsistentHashUtil.murHash(clientId);
                recorder.recordEvent(msg);
                ValueCallback.failed(valueCallback, msg);
            } else {
                ValueCallback.success(valueCallback, nettyClient);
            }
        });
    }


    public void queueRotateAllocate(ValueCallback<NettyClient> valueCallback) {
        safeDo(() -> {
            while (true) {
                NettyClient poll = clientList.poll();
                if (poll == null) {
                    ValueCallback.failed(wrapWithEmptyDelay(valueCallback), "no device");
                    return;
                }

                if (poll.isDown()) {
                    continue;
                }

                if (poll.offline) {
                    offlineClients.put(poll.getClientId(), poll);
                    continue;
                }
                clientList.addLast(poll);
                ValueCallback.success(wrapWithEmptyDelay(valueCallback), poll);
                return;
            }
        });
    }


    private int emptyQueueCounter = 0;
    private long firstEmptyTimestamp = 0;

    /**
     * 解决的问题：
     * sekiro单台服务器，下游一台服务节点，上游20台服务器
     * 下游节点宕机，上游20台服务器近2000个线程同时请求sekiro服务
     * <p>
     * 由于sekiro这里快速返回pool空状态，中间没有blocking的话，近似演变为2000个线程在伪死循环
     * 最终导致sekiroCPU占用率达整体的70%
     * <p>
     * 这里如果发生了下游节点为空，sekiro的策略改变为：对tcp处理进行一定程度的延时，规则如下：
     * 如果为空次数小于5，那么不进行延时
     * 如果空节点调用次数在6-10之间，那么对tcp连接的真实处理延时1-5秒
     * 如果空节点调用次数大于10，那么统一对该请求延时5s
     * 如果空节点调用时间超过10分钟且空调用次数大于100，那么统一增加延时15s
     * 如果空节点调用时间超过30分钟，那么统一增加延时20s
     * 假定2000个并发同时打到sekiro，且sekiro下游宕机，那么极限情况为这2000个连接同时加上了5秒的延时，整体qps为400
     */
    private ValueCallback<NettyClient> wrapWithEmptyDelay(ValueCallback<NettyClient> input) {
        return value -> {
            if (value.isSuccess()) {
                emptyQueueCounter = 0;
                ValueCallback.success(input, value.v);
                return;
            }
            if (emptyQueueCounter == 0) {
                firstEmptyTimestamp = System.currentTimeMillis();
            }
            emptyQueueCounter++;
            recorder.recordEvent("pool queue empty");
            if (emptyQueueCounter <= 5) {
                ValueCallback.failed(input, "pool queue empty");
                return;
            }
            int delaySecond = emptyQueueCounter - 4;
            if (delaySecond > 10 && emptyQueueCounter < 100) {
                delaySecond = 10;
            } else if (System.currentTimeMillis() - firstEmptyTimestamp > 10 * 60 * 1000) {
                delaySecond = 15;
            } else if (System.currentTimeMillis() - firstEmptyTimestamp > 10 * 60 * 1000) {
                delaySecond = 20;
            }
            looper.postDelay(() -> ValueCallback.failed(input, "pool queue empty"), delaySecond * 1000L);
        };
    }


    public void registerNettyClient(NettyClient nettyClient) {
        safeDo(() -> {
            NettyClient old = consistentTreeMap.remove(nettyClient.getConsistentKey());
            if (old != null) {
                old.offline = true;
                Channel historyChannel = old.getChannel();
                if (historyChannel.isActive()) {
                    recorder.recordEvent("duplicate client register old:" + historyChannel + " new:" + nettyClient.getChannel());
                    historyChannel.eventLoop().schedule((Runnable) historyChannel::close, 30, TimeUnit.SECONDS);
                }
            }
            consistentTreeMap.put(nettyClient.getConsistentKey(), nettyClient);
            clientList.addFirst(nettyClient);
        });

    }


    public void unregisterNettyClient(NettyClient nettyClient) {
        safeDo(() -> {
            nettyClient.offline = true;
            NettyClient old = consistentTreeMap.get(nettyClient.getConsistentKey());
            if (old == null) {
                recorder.recordEvent("unregister none exist NettyClient:" + nettyClient.getClientId());
                return;
            }

            // BugFix. 主要出现在网络状况不好时的重复注册场景。
            // 重复注册时，由于启动30s后close channel的任务，使得即使新的nettyClient注册到constantTreeMap,
            // 30后在constantTreeMap里新的nettyClient也会被删除。使得出现假死状态。即手机在线也无法执行任务操作。
            // 解决方案: 只有channel一致时才从consistentTreeMap里删除nettyClient对象.
            if (old.getChannel() == nettyClient.getChannel()) {
                consistentTreeMap.remove(nettyClient.getConsistentKey());
                offlineClients.remove(nettyClient.getClientId());
            }
        });

    }

    public static NettySekiroGroup createOrGet(String sekiroGroup) {
        NettySekiroGroup nettySekiroGroup = sGroup.get(sekiroGroup);
        if (nettySekiroGroup != null) {
            return nettySekiroGroup;
        }
        synchronized (NettySekiroGroup.class) {
            nettySekiroGroup = sGroup.get(sekiroGroup);
            if (nettySekiroGroup != null) {
                return nettySekiroGroup;
            }
            nettySekiroGroup = new NettySekiroGroup(sekiroGroup);
            sGroup.put(sekiroGroup, nettySekiroGroup);
        }
        return nettySekiroGroup;
    }


    // 两个统计使用的队列
    public void enabledQueue(ValueCallback<List<NettyClient>> valueCallback) {
        safeDo(() -> {
            List<NettyClient> ret = new LinkedList<>();
            for (NettyClient nettyClient : clientList) {
                if (nettyClient.getChannel().isActive()) {
                    ret.add(nettyClient);
                }
            }
            ValueCallback.success(valueCallback, ret);
        });
    }

    public void offlineQueue(ValueCallback<List<NettyClient>> valueCallback) {
        safeDo(() -> {
            List<NettyClient> ret = new LinkedList<>();
            for (NettyClient nettyClient : offlineClients.values()) {
                if (nettyClient.getChannel().isActive()) {
                    ret.add(nettyClient);
                }
            }
            ValueCallback.success(valueCallback, ret);
        });
    }

    public static void accessAndAllocate(JSONObject requestJson, ValueCallback<NettyClient> valueCallback) {
        String group = requestJson.getString(Constants.REVERSED_WORDS.GROUP);
        String bindClient = requestJson.getString(Constants.REVERSED_WORDS.BIND_CLIENT);
        String sekiroAllocateKey = requestJson.getString(Constants.REVERSED_WORDS.CONSTANT_INVOKE);

        if (StringUtils.isBlank(group)) {
            ValueCallback.failed(valueCallback, "the param {group} not presented");
            return;
        }


        NettySekiroGroup sekiroGroup = NettySekiroGroup.createOrGet(group);
        if (StringUtils.isNotBlank(bindClient)) {
            sekiroGroup.fetchByClientId(bindClient, valueCallback);
        } else if (StringUtils.isNotBlank(sekiroAllocateKey)) {
            sekiroGroup.consistentAllocate(sekiroAllocateKey, valueCallback);
        } else {
            sekiroGroup.queueRotateAllocate(valueCallback);
        }
    }

}
