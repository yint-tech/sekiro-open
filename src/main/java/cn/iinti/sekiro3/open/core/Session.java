package cn.iinti.sekiro3.open.core;

import cn.iinti.sekiro3.business.netty.channel.Channel;
import cn.iinti.sekiro3.business.netty.channel.ChannelFutureListener;
import cn.iinti.sekiro3.business.netty.util.AttributeKey;
import cn.iinti.sekiro3.open.Bootstrap;
import cn.iinti.sekiro3.open.framework.trace.EventRecordManager;
import cn.iinti.sekiro3.open.framework.trace.EventScene;
import cn.iinti.sekiro3.open.framework.trace.Recorder;
import cn.iinti.sekiro3.open.handlers.RelayHandler;
import cn.iinti.sekiro3.open.utils.IpAndPort;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

public class Session {
    private final String sessionId = UUID.randomUUID().toString();

    @Getter
    private final Recorder recorder = EventRecordManager.acquireRecorder(sessionId, Bootstrap.isLocalDebug, EventScene.USER_SESSION);

    @Getter
    private final Channel userRequestChannel;


    @Getter
    private Channel transUpStream;


    public static void newSession(Channel proxyUserRequestChannel) {
        new Session(proxyUserRequestChannel);
    }

    @Getter
    @Setter
    private IpAndPort proxyTarget;


    Session(Channel userRequestChannel) {
        this.userRequestChannel = userRequestChannel;
        recorder.recordEvent("new request from: " + userRequestChannel);
        attach(userRequestChannel);
        userRequestChannel.closeFuture().addListener(future -> {
            // 在用户请求完成关闭的时候，确保session被回收了
            recorder.recordEvent("user connection closed");
            if (transUpStream != null) {
                transUpStream.close();
            }
        });
    }

    private static final AttributeKey<Session> SESSION_ATTRIBUTE_KEY = AttributeKey.newInstance("SESSION_ATTRIBUTE_KEY");

    private void attach(Channel channel) {
        channel.attr(SESSION_ATTRIBUTE_KEY).set(this);
    }

    public void onUpstreamChannel(Channel channel) {
        transUpStream = channel;
        attach(channel);
    }

    public static Session get(Channel channel) {
        return channel.attr(SESSION_ATTRIBUTE_KEY).get();
    }


    public void replay() {
        transUpStream.closeFuture().addListener((ChannelFutureListener) future -> userRequestChannel.close());
        userRequestChannel.closeFuture().addListener((ChannelFutureListener) future -> transUpStream.close());

        transUpStream.pipeline().addLast(new RelayHandler(userRequestChannel));
        userRequestChannel.pipeline().addLast(new RelayHandler(transUpStream));
    }
}
