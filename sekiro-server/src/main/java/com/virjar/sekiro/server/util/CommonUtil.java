package com.virjar.sekiro.server.util;

import com.google.common.base.Joiner;
import com.google.common.collect.Lists;

import org.apache.commons.lang3.StringUtils;

import java.io.ByteArrayOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.URLEncoder;
import java.util.List;
import java.util.Map;

public class CommonUtil {
    public static String getStackTrack(Throwable throwable) {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        PrintWriter printWriter = new PrintWriter(new OutputStreamWriter(byteArrayOutputStream));
        throwable.printStackTrace(printWriter);
        return byteArrayOutputStream.toString();
    }

    public static String translateSimpleExceptionMessage(Exception exception) {
        String message = exception.getMessage();
        if (StringUtils.isBlank(message)) {
            message = exception.getClass().getName();
        }
        return message;
    }


    private static final Joiner joiner = Joiner.on('&').skipNulls();

    public static String joinParam(Map<String, String[]> params) {
        if (params == null || params.isEmpty()) {
            return null;
        }

        List<String> segment = Lists.newLinkedList();
        for (Map.Entry<String, String[]> entry : params.entrySet()) {
            String name = entry.getKey();
            String[] inputValue = entry.getValue();
            // List<String> encodeItem = Lists.newArrayListWithExpectedSize(inputValue.length);
            for (String value : inputValue) {
                segment.add(URLEncoder.encode(name) + "=" + URLEncoder.encode(value));
            }
        }
        return joiner.join(segment);
    }

    public static String joinListParam(Map<String, List<String>> params) {
        if (params == null || params.isEmpty()) {
            return null;
        }

        List<String> segment = Lists.newLinkedList();
        for (Map.Entry<String, List<String>> entry : params.entrySet()) {
            String name = entry.getKey();
            List<String> inputValue = entry.getValue();
            // List<String> encodeItem = Lists.newArrayListWithExpectedSize(inputValue.length);
            for (String value : inputValue) {
                segment.add(URLEncoder.encode(name) + "=" + URLEncoder.encode(value));
            }
        }
        return joiner.join(segment);
    }
}
