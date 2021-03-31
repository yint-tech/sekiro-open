package com.virjar.sekiro.business.samples;

import com.virjar.sekiro.business.api.SekiroInvokerClient;
import com.virjar.sekiro.business.api.invoker.Call;
import com.virjar.sekiro.business.api.invoker.Callback;
import com.virjar.sekiro.business.api.invoker.InvokerRequest;
import com.virjar.sekiro.business.api.invoker.InvokerResponse;

import java.io.IOException;


public class SekiroInvokerStart {
    public static void main(String[] args) {

        SekiroInvokerClient sekiroInvokerClient = new SekiroInvokerClient();

        InvokerRequest invokerRequest = new InvokerRequest.Builder()
                .group("test")
                .action("test")
                .apiToken("7cd51507-cb3a-4a8a-aba2-4c6d66906e9d")
                .field("param", "testParam")
                .build();

        sekiroInvokerClient.newCall(invokerRequest).enqueue(new Callback() {
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
