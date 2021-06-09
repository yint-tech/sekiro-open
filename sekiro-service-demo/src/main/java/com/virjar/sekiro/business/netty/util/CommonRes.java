package com.virjar.sekiro.business.netty.util;


import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class CommonRes<T> {
    private int status = statusOK;
    private String message;
    private T data;
    private String clientId;


    public static final int statusOK = 0;
    public static final int statusError = -1;
    public static final int statusBadRequest = -2;
    public static final int statusSekiroDestroy = -3;
    public static final int statusNeedLogin = -4;
    public static final int statusLoginExpire = -5;
    public static final int statusDeny = -6;

    public static <T> CommonRes<T> success(T t) {
        CommonRes<T> ret = new CommonRes<>();
        ret.status = statusOK;
        ret.message = null;
        ret.data = t;
        return ret;

    }

    public CommonRes(int status, String message, T data) {
        this.status = status;
        this.message = message;
        this.data = data;
    }

    public CommonRes() {
    }

    public CommonRes setClientId(String clientId) {
        this.clientId = clientId;
        return this;
    }

    public static <T> CommonRes<T> failed(String message) {
        return failed(statusError, message);
    }

    public static <T> CommonRes<T> failed(int status, String message) {
        CommonRes<T> ret = new CommonRes<>();
        ret.status = status;
        ret.message = message;
//        if (status != statusOK && TextUtil.isEmpty(message)) {
//            SekiroLogger.warn("empty error message", new Throwable());
//        }
        return ret;
    }

    public boolean isOk() {
        return status == statusOK;
    }

    public <TN> CommonRes<TN> errorTransfer() {
        return CommonRes.failed(status, message);
    }
}
