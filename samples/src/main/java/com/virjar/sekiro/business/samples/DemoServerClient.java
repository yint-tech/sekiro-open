package com.virjar.sekiro.business.samples;

import com.virjar.sekiro.business.api.ClusterSekiroClient;
import com.virjar.sekiro.business.api.SekiroClient;

import java.util.UUID;

public class DemoServerClient {
    public static void main(String[] args) {
        // 使用集群客户端访问连接demo server ，不推荐。demo server没有使用集群客户端的必要。因为demo server都是单点的
        ClusterSekiroClient demoServerClient = new ClusterSekiroClient("test-group", UUID.randomUUID().toString(), "http://sekiro.virjar.com:5620/business-demo/mock-allocate?ip=sekiro.virjar.com");

        // 通过指定服务器地址的方式连接到demo server，推荐这种方案。
        SekiroClient sekiroClient = new SekiroClient("test", UUID.randomUUID().toString(), "sekiro.virjar.com", 5620);
    }
}
