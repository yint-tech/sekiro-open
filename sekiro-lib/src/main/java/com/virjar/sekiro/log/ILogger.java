package com.virjar.sekiro.log;

public interface ILogger {
    void info(String msg);

    void info(String msg, Throwable throwable);

    void warn(String msg);

    void warn(String msg, Throwable throwable);

    void error(String msg);

    void error(String msg, Throwable throwable);
}
