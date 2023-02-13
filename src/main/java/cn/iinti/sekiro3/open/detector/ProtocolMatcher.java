package cn.iinti.sekiro3.open.detector;


import cn.iinti.sekiro3.business.netty.buffer.ByteBuf;
import cn.iinti.sekiro3.business.netty.channel.ChannelPipeline;

/**
 * Matcher for protocol.
 */
public abstract class ProtocolMatcher {

    static int MATCH = 1;
    static int MISMATCH = -1;
    static int PENDING = 0;

    /**
     * If match the protocol.
     *
     * @return 1:match, -1:not match, 0:still can not judge now
     */
    protected abstract int match(ByteBuf buf);

    /**
     * Deal with the pipeline when matched
     */
    protected void handlePipeline(ChannelPipeline pipeline) {
    }
}
