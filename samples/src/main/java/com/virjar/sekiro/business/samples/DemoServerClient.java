package com.virjar.sekiro.business.samples;

import com.virjar.sekiro.business.api.SekiroClient;
import com.virjar.sekiro.business.api.interfaze.*;

import java.util.UUID;

public class DemoServerClient {
    public static void main(String[] args) {

        // 通过指定服务器地址的方式连接到demo server，推荐这种方案。
        SekiroClient sekiroClient = new SekiroClient("test_group", UUID.randomUUID().toString(), "127.0.0.1", 5620);


        sekiroClient.setupSekiroRequestInitializer((sekiroRequest, handlerRegistry) -> handlerRegistry.registerSekiroHandler(new ActionHandler() {
            @Override
            public String action() {
                return "test";
            }

            @Override
            public void handleRequest(SekiroRequest sekiroRequest, SekiroResponse sekiroResponse) {

                sekiroResponse.success("testParam:" + sekiroRequest.getString("testParam")
                        + " not Time:" + System.currentTimeMillis());
            }
        })).start();

        // 测试步骤：
        // 1. 用idea或者AndroidStudio打开 本项目
        // 2. 运行： sekiro-service-demo/src/main/java/com/virjar/sekiro/business/netty/Bootstrap.java
        // 3. 运行： samples/src/main/java/com/virjar/sekiro/business/samples/DemoServerClient.java
        // 4. 在浏览器中打开： http://127.0.0.1:5620/business-demo/invoke?group=test_group&action=test&testParam=%E5%8F%82%E6%95%B0%E4%BC%A0%E9%80%92%E6%B5%8B%E8%AF%95

    }
}
