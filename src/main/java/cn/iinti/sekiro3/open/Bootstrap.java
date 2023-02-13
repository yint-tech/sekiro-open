package cn.iinti.sekiro3.open;

import cn.iinti.sekiro3.business.netty.bootstrap.ServerBootstrap;
import cn.iinti.sekiro3.business.netty.buffer.Unpooled;
import cn.iinti.sekiro3.business.netty.channel.ChannelFutureListener;
import cn.iinti.sekiro3.business.netty.channel.ChannelInitializer;
import cn.iinti.sekiro3.business.netty.channel.nio.NioEventLoopGroup;
import cn.iinti.sekiro3.business.netty.channel.socket.SocketChannel;
import cn.iinti.sekiro3.business.netty.channel.socket.nio.NioServerSocketChannel;
import cn.iinti.sekiro3.business.netty.util.concurrent.DefaultThreadFactory;
import cn.iinti.sekiro3.open.core.Session;
import cn.iinti.sekiro3.open.detector.HttpMatcher;
import cn.iinti.sekiro3.open.detector.ProtocolDetector;
import cn.iinti.sekiro3.open.detector.ProtocolMatcher;
import cn.iinti.sekiro3.open.detector.SekiroMatcher;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.math.NumberUtils;

import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

@Slf4j
public class Bootstrap {
    private static boolean started = false;
    private static final Properties properties = new Properties();
    private static final byte[] UN_SUPPORT_PROTOCOL_MSG = "sekiro unknown protocol".getBytes();
    @Getter
    public static Integer listenPort;

    private static final String IntMessage = "welcome use sekiro framework, for more support please visit our website: https://iinti.cn/";
    public static boolean isLocalDebug;

    public static void main(String[] args) throws Exception {
        if (started) {
            return;
        }
        InputStream resourceAsStream = Bootstrap.class.getClassLoader().getResourceAsStream("config.properties");
        properties.load(resourceAsStream);

        isLocalDebug = BooleanUtils.toBoolean(properties.getProperty("sekiro.localDebug"));

        startUp();
        started = true;
    }

    private static void startUp() {
        ServerBootstrap serverBootstrap = new ServerBootstrap();

        NioEventLoopGroup serverBossGroup = new NioEventLoopGroup(Runtime.getRuntime().availableProcessors() * 2, new DefaultThreadFactory("sekiro-boss"));
        NioEventLoopGroup serverWorkerGroup = new NioEventLoopGroup(Runtime.getRuntime().availableProcessors() * 6, new DefaultThreadFactory("sekiro-worker"));


        serverBootstrap.group(serverBossGroup, serverWorkerGroup).channel(NioServerSocketChannel.class).childHandler(new ChannelInitializer<SocketChannel>() {
            @Override
            protected void initChannel(SocketChannel socketChannel) {
                Session.newSession(socketChannel);
                List<ProtocolMatcher> matchers = Arrays.asList(
                        // sekiro底层协议
                        new SekiroMatcher(), new HttpMatcher());

                ProtocolDetector protocolDetector = new ProtocolDetector((ctx, buf) -> {
                    Session.get(ctx.channel()).getRecorder().recordEvent("unsupported protocol");
                    buf.release();
                    ctx.channel().writeAndFlush(Unpooled.wrappedBuffer(UN_SUPPORT_PROTOCOL_MSG)).addListener(ChannelFutureListener.CLOSE);
                }, matchers.toArray(new ProtocolMatcher[]{}));
                socketChannel.pipeline().addLast(protocolDetector);
            }
        });

        listenPort = NumberUtils.toInt(properties.getProperty("sekiro.port", "5612"));
        log.info("start sekiro netty server,port:{}", listenPort);
        log.info(IntMessage);
        System.out.println(IntMessage);
        serverBootstrap.bind(listenPort).addListener(future -> {
            if (future.isSuccess()) {
                log.info("sekiro netty server start success");
            } else {
                log.info("sekiro netty server start failed");
            }
        });
    }

}
