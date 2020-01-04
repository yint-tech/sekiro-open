package com.virjar.sekiro.log;

import android.util.Log;

import org.slf4j.LoggerFactory;

public class SekiroLogger {
    public static String tag = "Sekiro";

    private static ILogger logger = null;

    static {
        genLogger();
    }

    public static void setLogger(ILogger logger) {
        if (logger == null) {
            throw new IllegalArgumentException("input logger can not be null");
        }
        SekiroLogger.logger = logger;
    }

    private static void genLogger() {
        try {
            Log.i(tag, "test sekiro log");
            logger = new AndroidLogger();
            return;
        } catch (Throwable throwable) {
            //ignore
        }

        try {
            LoggerFactory.getLogger(tag).info("test sekiro log");
            logger = new Slf4jLogger();
            return;
        } catch (Throwable throwable) {
            //ignore
        }
        logger = new SystemOutLogger();
    }

    public static void info(String msg) {
        logger.info(msg);
    }

    public static void info(String msg, Throwable throwable) {
        logger.info(msg, throwable);
    }

    public static void warn(String msg) {
        logger.warn(msg);
    }

    public static void warn(String msg, Throwable throwable) {
        logger.warn(msg, throwable);
    }

    public static void error(String msg) {
        logger.error(msg);
    }

    public static void error(String msg, Throwable throwable) {
        logger.error(msg, throwable);
    }
}
