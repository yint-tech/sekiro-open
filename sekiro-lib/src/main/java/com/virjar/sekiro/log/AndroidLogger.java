package com.virjar.sekiro.log;

import android.util.Log;

public class AndroidLogger implements ILogger {


    @Override
    public void info(String msg) {
        Log.i(SekiroLogger.tag, msg);
    }

    @Override
    public void info(String msg, Throwable throwable) {
        Log.i(SekiroLogger.tag, msg, throwable);
    }

    @Override
    public void warn(String msg) {
        Log.w(SekiroLogger.tag, msg);
    }

    @Override
    public void warn(String msg, Throwable throwable) {
        Log.w(SekiroLogger.tag, msg, throwable);
    }

    @Override
    public void error(String msg) {
        Log.e(SekiroLogger.tag, msg);
    }

    @Override
    public void error(String msg, Throwable throwable) {
        Log.e(SekiroLogger.tag, msg, throwable);
    }
}
