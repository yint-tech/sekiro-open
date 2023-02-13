package cn.iinti.sekiro3.open.handlers;


import cn.iinti.sekiro3.business.netty.buffer.Unpooled;
import cn.iinti.sekiro3.business.netty.channel.Channel;
import cn.iinti.sekiro3.business.netty.channel.ChannelHandlerContext;
import cn.iinti.sekiro3.business.netty.channel.ChannelInboundHandlerAdapter;
import cn.iinti.sekiro3.business.netty.util.ReferenceCountUtil;
import cn.iinti.sekiro3.open.core.Session;
import cn.iinti.sekiro3.open.framework.trace.Recorder;

public final class RelayHandler extends ChannelInboundHandlerAdapter {

    private final Channel nextChannel;
    private final String TAG;

    private final Recorder recorder;

    public RelayHandler(Channel relayChannel) {
        this.nextChannel = relayChannel;
        Session session = Session.get(nextChannel);
        recorder = session.getRecorder();
        if (nextChannel.equals(session.getUserRequestChannel())) {
            TAG = "replay-outbound";
        } else if (nextChannel.equals(session.getTransUpStream())) {
            TAG = "replay-inbound";
        } else {
            TAG = "replay-unknown";
        }
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        ctx.writeAndFlush(Unpooled.EMPTY_BUFFER);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        recorder.recordEvent("channel channelInactive");
        super.channelInactive(ctx);
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        recorder.recordEvent(TAG + ": receive message: " + msg);
        if (nextChannel.isActive()) {
            nextChannel.writeAndFlush(msg);
        } else {
            ReferenceCountUtil.release(msg);
        }
    }


    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        recorder.recordEvent(() -> TAG + ": replay exception", cause);
        if (nextChannel.isActive()) {
            nextChannel.write(Unpooled.EMPTY_BUFFER)
                    .addListener(future -> nextChannel.close());
        } else {
            ctx.close();
        }

    }
}

