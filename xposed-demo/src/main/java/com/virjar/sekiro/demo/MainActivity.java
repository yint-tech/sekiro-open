package com.virjar.sekiro.demo;

import android.app.Activity;
import android.os.Bundle;
import android.support.annotation.Nullable;

import com.virjar.sekiro.business.api.SekiroClient;
import com.virjar.sekiro.business.api.interfaze.HandlerRegistry;
import com.virjar.sekiro.business.api.interfaze.SekiroRequest;
import com.virjar.sekiro.business.api.interfaze.SekiroRequestInitializer;
import com.virjar.sekiro.demo.handlers.ClientTimeHandler;
import com.virjar.sekiro.demoapp.R;

import java.util.UUID;

public class MainActivity extends Activity {
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // 在普通Android应用中使用sekiro
        new SekiroClient("test-android", UUID.randomUUID().toString())
                .setupSekiroRequestInitializer(new SekiroRequestInitializer() {
                    @Override
                    public void onSekiroRequest(SekiroRequest sekiroRequest, HandlerRegistry handlerRegistry) {
                        handlerRegistry.registerSekiroHandler(new ClientTimeHandler());
                    }
                }).start();
    }
}
