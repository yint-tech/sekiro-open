package cn.iinti.sekiro3.open.detector;

import cn.iinti.sekiro3.business.netty.buffer.ByteBuf;
import cn.iinti.sekiro3.business.netty.channel.ChannelPipeline;
import cn.iinti.sekiro3.business.netty.handler.codec.http.HttpServerCodec;
import cn.iinti.sekiro3.open.handlers.HttpFirstPacketHandler;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static java.nio.charset.StandardCharsets.US_ASCII;

/**
 * Matcher for plain http request.
 */
public class HttpMatcher extends ProtocolMatcher {

    private static final Set<String> methods = new HashSet<>(Arrays.asList("GET", "POST", "PUT", "HEAD", "OPTIONS", "PATCH", "DELETE",
            "TRACE"));

    @Override
    public int match(ByteBuf buf) {
        if (buf.readableBytes() < 8) {
            return PENDING;
        }


        int index = buf.indexOf(0, 8, (byte) ' ');
        if (index < 0) {
            return MISMATCH;
        }

        int firstURIIndex = index + 1;
        if (buf.readableBytes() < firstURIIndex + 1) {
            return PENDING;
        }

        String method = buf.toString(0, index, US_ASCII);
        char firstURI = (char) (buf.getByte(firstURIIndex + buf.readerIndex()) & 0xff);
        if (!methods.contains(method) || firstURI != '/') {
            return MISMATCH;
        }

        return MATCH;
    }

    @Override
    public void handlePipeline(ChannelPipeline pipeline) {
        pipeline.addLast(
                new HttpServerCodec(),
                new HttpFirstPacketHandler()
        );
    }
}
