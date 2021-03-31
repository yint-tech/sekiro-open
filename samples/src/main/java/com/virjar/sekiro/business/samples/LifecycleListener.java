package com.virjar.sekiro.business.samples;

import com.virjar.sekiro.business.api.SekiroClient;
import com.virjar.sekiro.business.api.bootstrap.Bootstrap;
import com.virjar.sekiro.business.api.interfaze.*;

public class LifecycleListener {
    public static void main(String[] args) {
        Bootstrap.newSekiroClientBuilder()
                // 设置目标group，必须
                .sekiroGroup("test")
                // handler挂载器
                .setupSekiroRequestInitializer((sekiroRequest, handlerRegistry) -> handlerRegistry.registerSekiroHandler(new LifecycleTestAction()))

                .addSekiroClientListener(new SekiroClientLifecycleListener() {
                    @Override
                    public void onClientConnected(SekiroClient sekiroClient) {
                        System.out.println("客户端连接服务器成功");
                    }

                    @Override
                    public void onClientDisConnected(SekiroClient sekiroClient) {
                        System.out.println("客户端断开连接");
                    }

                    @Override
                    public void onClientDestroy(SekiroClient sekiroClient) {
                        System.out.println("客户端被销毁");
                    }

                    /**
                     * 。。。在未来这里可能还会增加其他的生命周期挂载点
                     */
                })
                // 构建client对象
                .build()
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
