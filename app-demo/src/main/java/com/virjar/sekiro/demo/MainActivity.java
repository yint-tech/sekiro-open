package com.virjar.sekiro.demo;

import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.text.TextUtils;
import android.widget.TextView;

import com.virjar.sekiro.api.SekiroClient;
import com.virjar.sekiro.demo.handlers.ClientTimeHandler;
import com.virjar.sekiro.demoapp.R;

public class MainActivity extends AppCompatActivity {

    TextView textView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);

        textView = findViewById(R.id.text);


        new Thread() {
            @Override
            public void run() {
                while (!Thread.currentThread().isInterrupted()) {
                    FingerData fingerData = FingerDataLoader.getFingerData();

                    if (TextUtils.isEmpty(fingerData.getImei())
                            || TextUtils.isEmpty(fingerData.getSerial())) {
                        try {
                            Thread.sleep(20000);
                            continue;
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                    String clientId = fingerData.getSerial() + "_" + fingerData.getImei();
                    final SekiroClient sekiroClient = SekiroClient.start("sekiro.virjar.com", clientId, "sekiro-demo");

                    sekiroClient.registerHandler("clientTime", new ClientTimeHandler());
                    break;
                }
            }
        }.start();


    }

    @Override
    protected void onResume() {
        super.onResume();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

    }


}
