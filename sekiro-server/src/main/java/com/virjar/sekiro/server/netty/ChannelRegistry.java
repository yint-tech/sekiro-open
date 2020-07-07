package com.virjar.sekiro.server.netty;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.virjar.sekiro.api.CommonRes;

import org.apache.commons.lang3.StringUtils;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

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


    public void registryClient(String client, Channel cmdChannel, NatClient.NatClientType natClientType) {
        log.info("register for client:{}", client);

        int index = client.indexOf("@");
        if (index < 0) {
            return;
        }
        String[] clientAndGroup = client.split("@");
        String group = clientAndGroup[1];
        String clientId = clientAndGroup[0];
        createOrGet(group).registryClient(clientId, cmdChannel, natClientType);
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
        Collection<NatClient> natClients = clientGroup.queue();
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

    public CommonRes<?> forceDisconnect(String group, String clientId) {
        if (group == null) {
            return CommonRes.failed("need param:{group}");
        }
        ClientGroup clientGroup = clientGroupMap.get(group);
        if (clientGroup == null) {
            return CommonRes.failed("no group:{" + group + "}");
        }
        String errorMessage = clientGroup.disconnect(clientId);
        if (errorMessage != null) {
            return CommonRes.failed(errorMessage);
        }
        return CommonRes.success("ok");
    }
}
