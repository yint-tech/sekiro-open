package cn.iinti.sekiro3.open.detector;


import cn.iinti.sekiro3.business.netty.buffer.ByteBuf;
import cn.iinti.sekiro3.business.netty.channel.ChannelHandlerContext;
import cn.iinti.sekiro3.business.netty.channel.ChannelInboundHandlerAdapter;
import cn.iinti.sekiro3.business.netty.handler.codec.ByteToMessageDecoder;
import cn.iinti.sekiro3.open.core.Session;
import cn.iinti.sekiro3.open.framework.trace.Recorder;
import cn.iinti.sekiro3.open.utils.NettyUtils;

import static cn.iinti.sekiro3.business.netty.handler.codec.ByteToMessageDecoder.MERGE_CUMULATOR;

/**
 * Switcher to distinguish different protocols
 */
public class ProtocolDetector extends ChannelInboundHandlerAdapter {

    private final ByteToMessageDecoder.Cumulator cumulator = MERGE_CUMULATOR;
    private final ProtocolMatcher[] matcherList;
    private int index;

    private ByteBuf buf;

    private final MatchMissHandler missHandler;


    public ProtocolDetector(MatchMissHandler missHandler, ProtocolMatcher... matchers) {
        this.missHandler = missHandler;
        if (matchers.length == 0) {
            throw new IllegalArgumentException("No matcher for ProtocolDetector");
        }
        this.matcherList = matchers;
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        Recorder recorder = Session.get(ctx.channel()).getRecorder();
        if (!(msg instanceof ByteBuf)) {
            recorder.recordEvent(() -> "unexpected message type for ProtocolDetector: " + msg.getClass());
            NettyUtils.closeOnFlush(ctx.channel());
            return;
        }
        ByteBuf in = (ByteBuf) msg;
        if (buf == null) {
            buf = in;
        } else {
            buf = cumulator.cumulate(ctx.alloc(), buf, in);
        }

        for (int i = index; i < matcherList.length; i++) {
            ProtocolMatcher matcher = matcherList[i];
            int match = matcher.match(buf.duplicate());
            if (match == ProtocolMatcher.MATCH) {
                recorder.recordEvent(() -> "matched by " + matcher.getClass().getName());
                matcher.handlePipeline(ctx.pipeline());
                ctx.pipeline().remove(this);
                ctx.fireChannelRead(buf);
                return;
            }

            if (match == ProtocolMatcher.PENDING) {
                index = i;
                recorder.recordEvent(() -> "match pending..");
                return;
            }
        }

        // all miss
        missHandler.onAllMatchMiss(ctx, buf);
        buf = null;
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        if (buf != null) {
            buf.release();
            buf = null;
        }
        Session.get(ctx.channel()).getRecorder().recordEvent(() -> "error", cause);
        NettyUtils.closeOnFlush(ctx.channel());
    }

    public interface MatchMissHandler {
        void onAllMatchMiss(ChannelHandlerContext ctx, ByteBuf buf);
    }

}
