package cn.iinti.sekiro3.open.utils;

import cn.iinti.sekiro3.business.api.core.SekiroFastJson;
import cn.iinti.sekiro3.business.api.fastjson.JSON;
import cn.iinti.sekiro3.business.api.fastjson.JSONObject;
import cn.iinti.sekiro3.business.api.protocol.SekiroPacket;
import cn.iinti.sekiro3.business.api.util.Constants;
import cn.iinti.sekiro3.business.netty.buffer.ByteBuf;
import cn.iinti.sekiro3.business.netty.buffer.Unpooled;
import cn.iinti.sekiro3.business.netty.channel.Channel;
import cn.iinti.sekiro3.business.netty.channel.ChannelFuture;
import cn.iinti.sekiro3.business.netty.channel.ChannelFutureListener;
import cn.iinti.sekiro3.business.netty.channel.ChannelHandlerContext;
import cn.iinti.sekiro3.business.netty.handler.codec.http.*;
import cn.iinti.sekiro3.open.core.CommonRes;
import cn.iinti.sekiro3.open.framework.trace.Recorder;
import cn.iinti.sekiro3.open.core.client.InvokeRecord;
import org.apache.commons.lang3.StringUtils;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static cn.iinti.sekiro3.business.netty.handler.codec.http.HttpHeaders.Names.CONTENT_LENGTH;

public class NettyUtils {

    /**
     * Closes the specified channel after all queued write requests are flushed.
     */
    public static void closeOnFlush(Channel channel) {
        if (channel.isActive()) {
            channel.writeAndFlush(Unpooled.EMPTY_BUFFER).addListener(ChannelFutureListener.CLOSE);
        }
    }

    public static void httpResponseText(Channel httpChannel, HttpResponseStatus status, String body) {
        DefaultFullHttpResponse response;
        if (body != null) {
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            ByteBuf content = Unpooled.copiedBuffer(bytes);
            response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, status, content);
            response.headers().set(HttpHeaders.Names.CONTENT_LENGTH, bytes.length);
            response.headers().set(HttpHeaders.Names.CONTENT_TYPE, "text/html; charset=utf-8");
        } else {
            response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, status);
        }
        HttpUtil.setKeepAlive(response, false);
        httpChannel.writeAndFlush(response)
                .addListener(ChannelFutureListener.CLOSE);
    }

    public static String getParam(Map<String, List<String>> parameters, String key) {
        List<String> strings = parameters.get(key);
        if (strings == null) {
            return null;
        }
        if (strings.isEmpty()) {
            return null;
        }
        return strings.get(0);
    }

    public static void sendHttpResponse(ChannelHandlerContext ctx, FullHttpRequest req, DefaultFullHttpResponse res) {
        res.headers().set(CONTENT_LENGTH, res.content().readableBytes());
        if (res.getStatus().code() != 200) {
            ByteBuf buf = Unpooled.copiedBuffer(res.getStatus().toString(), StandardCharsets.UTF_8);
            res.content().writeBytes(buf);
            buf.release();
        }

        ChannelFuture f = ctx.channel().writeAndFlush(res);
        if (!HttpHeaders.isKeepAlive(req) || res.getStatus().code() != 200) {
            f.addListener(ChannelFutureListener.CLOSE);
        }
    }

    public static DefaultFullHttpResponse buildJSONHttpResponse(String jsonObject) {
        DefaultFullHttpResponse httpResponse = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK,
                Unpooled.wrappedBuffer(jsonObject.getBytes(StandardCharsets.UTF_8)));
        httpResponse.headers().set("Content-Type", "application/json;charset=utf8;");
        httpResponse.headers().set(CONTENT_LENGTH, httpResponse.content().readableBytes());
        return httpResponse;
    }

    public static DefaultFullHttpResponse buildJSONHttpResponse(CommonRes<?> commonRes) {
        return buildJSONHttpResponse(JSON.toJSONString(commonRes));
    }

    public static HttpObject buildHttpResponse(SekiroPacket sekiroPacket, InvokeRecord invokeRecord) {
        Recorder recorder = invokeRecord.getRecorder();
        String contentType = sekiroPacket.getHeader(Constants.PAYLOAD_CONTENT_TYPE.PAYLOAD_CONTENT_TYPE);
        if (Constants.PAYLOAD_CONTENT_TYPE.CONTENT_TYPE_SEKIRO_FAST_JSON.equals(contentType)) {
            // 内部快速反序列化，可以避免服务器进行json解码，减少CPU计算,2021年后的代码，应该都会走这个分支
            SekiroFastJson.FastJson fastJson = SekiroFastJson.quickJsonDecode(sekiroPacket.getData(), invokeRecord.getNettyClient().getClientId());
            recorder.recordEvent(() -> "http_invoke_result_" + (fastJson.getStatus() == 0) + " : " + StringUtils.left(fastJson.getFinalJson(), 200));

            sekiroPacket.getHeaders().put(Constants.REVERSED_WORDS.RESPONSE_STATUS, String.valueOf(fastJson.getStatus()));
            return buildJSONHttpResponse(fastJson.getFinalJson());
        }

        String isSegment = sekiroPacket.getHeader(Constants.PAYLOAD_CONTENT_TYPE.SEGMENT_STREAM_FLAG);
        boolean onceCall = !"true".equalsIgnoreCase(isSegment);
        if (!onceCall) {
            invokeRecord.isSegmentResponse = true;
        }

        if (onceCall && !invokeRecord.isSegmentResponse) {
            // sekiroV1和V2早期有部分代码，会走到这个分支，他是兼容逻辑
            // 在吞吐过大的场景下，这个分支涉及到json的解析，可能会非常占用资源
            byte[] data = sekiroPacket.getData();
            ContentType from = ContentType.from(contentType);
            if (from.getMainType().equals("application") && from.getSubType().equals("json")) {
                Charset charset = StandardCharsets.UTF_8;
                if (StringUtils.isNotBlank(from.getCharset())) {
                    try {
                        charset = Charset.forName(from.getCharset());
                    } catch (Exception e) {
                        recorder.recordEvent(() -> "no charset: " + from.getCharset(), e);
                    }
                }
                JSONObject jsonObject = JSONObject.parseObject(new String(data, charset));
                int status = jsonObject.getIntValue("status");
                sekiroPacket.getHeaders().put(Constants.REVERSED_WORDS.RESPONSE_STATUS, String.valueOf(status));

                jsonObject.put("clientId", invokeRecord.getNettyClient().getClientId());
                data = jsonObject.toJSONString().getBytes(charset);
            }

            DefaultFullHttpResponse httpResponse = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK,
                    Unpooled.wrappedBuffer(data));
            httpResponse.headers().set("Content-Type", contentType);
            httpResponse.headers().set(CONTENT_LENGTH, httpResponse.content().readableBytes());

            return httpResponse;
        }

        // 分段传输
        if (!invokeRecord.hasResponseSegmentHttpHeader) {
            invokeRecord.hasResponseSegmentHttpHeader = true;
            DefaultHttpResponse resp = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
            //resp.setChunked(true);
            resp.headers().set("Content-Type", contentType);
            resp.headers().set(HttpHeaders.Names.TRANSFER_ENCODING, HttpHeaders.Values.CHUNKED);
            return resp;
        }


        if (onceCall || sekiroPacket.getData().length == 0) {
            return LastHttpContent.EMPTY_LAST_CONTENT;
        }
        //延长超时时间，这样分段传输的时间可以拉长，避免大报文无法在规定时间内返回完成
        invokeRecord.mustResponseTimestamp = System.currentTimeMillis() + invokeRecord.timeout;
        return new DefaultHttpContent(Unpooled.wrappedBuffer(sekiroPacket.getData()));
    }

}
