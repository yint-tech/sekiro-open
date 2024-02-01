package cn.iinti.sekiro3.open.handlers;

import cn.iinti.sekiro3.business.api.util.Constants;
import cn.iinti.sekiro3.business.netty.channel.ChannelHandlerContext;
import cn.iinti.sekiro3.business.netty.handler.codec.http.websocketx.PingWebSocketFrame;
import cn.iinti.sekiro3.business.netty.handler.timeout.IdleStateEvent;
import cn.iinti.sekiro3.business.netty.handler.timeout.IdleStateHandler;
import cn.iinti.sekiro3.open.framework.trace.Recorder;

public class WsHeartbeatHandler extends IdleStateHandler {
    private final Recorder recorder;

    public WsHeartbeatHandler(Recorder recorder) {
        super(Constants.SERVER_READ_IDLE, Constants.SERVER_WRITE_IDLE, Constants.SERVER_READ_WRITE);
        this.recorder = recorder;
    }

    @Override
    protected void channelIdle(ChannelHandlerContext ctx, IdleStateEvent event) throws Exception {
        switch (event.state()) {
            case READER_IDLE:
                // 客户端需要在 SERVER_READ_IDLE - SERVER_READ_WRITE (默认 15s) 内回复
                // 没有回复则会触发该事件，关闭客户端连接
                recorder.recordEvent("Client READER_IDLE timeout, close channel");
                ctx.channel().close();
                break;
            case WRITER_IDLE:
                recorder.recordEvent("Server WRITE_IDLE timeout,send heartbeat to Client");
                ctx.channel().writeAndFlush(new PingWebSocketFrame());
            case ALL_IDLE:
                // 服务端读写空闲超时发送一个心跳包给客户端
                recorder.recordEvent("Server ALL_IDLE timeout, send heartbeat to Client");
                ctx.channel().writeAndFlush(new PingWebSocketFrame());
                break;
            default:
                // never call here
                recorder.recordEvent("未知事件");
                super.channelIdle(ctx, event);
        }
    }
}
