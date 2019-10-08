package com.virjar.sekiro.server.netty;

import com.virjar.sekiro.Constants;
import com.virjar.sekiro.netty.protocol.SekiroMessageEncoder;
import com.virjar.sekiro.netty.protocol.SekiroNatMessageDecoder;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class SekiroNatServer implements InitializingBean {

    @Value("${natServerPort}")
    private Integer natServerPort;

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


    public void startUp() {
        ServerBootstrap serverBootstrap = new ServerBootstrap();
        NioEventLoopGroup serverBossGroup = new NioEventLoopGroup();
        NioEventLoopGroup serverWorkerGroup = new NioEventLoopGroup();

        serverBootstrap.group(serverBossGroup, serverWorkerGroup)
                .channel(NioServerSocketChannel.class)
                .childHandler(new ChannelInitializer<SocketChannel>() {

                    @Override
                    protected void initChannel(SocketChannel socketChannel) throws Exception {
                        socketChannel.pipeline().addLast(new SekiroNatMessageDecoder(
                                Constants.MAX_FRAME_LENGTH,
                                Constants.LENGTH_FIELD_OFFSET,
                                Constants.LENGTH_FIELD_LENGTH,
                                Constants.LENGTH_ADJUSTMENT,
                                Constants.INITIAL_BYTES_TO_STRIP));
                        socketChannel.pipeline().addLast(new SekiroMessageEncoder());
                        socketChannel.pipeline().addLast(new ServerIdleCheckHandler());
                        socketChannel.pipeline().addLast(new NatServerChannelHandler());
                    }
                });

        if (natHttpServerPort == null) {
            natHttpServerPort = Constants.defaultNatHttpServerPort;
        }

        if (natServerPort == null) {
            natServerPort = Constants.defaultNatServerPort;
        }

        log.info("start netty nat server,port:{}", natServerPort);
        serverBootstrap.bind(natServerPort);
    }

}
