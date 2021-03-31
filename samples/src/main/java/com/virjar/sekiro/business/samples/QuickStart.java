package com.virjar.sekiro.business.samples;

import com.virjar.sekiro.business.api.bootstrap.Bootstrap;
import com.virjar.sekiro.business.api.interfaze.ActionHandler;
import com.virjar.sekiro.business.api.interfaze.AutoBind;
import com.virjar.sekiro.business.api.interfaze.SekiroRequest;
import com.virjar.sekiro.business.api.interfaze.SekiroResponse;

public class QuickStart {
    public static void main(String[] args) {
        // https://sekiro.virjar.com/business/invoke?group=test&action=test&sekiro_token=7cd51507-cb3a-4a8a-aba2-4c6d66906e9d&param=testparm

        // 新建一个构建器
        Bootstrap.newSekiroClientBuilder()
                // 设置目标group，必须
                .sekiroGroup("test")
                // handler挂载器
                .setupSekiroRequestInitializer((sekiroRequest, handlerRegistry) ->

                        //注册handler
                        handlerRegistry.registerSekiroHandler(new ActionHandler() {

                            // 参数绑定规则，将参数的param赋值，如果没有传递，则设置为默认值defaultParam
                            @AutoBind(defaultValue = "defaultParam")
                            private String param;

                            // 参数绑定规则，将参数的intParam赋值，如果没有传递，则设置为默认值12
                            @AutoBind(defaultValue = "12")
                            private Integer intParam;

                            @Override
                            public void handleRequest(SekiroRequest sekiroRequest, SekiroResponse sekiroResponse) {
                                sekiroResponse.success("param：" + param
                                        + " intParam:" + intParam);

                            }

                            @Override
                            public String action() {
                                // actionHandler通过这个函数指定action名称
                                return "test";
                            }
                        }))
                // 构建client对象
                .build()
                // 启动SekiroClient
                .start();

        // https://sekiro.virjar.com/business/invoke?group=test&action=test&sekiro_token=7cd51507-cb3a-4a8a-aba2-4c6d66906e9d&param=testparm

        // group为刚刚设置的test
        // action为刚刚设置的test
        // param=testparm 给参数字段param赋值：testparm
        // sekiro_token ：后台设置的鉴权token，没有token其他用户无法随便访问
    }
}
