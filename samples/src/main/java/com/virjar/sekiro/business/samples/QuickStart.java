package com.virjar.sekiro.business.samples;

import com.virjar.sekiro.business.api.SekiroClient;
import com.virjar.sekiro.business.api.interfaze.ActionHandler;
import com.virjar.sekiro.business.api.interfaze.AutoBind;
import com.virjar.sekiro.business.api.interfaze.SekiroRequest;
import com.virjar.sekiro.business.api.interfaze.SekiroResponse;

import java.util.UUID;

public class QuickStart {
    public static void main(String[] args) {
        // https://sekiro.virjar.com/business/invoke?group=sample-test&action=test&param=testparm
        new SekiroClient("sample-test", UUID.randomUUID().toString()).setupSekiroRequestInitializer((sekiroRequest, handlerRegistry) ->

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
                // 启动SekiroClient
                .start();

        // https://sekiro.virjar.com/business/invoke?group=sample-test&action=test&param=testparm

        // group为刚刚设置的sample-test
        // action为刚刚设置的test
        // param=testparm 给参数字段param赋值：testparm
        // sekiro_token ：后台设置的鉴权token，没有token其他用户无法随便访问
    }
}
