package com.virjar.sekiro.demo;

import android.app.Application;

public class DemoApplication extends Application {

    private static DemoApplication mInstance = null;

    public static DemoApplication getInstance() {
        return mInstance;
    }


    @Override
    public void onCreate() {
        super.onCreate();
        mInstance = this;
        HiddenAPIEnforcementPolicyUtils.bypassHiddenAPIEnforcementPolicyIfNeeded();

        FingerDataLoader.refreshFinger();


    }
}
