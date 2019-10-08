package com.virjar.sekiro.demo;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.support.v4.app.ActivityCompat;
import android.telephony.TelephonyManager;
import android.util.Log;

import lombok.Getter;

public class FingerDataLoader {
    @Getter
    private static FingerData fingerData = new FingerData();
    private static final int defaultRequestCode = 20;

    private static String TAG = "FingerDataLoader";

    public static void refreshFinger() {
        getImei();
        getSerial();
    }


    public static void setLocation(double latitude, double longitude) {
        fingerData.setLatitude(latitude);
        fingerData.setLongitude(longitude);
    }

    private static MainThreadRunner.FocusActivityOccurEvent requestPhoneStatusPermissionHandler = new MainThreadRunner.FocusActivityOccurEvent() {

        @Override
        public boolean onFocusActivityOccur(Activity activity) {
            if (ActivityCompat.checkSelfPermission(DemoApplication.getInstance(), Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED) {
                return true;
            }
            ActivityCompat.requestPermissions(activity, new String[]{Manifest.permission.READ_PHONE_STATE}, defaultRequestCode);
            Log.i(TAG, "need READ_PHONE_STATE permission");
            new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                @Override
                public void run() {
                    refreshFinger();
                }
            }, 2000);
            return true;
        }

        @Override
        public void onActivityEmpty() {

        }
    };

    @SuppressLint("HardwareIds")
    private static void getImei() {
        TelephonyManager telephonyManager = (TelephonyManager) DemoApplication.getInstance().getSystemService(Context.TELEPHONY_SERVICE);
        if (telephonyManager == null) {
            Log.e(TAG, "can not get telephonyManager instance");
            return;
        }

        if (ActivityCompat.checkSelfPermission(DemoApplication.getInstance(), Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
            MainThreadRunner.runOnFocusActivity(requestPhoneStatusPermissionHandler);
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            fingerData.setImei(telephonyManager.getImei());
        } else {
            fingerData.setImei(telephonyManager.getDeviceId());
        }

    }

    @SuppressLint("HardwareIds")
    private static void getSerial() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (ActivityCompat.checkSelfPermission(DemoApplication.getInstance(), Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
                MainThreadRunner.runOnFocusActivity(requestPhoneStatusPermissionHandler);
                return;
            }
            fingerData.setSerial(Build.getSerial());
        }
        fingerData.setSerial(Build.SERIAL);
    }


}
