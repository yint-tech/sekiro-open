package com.virjar.sekiro.server.netty;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import io.netty.channel.Channel;
import lombok.extern.slf4j.Slf4j;

@Slf4j
class ClientGroup {
    ClientGroup(String group) {
        this.group = group;
    }

    private String group;
    private Map<String, NatClient> natClientMap = Maps.newConcurrentMap();

    private LinkedList<String> poolQueue = new LinkedList<>();

    synchronized String disconnect(String clientId) {
        NatClient natClient = natClientMap.get(clientId);
        natClientMap.remove(clientId);
        removeQueue(clientId);
        if (natClient == null) {
            return "no client: " + clientId;
        } else {
            natClient.getCmdChannel().close();
        }
        return null;
    }

    //对象操作全部加锁，防止并发紊乱
    synchronized List<NatClient> queue() {
        List<NatClient> ret = Lists.newArrayListWithCapacity(poolQueue.size());
        // java.util.ConcurrentModificationException
        for (String key : Lists.newArrayList(poolQueue)) {
            NatClient natClient = natClientMap.get(key);
            if (natClient == null) {
                natClientMap.remove(key);
                removeQueue(key);
                continue;
            }
            Channel cmdChannel = natClient.getCmdChannel();
            if (cmdChannel == null || !cmdChannel.isActive()) {
                natClientMap.remove(key);
                removeQueue(key);
                continue;
            }
            ret.add(natClient);
        }
        return ret;
    }

    synchronized NatClient getByClientId(String clientId) {
        NatClient ret = natClientMap.get(clientId);
        if (ret == null) {
            return null;
        }
        if (!ret.getCmdChannel().isActive()) {
            natClientMap.remove(clientId);
            removeQueue(clientId);
        }
        return ret;
    }

    synchronized NatClient allocateOne() {
        while (true) {
            String poll = poolQueue.poll();
            if (poll == null) {
                log.info("pool queue empty for group:{}", group);
                return null;
            }

            NatClient natClient = natClientMap.get(poll);
            if (natClient == null) {
                continue;
            }
            if (natClient.getCmdChannel() == null) {
                natClientMap.remove(poll);
                continue;
            }
            if (!natClient.getCmdChannel().isActive()) {
                natClientMap.remove(poll);
                continue;
            }
            poolQueue.add(poll);
            return natClient;
        }

    }


    synchronized void registryClient(String client, Channel cmdChannel, NatClient.NatClientType natClientType) {
        NatClient oldNatClient = natClientMap.get(client);
//        if (natClient != null) {
//            Channel cmdChannelOld = natClient.getCmdChannel();
//            if (cmdChannelOld != cmdChannel) {
//                log.info("old channel exist,attach again，oldChannel:{}  now channel:{} client:{}", cmdChannelOld, cmdChannel, client);
//                natClient.attachChannel(cmdChannel);
//            }
//            return;
//        }
        log.info("register a client :{} with channel:{} ", client, cmdChannel);
        NatClient natClient = new NatClient(client, group, cmdChannel, natClientType);
        if ((oldNatClient != null)) {
            natClient.migrateSeqGenerator(oldNatClient);
        }
        natClientMap.put(client, natClient);
        removeQueue(client);
        poolQueue.add(client);
    }

    private void removeQueue(String clientId) {
        while (poolQueue.remove(clientId)) ;
    }
}
