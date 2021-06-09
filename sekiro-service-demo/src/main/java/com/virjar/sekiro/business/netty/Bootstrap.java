package com.virjar.sekiro.business.netty;

import com.virjar.sekiro.business.netty.bootstrap.ServerBootstrap;
import com.virjar.sekiro.business.netty.channel.ChannelInitializer;
import com.virjar.sekiro.business.netty.channel.nio.NioEventLoopGroup;
import com.virjar.sekiro.business.netty.channel.socket.SocketChannel;
import com.virjar.sekiro.business.netty.channel.socket.nio.NioServerSocketChannel;
import com.virjar.sekiro.business.netty.routers.Router;
import com.virjar.sekiro.business.netty.util.concurrent.DefaultThreadFactory;

import org.apache.commons.lang3.math.NumberUtils;

import java.io.InputStream;
import java.util.Properties;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class Bootstrap {
    private static boolean started = false;
    private static Properties properties = new Properties();

    @Getter
    public static Integer listenPort;

    public static void main(String[] args) throws Exception {
        if (started) {
            return;
        }
        InputStream resourceAsStream = Bootstrap.class.getClassLoader().getResourceAsStream("config.properties");
        properties.load(resourceAsStream);

        startUp();
        started = true;
    }

    private static void startUp() {
        ServerBootstrap serverBootstrap = new ServerBootstrap();

        NioEventLoopGroup serverBossGroup = new NioEventLoopGroup(
                Runtime.getRuntime().availableProcessors() * 2,
                new DefaultThreadFactory("sekiro-boss")
        );
        NioEventLoopGroup serverWorkerGroup = new NioEventLoopGroup(
                Runtime.getRuntime().availableProcessors() * 6,
                new DefaultThreadFactory("sekiro-worker")
        );


        serverBootstrap.group(serverBossGroup, serverWorkerGroup)
                .channel(NioServerSocketChannel.class)
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel socketChannel) {
                        socketChannel.pipeline().addLast(new Router());
                    }
                });

        listenPort = NumberUtils.toInt(properties.getProperty("sekiro.port", "5620"));
        log.info("start sekiro netty server,port:{}", listenPort);
        serverBootstrap.bind(listenPort).addListener(future -> {
            if (future.isSuccess()) {
                log.info("sekiro netty server start success");
            } else {
                log.info("sekiro netty server start failed");
            }
        });
    }
}
