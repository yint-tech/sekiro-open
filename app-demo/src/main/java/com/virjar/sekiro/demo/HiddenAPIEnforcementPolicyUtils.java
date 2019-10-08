package com.virjar.sekiro.demo;


import android.os.Build;
import android.util.Log;


import java.lang.reflect.Method;

//
// Created by Swift Gan on 2019/3/15.
//


//bypass hidden api on Android 9 - 10
public class HiddenAPIEnforcementPolicyUtils {

    private static Method addWhiteListMethod;

    private static Object vmRuntime;
    private static boolean sBypassedP = false;


    private static void passApiCheck() {
        try {
            Method getMethodMethod = Class.class.getDeclaredMethod("getDeclaredMethod", String.class, Class[].class);
            Method forNameMethod = Class.class.getDeclaredMethod("forName", String.class);
            Class vmRuntimeClass = (Class) forNameMethod.invoke(null, "dalvik.system.VMRuntime");
            addWhiteListMethod = (Method) getMethodMethod.invoke(vmRuntimeClass, "setHiddenApiExemptions", new Class[]{String[].class});
            Method getVMRuntimeMethod = (Method) getMethodMethod.invoke(vmRuntimeClass, "getRuntime", null);
            vmRuntime = getVMRuntimeMethod.invoke(null);

            addReflectionWhiteList("Landroid/",
                    "Lcom/android/",
                    "Ljava/lang/",
                    "Ldalvik/system/",
                    "Llibcore/io/",
                    "Lsun/misc/"
            );
        } catch (Throwable throwable) {
            Log.w(HiddenAPIEnforcementPolicyUtils.class.getSimpleName(), "pass Hidden API enforcement policy failed", throwable);
        }
    }

    //methidSigs like Lcom/swift/sandhook/utils/ReflectionUtils;->vmRuntime:java/lang/Object; (from hidden policy list)
    private static void addReflectionWhiteList(String... memberSigs) throws Throwable {
        addWhiteListMethod.invoke(vmRuntime, new Object[]{memberSigs});
    }

    private static int getPreviewSDKInt() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                return Build.VERSION.PREVIEW_SDK_INT;
            } catch (Throwable e) {
                // ignore
            }
        }
        return 0;
    }


    private static boolean isPie() {
        return Build.VERSION.SDK_INT > 27 || (Build.VERSION.SDK_INT == 27 && getPreviewSDKInt() > 0);
    }

    public static void bypassHiddenAPIEnforcementPolicyIfNeeded() {
        if (sBypassedP) {
            return;
        }
        if (isPie()) {
            try {
                //nativeBypassHiddenAPIEnforcementPolicy();
                passApiCheck();
            } catch (Throwable e) {
                e.printStackTrace();
            }
        }
        sBypassedP = true;
    }
}
