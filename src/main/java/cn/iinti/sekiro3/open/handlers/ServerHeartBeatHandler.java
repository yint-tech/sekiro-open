package cn.iinti.sekiro3.open.handlers;

import cn.iinti.sekiro3.business.api.protocol.SekiroPacketType;
import cn.iinti.sekiro3.business.api.util.Constants;
import cn.iinti.sekiro3.business.netty.channel.ChannelHandlerContext;
import cn.iinti.sekiro3.business.netty.handler.timeout.IdleStateEvent;
import cn.iinti.sekiro3.business.netty.handler.timeout.IdleStateHandler;
import cn.iinti.sekiro3.open.core.Session;
import cn.iinti.sekiro3.open.framework.trace.Recorder;


public class ServerHeartBeatHandler extends IdleStateHandler {


    public ServerHeartBeatHandler() {
        super(Constants.SERVER_READ_IDLE, Constants.SERVER_WRITE_IDLE, Constants.SERVER_READ_WRITE);
    }

    @Override
    protected void channelIdle(ChannelHandlerContext ctx, IdleStateEvent event) throws Exception {
        Recorder recorder = Session.get(ctx.channel()).getRecorder();
        switch (event.state()) {
            case READER_IDLE:
                // 客户端需要在 SERVER_READ_IDLE - SERVER_READ_WRITE (默认 15s) 内回复
                // 没有回复则会触发该事件，关闭客户端连接
                recorder.recordEvent("Client READER_IDLE timeout, close channel");
                ctx.channel().close();
                break;
            case WRITER_IDLE:
                recorder.recordEvent("Server WRITE_IDLE timeout,send heartbeat to Client");
                ctx.channel().writeAndFlush(SekiroPacketType.TYPE_HEARTBEAT.createPacket());
            case ALL_IDLE:
                // 服务端读写空闲超时发送一个心跳包给客户端
                recorder.recordEvent("Server ALL_IDLE timeout, send heartbeat to Client");
                ctx.channel().writeAndFlush(SekiroPacketType.TYPE_HEARTBEAT.createPacket());
                break;
            default:
                // never call here
                recorder.recordEvent("未知事件");
        }
        super.channelIdle(ctx, event);

    }

}
