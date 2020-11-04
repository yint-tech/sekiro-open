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
    private Map<String, NatClient> enableNatClientMap = Maps.newConcurrentMap();
    private Map<String, NatClient> disableNatClientMap = Maps.newConcurrentMap();

    private LinkedList<String> poolQueue = new LinkedList<>();

    synchronized String disconnect(String clientId) {
        NatClient natClient = enableNatClientMap.get(clientId);
        enableNatClientMap.remove(clientId);
        removeQueue(clientId);
        if (natClient == null) {
            return "no client: " + clientId;
        } else {
            natClient.getCmdChannel().close();
        }
        return null;
    }

    synchronized String disable(String clientId) {
        NatClient natClient = enableNatClientMap.get(clientId);
        if (natClient == null) {
            return "enable not have the client: " + clientId;
        }
        disableNatClientMap.put(clientId, natClient);
        enableNatClientMap.remove(clientId);
        removeQueue(clientId);
        return null;
    }

    synchronized String enable(String clientId) {
        NatClient natClient = disableNatClientMap.get(clientId);
        if (natClient == null) {
            return "disable not have the client: " + clientId;
        }
        if (!natClient.getCmdChannel().isActive()) {
            disableNatClientMap.remove(clientId);
            return "client is not active: " + clientId;
        }
        log.info("enable a client :{} with channel:{} ", clientId, natClient.getCmdChannel());
        enableNatClientMap.put(clientId, natClient);
        disableNatClientMap.remove(clientId);
        removeQueue(clientId);
        poolQueue.add(clientId);
        return null;
    }

    //对象操作全部加锁，防止并发紊乱
    synchronized List<NatClient> queue() {
        List<NatClient> ret = Lists.newArrayListWithCapacity(poolQueue.size());
        // java.util.ConcurrentModificationException
        for (String key : Lists.newArrayList(poolQueue)) {
            NatClient natClient = enableNatClientMap.get(key);
            if (natClient == null) {
                enableNatClientMap.remove(key);
                removeQueue(key);
                continue;
            }
            Channel cmdChannel = natClient.getCmdChannel();
            if (cmdChannel == null || !cmdChannel.isActive()) {
                enableNatClientMap.remove(key);
                removeQueue(key);
                continue;
            }
            ret.add(natClient);
        }
        return ret;
    }

    synchronized List<NatClient> queueDisable() {
        List<NatClient> ret = Lists.newArrayList();
        // java.util.ConcurrentModificationException
        for (String key : Lists.newArrayList(disableNatClientMap.keySet())) {
            NatClient natClient = disableNatClientMap.get(key);
            if (natClient == null) {
                disableNatClientMap.remove(key);
                continue;
            }
            Channel cmdChannel = natClient.getCmdChannel();
            if (cmdChannel == null || !cmdChannel.isActive()) {
                disableNatClientMap.remove(key);
                continue;
            }
            ret.add(natClient);
        }
        return ret;
    }

    synchronized NatClient getByClientId(String clientId) {
        NatClient ret = enableNatClientMap.get(clientId);
        if (ret == null) {
            return null;
        }
        if (!ret.getCmdChannel().isActive()) {
            enableNatClientMap.remove(clientId);
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

            NatClient natClient = enableNatClientMap.get(poll);
            if (natClient == null) {
                continue;
            }
            if (natClient.getCmdChannel() == null) {
                enableNatClientMap.remove(poll);
                continue;
            }
            if (!natClient.getCmdChannel().isActive()) {
                enableNatClientMap.remove(poll);
                continue;
            }
            poolQueue.add(poll);
            return natClient;
        }

    }


    synchronized void registryClient(String client, Channel cmdChannel, NatClient.NatClientType natClientType) {
        NatClient oldNatClient = enableNatClientMap.get(client);
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
        enableNatClientMap.put(client, natClient);
        removeQueue(client);
        poolQueue.add(client);
    }

    private void removeQueue(String clientId) {
        while (poolQueue.remove(clientId)) ;
    }
}
