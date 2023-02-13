package cn.iinti.sekiro3.open.detector;

import cn.iinti.sekiro3.business.api.core.compress.CompressorManager;
import cn.iinti.sekiro3.business.api.core.compress.NettyCompressHandler;
import cn.iinti.sekiro3.business.api.core.compress.NettyDecompressHandler;
import cn.iinti.sekiro3.business.api.protocol.PacketCommon;
import cn.iinti.sekiro3.business.api.protocol.SekiroPackerEncoder;
import cn.iinti.sekiro3.business.api.protocol.SekiroPacketDecoder;
import cn.iinti.sekiro3.business.netty.buffer.ByteBuf;
import cn.iinti.sekiro3.business.netty.channel.ChannelPipeline;
import cn.iinti.sekiro3.open.handlers.ServerHeartBeatHandler;
import cn.iinti.sekiro3.open.core.RouterSekiro;

public class SekiroMatcher extends ProtocolMatcher {
    private static final CompressorManager compressManager = new CompressorManager();
    private static final int ROUTER_MAGIC_HEADER_LENGTH = 8;

    @Override
    protected int match(ByteBuf buf) {
        if (buf.readableBytes() < ROUTER_MAGIC_HEADER_LENGTH) {
            // not enough data
            return ProtocolMatcher.PENDING;
        }
        long magic = buf.getLong(buf.readerIndex());
        return magic == PacketCommon.MAGIC ?
                ProtocolMatcher.MATCH : ProtocolMatcher.MISMATCH;
    }

    @Override
    protected void handlePipeline(ChannelPipeline pipeline) {
        pipeline
                .addLast(new SekiroPackerEncoder())
                .addLast(new NettyCompressHandler(compressManager, true))
                .addLast(new SekiroPacketDecoder())
                .addLast(new NettyDecompressHandler(compressManager, true))
                .addLast(new ServerHeartBeatHandler())
                .addLast(new RouterSekiro())
        ;
    }
}
