package com.virjar.sekiro.demo;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;
import android.util.ArrayMap;

public class MainThreadRunner {
    public static void runOnFocusActivity(final FocusActivityOccurEvent focusActivityOccurEvent) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            findAndFire(focusActivityOccurEvent);
        } else {
            new Handler(Looper.getMainLooper()).post(new Runnable() {
                @Override
                public void run() {
                    findAndFire(focusActivityOccurEvent);
                }
            });
        }
    }

    public interface FocusActivityOccurEvent {
        boolean onFocusActivityOccur(Activity activity);

        void onActivityEmpty();

        //onLostF
    }

    private static void findAndFire(final FocusActivityOccurEvent focusActivityOccurEvent) {
        ArrayMap mActivities = ReflectUtil.getObjectField(ReflectUtil.getMainThread(), "mActivities");
        Activity topActivity = null;

        if (mActivities.values().size() == 0) {
            focusActivityOccurEvent.onActivityEmpty();
        }
        for (Object activityClientRecord : mActivities.values()) {
            Activity tempActivity = (Activity) ReflectUtil.getObjectField(activityClientRecord, "activity");
            if (tempActivity.hasWindowFocus()) {
                topActivity = tempActivity;
                break;
            }
        }

        if (topActivity == null) {
            new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                @Override
                public void run() {
                    findAndFire(focusActivityOccurEvent);
                }
            }, 500);
            return;
        }
        if (focusActivityOccurEvent.onFocusActivityOccur(topActivity)) {
            new Handler(Looper.getMainLooper()).post(new Runnable() {
                @Override
                public void run() {
                    findAndFire(focusActivityOccurEvent);
                }
            });
        }
    }
}
