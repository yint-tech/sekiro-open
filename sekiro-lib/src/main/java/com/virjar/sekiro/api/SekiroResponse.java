package com.virjar.sekiro.api;

import com.virjar.sekiro.Constants;
import com.virjar.sekiro.api.compress.Compressor;
import com.virjar.sekiro.log.SekiroLogger;
import com.virjar.sekiro.netty.protocol.SekiroNatMessage;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.Hashtable;

import external.com.alibaba.fastjson.JSON;
import external.com.alibaba.fastjson.JSONObject;
import io.netty.channel.Channel;

public class SekiroResponse {
    private SekiroRequest request;
    private Channel channel;
    private boolean closed = false;
    private SekiroClient sekiroClient;

    public SekiroResponse(SekiroRequest request, Channel channel, SekiroClient sekiroClient) {
        this.request = request;
        this.channel = channel;
        this.sekiroClient = sekiroClient;
    }

    /**
     * @deprecated 为了兼容老版本的API
     */
    @Deprecated
    public void send(String contentType, byte[] bytes) {
        send(toContentTypeExtra(contentType), bytes);
    }

    /**
     * 返回数据到服务器
     *
     * @param extra 扩展数据，现在要求是一个json字符串。包括mime type，压缩算法，是否开启压缩等
     * @param bytes 返回内容
     */
    public void send(JSONObject extra, byte[] bytes) {
        if (closed) {
            SekiroLogger.warn("response send already!");
            return;
        }
        SekiroNatMessage sekiroNatMessage = new SekiroNatMessage();
        sekiroNatMessage.setSerialNumber(request.getSerialNo());
        sekiroNatMessage.setType(SekiroNatMessage.TYPE_INVOKE);
//        //TODO @weixuan
//        CompressUtil.Extra extra = CompressUtil.parseContextType(extra);
//        if (CompressUtil.canCompress(extra)) {
//            CompressUtil.CompressResponse compress = CompressUtil.compress(bytes);
//            sekiroNatMessage.setData(compress.getSrc());
//            //            todo delete
//            SekiroLogger.info("use compress before length " + bytes.length + " after length " + compress.getSrc().length);
//        } else {
//            sekiroNatMessage.setData(bytes);
//        }
        sekiroNatMessage.setData(bytes);
        sekiroNatMessage.setExtra(extra.toJSONString());
        channel.writeAndFlush(sekiroNatMessage);
        closed = true;

    }

    public <T> void send(CommonRes<T> commonRes) {
        String stringContent = JSON.toJSONString(commonRes);
        SekiroLogger.info("invoke response: " + stringContent);
        JSONObject extra = toContentTypeExtra("application/json; charset=utf-8");
        if (stringContent.length() < 10 * 1024) {
            send(extra, stringContent.getBytes(StandardCharsets.UTF_8));
        } else {
            byte[] bytes = handleCompress(extra, stringContent.getBytes(StandardCharsets.UTF_8));
            send(extra, bytes);
        }
    }

    private byte[] handleCompress(JSONObject extra, byte[] input) {
        Compressor compressor = sekiroClient.getCompressor();
        if (compressor == null) {
            return input;
        }
        String method = compressor.method();
        try {
            byte[] compressData = compressor.compress(input);
            extra.put(Constants.compressMethod, method);
            return compressData;
        } catch (Exception e) {
            SekiroLogger.warn("call compressor failed", e);
            return input;
        }
    }

//    public <T> void send(CommonRes<T> commonRes, boolean compress) {
//        String stringContent = JSON.toJSONString(commonRes);
//        CompressUtil.Extra extra = new CompressUtil.Extra();
//        extra.setContextType("application/json; charset=utf-8");
//        extra.setCompress(compress);
//        send(JSON.toJSONString(extra), stringContent);
//    }

    public void send(String contentType, String string) {
        send(toContentTypeExtra(contentType), string.getBytes(StandardCharsets.UTF_8));
        SekiroLogger.info("invoke response: " + string);
    }

    public void send(String string) {
        send(toContentTypeExtra("text/html; charset=utf-8"), string.getBytes(StandardCharsets.UTF_8));
    }

    public void sendFile(File file) throws IOException {
        FileInputStream fin = new FileInputStream(file);
        sendStream(getContentType(file.getAbsolutePath()), new BufferedInputStream(fin, 64000));
    }

    private static final int DEFAULT_BUFFER_SIZE = 1024 * 4;
    private static final int EOF = -1;

    public JSONObject toContentTypeExtra(String contentType) {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("contentType", contentType);
        return jsonObject;
    }

    public void sendStream(JSONObject extra, InputStream inputStream) throws IOException {
        try (final ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            // copy(inputStream, output);
            long count = 0;
            int n;
            byte[] buffer = new byte[DEFAULT_BUFFER_SIZE];
            while (EOF != (n = inputStream.read(buffer))) {
                output.write(buffer, 0, n);
                count += n;
            }
            send(extra, output.toByteArray());
        }
    }

    /**
     * @param contentType contentType
     * @param inputStream inputStream
     * @throws IOException maybe throw exception
     * @deprecated use {@link SekiroResponse#sendStream(external.com.alibaba.fastjson.JSONObject, java.io.InputStream)}
     */
    @Deprecated
    public void sendStream(String contentType, InputStream inputStream) throws IOException {
        sendStream(toContentTypeExtra(contentType), inputStream);
    }

    private static Hashtable<String, String> mContentTypes = new Hashtable<String, String>();

    static {
        mContentTypes.put("js", "application/javascript");
        mContentTypes.put("json", "application/json");
        mContentTypes.put("png", "image/png");
        mContentTypes.put("jpg", "image/jpeg");
        mContentTypes.put("html", "text/html");
        mContentTypes.put("css", "text/css");
        mContentTypes.put("mp4", "video/mp4");
        mContentTypes.put("mov", "video/quicktime");
        mContentTypes.put("wmv", "video/x-ms-wmv");
    }

    public static String getContentType(String path) {
        String type = tryGetContentType(path);
        if (type != null)
            return type;
        return "text/plain";
    }

    public static String tryGetContentType(String path) {
        int index = path.lastIndexOf(".");
        if (index != -1) {
            String e = path.substring(index + 1);
            String ct = mContentTypes.get(e);
            if (ct != null)
                return ct;
        }
        return null;
    }

    public static String getStackTrack(Throwable throwable) {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        PrintWriter printWriter = new PrintWriter(new OutputStreamWriter(byteArrayOutputStream));
        throwable.printStackTrace(printWriter);
        printWriter.close();
        try {
            return byteArrayOutputStream.toString(StandardCharsets.UTF_8.name());
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
    }


    public void failed(int errorCode, Throwable throwable) {
        failed(errorCode, getStackTrack(throwable));
    }

    public void failed(int errorCode, String message) {
        send(CommonRes.failed(errorCode, message));
    }

    public void failed(String message) {
        send(CommonRes.failed(message));
    }

    public <T> void success(T data) {
        send(CommonRes.success(data));
    }

//    public <T> void success(T data, boolean compress) {
//        send(CommonRes.success(data), compress);
//    }


    public SekiroClient getSekiroClient() {
        return sekiroClient;
    }
}
