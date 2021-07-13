package com.virjar.sekiro.demo.handlers;


import com.virjar.sekiro.business.api.interfaze.Action;
import com.virjar.sekiro.business.api.interfaze.AutoBind;
import com.virjar.sekiro.business.api.interfaze.RequestHandler;
import com.virjar.sekiro.business.api.interfaze.SekiroRequest;
import com.virjar.sekiro.business.api.interfaze.SekiroResponse;
import com.virjar.sekiro.demo.XposedMain;

@Action("clientTime")
public class ClientTimeHandler implements RequestHandler {
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
        sekiroResponse.success("process: " + XposedMain.loadPackageParam.processName + " : now:" + System.currentTimeMillis() + " your param1:" + param1);
    }
}
