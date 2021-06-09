package com.virjar.sekiro.business.samples;

import com.virjar.sekiro.business.api.SekiroClient;
import com.virjar.sekiro.business.api.core.eventbus.event.client.SekiroClientConnectEvent;
import com.virjar.sekiro.business.api.core.eventbus.event.client.SekiroClientDestroyEvent;
import com.virjar.sekiro.business.api.core.eventbus.event.client.SekiroClientDisConnectedEvent;
import com.virjar.sekiro.business.api.interfaze.*;

import java.util.UUID;

public class LifecycleListener {
    public static void main(String[] args) {
        SekiroClient sekiroClient = new SekiroClient("test", UUID.randomUUID().toString());
        sekiroClient
                // handler挂载器
                .setupSekiroRequestInitializer((sekiroRequest, handlerRegistry) -> handlerRegistry.registerSekiroHandler(new LifecycleTestAction()))

                .addEventListener(new SekiroClientConnectEvent() {
                    @Override
                    public void onClientConnected(SekiroClient sekiroClient) {
                        System.out.println("客户端连接服务器成功");
                    }
                })
                .addEventListener(new SekiroClientDisConnectedEvent() {
                    @Override
                    public void onClientDisConnected(SekiroClient sekiroClient) {
                        System.out.println("客户端断开连接");
                    }
                })
                .addEventListener(new SekiroClientDestroyEvent() {
                    @Override
                    public void onClientDestroy(SekiroClient sekiroClient) {
                        System.out.println("客户端被销毁");
                    }
                })
                // 启动SekiroClient
                .start();
    }

    /**
     * 这是另一种设置action的方法，在handler为独立的文件的时候，使用注解确定action将会更加优雅
     */
    @Action("Lifecycle")
    private static class LifecycleTestAction implements RequestHandler {

        @Override
        public void handleRequest(SekiroRequest sekiroRequest, SekiroResponse sekiroResponse) {

            sekiroResponse.success("success：");
        }
    }
}
