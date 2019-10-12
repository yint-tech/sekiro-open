package com.virjar.sekiro.server.netty.http;

import com.virjar.sekiro.Constants;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpResponseEncoder;
import lombok.extern.slf4j.Slf4j;

/**
 * 异步http服务端，主要解决springBoot内置tomcat使用BIO的问题，在SKIRO环境下，如果使用BIO进行调用转发，那么存在吞吐并发上线
 */
@Slf4j
@Component
public class SekiroHttpServer implements InitializingBean {

    @Value("${natHttpServerPort}")
    private Integer natHttpServerPort;

    private boolean started = false;


    @Override
    public void afterPropertiesSet() throws Exception {
        if (started) {
            return;
        }
        startUp();
        started = true;
    }

    private void startUp() {

        ServerBootstrap serverBootstrap = new ServerBootstrap();
        NioEventLoopGroup serverBossGroup = new NioEventLoopGroup();
        NioEventLoopGroup serverWorkerGroup = new NioEventLoopGroup();

        serverBootstrap.group(serverBossGroup, serverWorkerGroup)
                .channel(NioServerSocketChannel.class)
                .childHandler(new ChannelInitializer<SocketChannel>() {

                    @Override
                    protected void initChannel(SocketChannel socketChannel) throws Exception {
                        socketChannel.pipeline().addLast(new RBHttpRequestDecoder())
                                .addLast(new HttpResponseEncoder())
                                //最大32M的报文
                                .addLast(new HttpObjectAggregator(1 << 25))
                                .addLast(new HttpRequestDispatcher());
                    }
                });


        if (natHttpServerPort == null) {
            natHttpServerPort = Constants.defaultNatHttpServerPort;
        }
        log.info("start netty http server,port:{}", natHttpServerPort);
        serverBootstrap.bind(natHttpServerPort)
                .addListener(new ChannelFutureListener() {

                    @Override
                    public void operationComplete(ChannelFuture channelFuture) {
                        if (!channelFuture.isSuccess()) {
                            log.warn("start netty http server failed", channelFuture.cause());
                        }
                    }
                });


    }
}
