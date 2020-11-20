package com.virjar.sekiro.demo.handlers;

import com.virjar.sekiro.api.SekiroRequest;
import com.virjar.sekiro.api.SekiroRequestHandler;
import com.virjar.sekiro.api.SekiroResponse;
import com.virjar.sekiro.api.databind.AutoBind;
import com.virjar.sekiro.demo.DemoApplication;

public class ClientTimeHandler implements SekiroRequestHandler {
    @AutoBind
    private String param1;

    @AutoBind
    private Integer sleep;

    @Override
    public void handleRequest(SekiroRequest sekiroRequest, SekiroResponse sekiroResponse) {
        if (sleep != null && sleep > 0) {
            try {
                Thread.sleep(sleep * 1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        sekiroResponse.success("process: " + DemoApplication.getInstance().getPackageName() + " : now:" + System.currentTimeMillis() + " your param1:" + param1);
    }
}
