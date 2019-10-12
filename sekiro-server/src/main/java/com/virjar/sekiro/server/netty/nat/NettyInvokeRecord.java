package com.virjar.sekiro.server.netty.nat;

import com.virjar.sekiro.netty.protocol.SekiroNatMessage;

import lombok.Getter;

public class NettyInvokeRecord {
    @Getter
    private String clientId;
    @Getter
    private long taskId;
    @Getter
    private String paramContent;

    private SekiroNatMessage invokeResult;

    public NettyInvokeRecord(String clientId, long taskId, String paramContent) {
        this.clientId = clientId;
        this.taskId = taskId;
        this.paramContent = paramContent;
    }

    private final Object lock = new Object();

    private boolean callbackCalled = false;

    public NettyInvokeRecord waitCallback(long timeOUt) {
        if (callbackCalled) {
            return this;
        }
        synchronized (lock) {
            if (callbackCalled) {
                return this;
            }

            try {
                lock.wait(timeOUt);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            return this;
        }
    }

    /**
     * 异步返回，需要主动调用这个接口通知数据到达，唤醒http request线程，进行数据回传
     *
     * @param responseJson 异步返回结果，可以是任何类型
     */
    public void notifyDataArrival(SekiroNatMessage responseJson) {
        this.invokeResult = responseJson;
        synchronized (lock) {
            callbackCalled = true;
            lock.notify();
        }
    }

    public SekiroNatMessage finalResult() {
        if (!callbackCalled) {
            return null;
        }
        return invokeResult;
    }

    boolean isCallbackCalled() {
        return callbackCalled;
    }

}
