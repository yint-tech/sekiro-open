package com.virjar.sekiro.server.netty.http;

import com.google.common.base.Strings;
import com.google.common.collect.Maps;

import java.util.Hashtable;
import java.util.Map;


public class ContentType {
    private String mimeType;
    private String charset;

    private String mainType;
    private String subType;

    public String getMimeType() {
        return mimeType;
    }

    public String getCharset() {
        return charset;
    }

    public String getMainType() {
        return mainType;
    }

    public String getSubType() {
        return subType;
    }

    private final static Map<String, ContentType> cache = Maps.newHashMap();

    public static ContentType from(String contentTypeString) {
        if (Strings.isNullOrEmpty(contentTypeString)) {
            return null;
        }
        synchronized (cache) {
            ContentType contentType = cache.get(contentTypeString);
            if (contentType != null) {
                return contentType;
            }
        }
        // text/html;charset=utf8
        contentTypeString = contentTypeString.toLowerCase();
        int splitterIndex = contentTypeString.indexOf(";");
        ContentType ret = new ContentType();
        if (splitterIndex < 0) {
            ret.mimeType = contentTypeString.trim();
        } else {
            ret.mimeType = contentTypeString.substring(0, splitterIndex);
            ret.charset = contentTypeString.substring(splitterIndex + 1).trim().toLowerCase();

            if (ret.charset.startsWith("charset")) {
                String str = ret.charset.substring("charset".length()).trim();
                if (str.startsWith("=")) {
                    str = str.substring(1);
                }
                ret.charset = str;
            }
        }

        //parse mainType & subType

        splitterIndex = ret.mimeType.indexOf("/");
        if (splitterIndex > 0) {
            ret.mainType = ret.mimeType.substring(0, splitterIndex).trim();
            ret.subType = ret.mimeType.substring(splitterIndex + 1).trim();
        } else {
            ret.mainType = ret.mimeType;
        }
        synchronized (cache) {
            cache.put(contentTypeString, ret);
        }
        return ret;
    }

    static Hashtable<String, String> mContentTypes = new Hashtable<String, String>();

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
}
