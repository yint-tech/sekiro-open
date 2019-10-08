package com.virjar.sekiro.server.util;

import com.virjar.sekiro.api.CommonRes;

import java.io.IOException;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletResponse;

import external.com.alibaba.fastjson.JSON;
import external.com.alibaba.fastjson.JSONObject;

public class ReturnUtil {
    public static <T> CommonRes<T> failed(String message) {
        return failed(message, status_other);
    }

    public static CommonRes<Object> from(JSONObject jsonObject) {
        return from(jsonObject, null);
    }

    public static CommonRes<Object> from(JSONObject jsonObject, String clientId) {
        CommonRes<Object> ret = new CommonRes<>();
        ret.setStatus(jsonObject.getInteger("status"));
        ret.setMessage(jsonObject.getString("message"));
        ret.setData(jsonObject.get("data"));
        if (clientId != null) {
            ret.setClientId(clientId);
        }
        return ret;
    }

    public static <T> CommonRes<T> failed(Exception exception) {
        return failed(CommonUtil.translateSimpleExceptionMessage(exception), status_other);
    }

    public static <T> CommonRes<T> failed(String message, int status) {
        return new CommonRes<T>(status, message, null);
    }

    public static void writeRes(HttpServletResponse httpServletResponse, CommonRes<?> commonRes) {
        httpServletResponse.setContentType("application/json;charset=utf8");

        try {
            ServletOutputStream outputStream = httpServletResponse.getOutputStream();
            outputStream.write(JSON.toJSONBytes(commonRes));
            outputStream.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

    }


    public static final int status_other = -1;
    public static final int status_success = 0;
    public static final int status_timeout = 1;
}
