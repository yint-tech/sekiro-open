package com.virjar.sekiro.business.netty.routers.client;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.virjar.sekiro.business.api.core.Context;
import com.virjar.sekiro.business.api.core.safethread.Looper;
import com.virjar.sekiro.business.api.core.safethread.ValueCallback;
import com.virjar.sekiro.business.netty.channel.Channel;
import com.virjar.sekiro.business.netty.util.ConstantHashUtil;
import lombok.Getter;

import java.util.*;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;

public class NettySekiroGroup extends Context {

    private static final ConcurrentMap<String, NettySekiroGroup> sGroup = Maps.newConcurrentMap();

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
    private final TreeMap<Long, NettyClient> constantTreeMap = new TreeMap<>();
    private final TreeMap<String, NettyClient> offlineClients = new TreeMap<>();

    public NettySekiroGroup(String sekiroGroup) {
        super(null);
        this.sekiroGroup = sekiroGroup;
        looper = new Looper(getLogger(), "group-thread-" + sekiroGroup);
    }

    public static List<String> groupList() {
        return Lists.newArrayList(new TreeSet<>(sGroup.keySet()));
    }

    public void safeDo(Runnable runnable) {
        looper.post(runnable);
    }

    public void queue(ValueCallback<List<NettyClient>> valueCallback) {
        safeDo(() -> valueCallback.onReceiveValue(Lists.newArrayList(clientList)));
    }

    public void offlineClient(NettyClient nettyClient) {
        if (nettyClient == null) {
            return;
        }
        safeDo(() -> {
            NettyClient remove = constantTreeMap.remove(nettyClient.getConstantKey());
            if (remove != null) {
                offlineClients.put(nettyClient.getClientId(), nettyClient);
                // clientList.remove(nettyClient);
                nettyClient.offline = true;
            }
        });
    }

    public void constantAllocate(String key, ValueCallback<NettyClient> callback) {
        safeDo(() -> {
            long targetHash = ConstantHashUtil.murHash(key);
            SortedMap<Long, NettyClient> subMap = constantTreeMap.tailMap(targetHash);
            NettyClient natClient = fetchFromSortedMap(subMap);
            if (natClient != null) {
                callback.onReceiveValue(natClient);
                return;
            }
            callback.onReceiveValue(fetchFromSortedMap(constantTreeMap));
        });
    }

    private NettyClient fetchFromSortedMap(SortedMap<Long, NettyClient> subMap) {
        looper.checkLooper();
        NettyClient ret = null;
        for (Long index : subMap.keySet()) {
            NettyClient natClient = subMap.get(index);
            if (!natClient.getChannel().isActive()
                    || natClient.offline) {
                constantTreeMap.remove(index);
                continue;
            }
            ret = natClient;
            break;
        }
        return ret;
    }

    public void fetchByClientId(String clientId, ValueCallback<NettyClient> valueCallback) {
        safeDo(() -> fetchByClientId0(clientId, valueCallback));
    }

    private void fetchByClientId0(String clientId, ValueCallback<NettyClient> valueCallback) {
        looper.checkLooper();
        NettyClient nettyClient = constantTreeMap.get(ConstantHashUtil.murHash(clientId));
        if (nettyClient != null && !nettyClient.getClientId().equals(clientId)) {
            getLogger().error("hash conflict!! ？？ clientId1："
                    + clientId + " clientId2:" + nettyClient.getClientId()
                    + " murHash:" + ConstantHashUtil.murHash(clientId));
            nettyClient = null;
        }
        valueCallback.onReceiveValue(nettyClient);
    }


    public void allocate(ValueCallback<NettyClient> valueCallback) {
        safeDo(() -> allocate0(valueCallback));
    }

    private void allocate0(ValueCallback<NettyClient> valueCallback) {
        looper.checkLooper();
        while (true) {
            NettyClient poll = clientList.poll();
            if (poll == null) {
                getLogger().warn("pool queue empty");
                valueCallback.onReceiveValue(null);
                return;
            }

            if (!poll.getChannel().isActive() || poll.offline) {
                constantTreeMap.remove(poll.getConstantKey());
                continue;
            }

            clientList.addLast(poll);
            valueCallback.onReceiveValue(poll);
            return;
        }
    }

    public void registerNettyClient(NettyClient nettyClient) {
        safeDo(() -> registerNettyClient0(nettyClient));
    }

    private void registerNettyClient0(NettyClient nettyClient) {
        looper.checkLooper();
        NettyClient old = constantTreeMap.get(nettyClient.getConstantKey());
        // BugFix. 对于已注册的手机，执行overwrite之后，
        // nettyClient.doRegister方法未返回old nettyClient，却使用new nettyClient，导致invoke调用时nettyClient对象不一致，触发异常
        // 解决方案: 删除old nettyClient,添加new nettyClient.
        if (null != old) {
            Channel historyChannel = old.getChannel();
            if (historyChannel.isActive()) {
                getLogger().error("duplicate client register old:" + historyChannel
                        + " new:" + nettyClient.getChannel());
                historyChannel.eventLoop().schedule((Runnable) historyChannel::close, 30, TimeUnit.SECONDS);
            }
            constantTreeMap.remove(old.getConstantKey());
            clientList.remove(old);

            constantTreeMap.put(nettyClient.getConstantKey(), nettyClient);
            clientList.addFirst(nettyClient);
        } else {
            constantTreeMap.put(nettyClient.getConstantKey(), nettyClient);
            clientList.addFirst(nettyClient);
        }
    }

    public void unregisterNettyClient(NettyClient nettyClient) {
        if (looper.inLooper()) {
            unregisterNettyClient0(nettyClient);
            return;
        }
        looper.post(() -> unregisterNettyClient0(nettyClient));
    }

    public void unregisterNettyClient0(NettyClient nettyClient) {
        looper.checkLooper();
        NettyClient old = constantTreeMap.get(nettyClient.getConstantKey());
        if (old == null) {
            getLogger().warn("unregister none exist NettyClient:" + nettyClient.getClientId());
            return;
        }
        constantTreeMap.remove(nettyClient.getConstantKey());
        //clientList.remove(old);
        offlineClients.remove(nettyClient.getClientId());
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
            List<NettyClient> ret = Lists.newLinkedList();
            for (NettyClient nettyClient : clientList) {
                if (nettyClient.getChannel().isActive()) {
                    ret.add(nettyClient);
                }
            }
            valueCallback.onReceiveValue(ret);
        });
    }

    public void offlineQueue(ValueCallback<List<NettyClient>> valueCallback) {
        safeDo(() -> {
            List<NettyClient> ret = Lists.newLinkedList();
            for (NettyClient nettyClient : offlineClients.values()) {
                if (nettyClient.getChannel().isActive()) {
                    ret.add(nettyClient);
                }
            }
            valueCallback.onReceiveValue(ret);
        });
    }


    @Override
    public String getClientId() {
        return "group:" + sekiroGroup + ":system-fake";
    }
}
