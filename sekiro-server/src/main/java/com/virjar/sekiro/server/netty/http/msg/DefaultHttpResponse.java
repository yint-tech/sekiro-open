package com.virjar.sekiro.server.netty.http.msg;


import com.google.common.base.Charsets;
import com.virjar.sekiro.server.netty.http.HeaderNameValue;

import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;

public class DefaultHttpResponse extends io.netty.handler.codec.http.DefaultHttpResponse {
    public final byte[] contentByteData;

    public DefaultHttpResponse(String content) {
        super(HttpVersion.HTTP_1_1, HttpResponseStatus.BAD_REQUEST);

        contentByteData = content.getBytes(Charsets.UTF_8);
        headers().set(HeaderNameValue.CONTENT_TYPE, "text/html;charset=utf8;");
        headers().set(HeaderNameValue.CONTENT_LENGTH, contentByteData.length);
    }

    private static final String badRequestContent = "<!DOCTYPE html>\n" +
            "<html lang=\"en\">\n" +
            "<head>\n" +
            "    <meta charset=\"UTF-8\">\n" +
            "    <title>BadRequest</title>\n" +
            "</head>\n" +
            "<body>\n" +
            "<h1>\n" +
            "    the Sekiro http server can not recognize you request,please contact you website admin\n" +
            "</h1>\n" +
            "<h2>\n" +
            "    Sekiro 服务器不识别你的请求，请联系管理员或者Sekiro作者\n" +
            "</h2>\n" +
            "</body>\n" +
            "</html>";

    private static final String notFoundContent = "<!DOCTYPE html>\n" +
            "<html lang=\"en\">\n" +
            "<head>\n" +
            "    <meta charset=\"UTF-8\">\n" +
            "    <title>Not Found</title>\n" +
            "</head>\n" +
            "<body>\n" +
            "<h1>\n" +
            "    no handler found\n" +
            "</h1>\n" +
            "</body>\n" +
            "</html>";


    public static DefaultHttpResponse badRequest = new DefaultHttpResponse(badRequestContent);


    public static DefaultHttpResponse notFound = new DefaultHttpResponse(notFoundContent);
}
