package com.virjar.sekiro.business.samples;

import com.virjar.sekiro.business.api.SekiroInvoker;
import com.virjar.sekiro.business.api.invoker.Call;
import com.virjar.sekiro.business.api.invoker.Callback;
import com.virjar.sekiro.business.api.invoker.InvokerRequest;
import com.virjar.sekiro.business.api.invoker.InvokerResponse;

import java.io.IOException;


public class SekiroInvokerStart {
    private static final SekiroInvoker sekiroInvoker = new SekiroInvoker();

    public static void main(String[] args) {


        InvokerRequest invokerRequest = new InvokerRequest.Builder()
                .group("test")
                .action("test")
                .field("param", "testParam")
                .build();

        sekiroInvoker.newCall(invokerRequest).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                e.printStackTrace();
            }

            @Override
            public void onResponse(Call call, InvokerResponse response) {
                System.out.println(response.string());
            }
        });
    }
}
