package com.virjar.sekiro.server.netty;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

import org.apache.commons.lang3.StringUtils;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.LinkedBlockingDeque;

import io.netty.channel.Channel;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ChannelRegistry {

    private ChannelRegistry() {
    }

    private static ChannelRegistry instance = new ChannelRegistry();

    public static ChannelRegistry getInstance() {
        return instance;
    }

    private Map<String, ClientGroup> clientGroupMap = Maps.newConcurrentMap();

    private ClientGroup createOrGet(String group) {
        ClientGroup clientGroup = clientGroupMap.get(group);
        if (clientGroup != null) {
            return clientGroup;
        }
        synchronized (ChannelRegistry.class) {
            clientGroup = clientGroupMap.get(group);
            if (clientGroup != null) {
                return clientGroup;
            }
            clientGroup = new ClientGroup(group);
            clientGroupMap.put(group, clientGroup);
            return clientGroup;
        }
    }

    private static class ClientGroup {
        public ClientGroup(String group) {
            this.group = group;
        }

        private String group;
        private Map<String, NatClient> natClientMap = Maps.newConcurrentMap();

        private BlockingDeque<NatClient> poolQueue = new LinkedBlockingDeque<>();

        public NatClient getByClientId(String clientId) {
            NatClient ret = natClientMap.get(clientId);
            if (ret != null && !ret.getCmdChannel().isActive()) {
                natClientMap.remove(clientId);
            }
            return ret;
        }

        public NatClient allocateOne() {
            while (true) {
                NatClient poll = poolQueue.poll();
                if (poll == null) {
                    log.info("pool queue empty");
                    return null;
                }
                if (!poll.getCmdChannel().isActive()) {
                    //TODO queue 的数据结构不合理，需要支持线性remove
                    NatClient realNatClient = natClientMap.get(poll.getClientId());
                    if (realNatClient == poll) {
                        log.info("remove channel for client:{}", poll.getClientId());
                        natClientMap.remove(poll.getClientId());
                    }
                    continue;
                }

                poolQueue.add(poll);
                return poll;
            }

        }


        public synchronized void registryClient(String client, Channel cmdChannel) {

            NatClient natClient = natClientMap.get(client);
            if (natClient != null) {
                Channel cmdChannelOld = natClient.getCmdChannel();
                if (cmdChannelOld != cmdChannel) {
                    log.info("old channel exist,attach again，oldChannel:{}  now channel:{} client:{}", cmdChannelOld, cmdChannel, client);
                    natClient.attachChannel(cmdChannel);
                }
                return;
            }
            log.info("register a client :{} with channel:{} ", client, cmdChannel);
            natClient = new NatClient(client, group, cmdChannel);
            natClientMap.put(client, natClient);
            poolQueue.add(natClient);
        }


    }


    public synchronized void registryClient(String client, Channel cmdChannel) {
        log.info("register for client:{}", client);

        int index = client.indexOf("@");
        if (index < 0) {
            return;
        }
        String[] clientAndGroup = client.split("@");
        String group = clientAndGroup[1];
        String clientId = clientAndGroup[0];
        createOrGet(group).registryClient(clientId, cmdChannel);

    }


    public NatClient allocateOne(String group) {
        if (StringUtils.isBlank(group)) {
            group = "default";
        }
        return createOrGet(group).allocateOne();
    }

    public NatClient queryByClient(String group, String clientId) {
        if (StringUtils.isBlank(group)) {
            return queryByClient(clientId);
        }
        return createOrGet(group).getByClientId(clientId);
    }

    private NatClient queryByClient(String clientId) {
        for (ClientGroup clientGroup : clientGroupMap.values()) {
            NatClient natClient = clientGroup.getByClientId(clientId);
            if (natClient != null) {
                Channel cmdChannel = natClient.getCmdChannel();
                if (cmdChannel != null && cmdChannel.isActive()) {
                    return natClient;
                }
            }
        }
        return null;
    }

    public List<String> channelStatus(String group) {
        if (group == null) {
            return Collections.emptyList();
        }
        ClientGroup clientGroup = clientGroupMap.get(group);
        if (clientGroup == null) {
            return Collections.emptyList();
        }
        Collection<NatClient> natClients = clientGroup.natClientMap.values();
        List<String> clientVo = Lists.newArrayList();
        for (NatClient natClient : natClients) {
            if (natClient.getCmdChannel() != null && natClient.getCmdChannel().isActive()) {
                clientVo.add(natClient.getClientId());
            }
        }
        return clientVo;
    }


    public List<String> channelList() {
        return Lists.newArrayList(clientGroupMap.keySet());
    }

}
