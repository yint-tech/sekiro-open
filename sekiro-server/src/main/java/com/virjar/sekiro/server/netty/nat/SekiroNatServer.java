package com.virjar.sekiro.server.netty.nat;

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


/**
 * 手机端通信服务，通过这个和app连接，下发推送调用命令，可实现内网穿透，故命名为NatServer
 */
@Slf4j
@Component
public class SekiroNatServer implements InitializingBean {

    @Value("${natServerPort}")
    private Integer natServerPort;


    private boolean started = false;

    @Override
    public void afterPropertiesSet() {
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
                        socketChannel.pipeline().addLast(new SekiroNatMessageDecoder());
                        socketChannel.pipeline().addLast(new SekiroMessageEncoder());
                        socketChannel.pipeline().addLast(new ServerIdleCheckHandler());
                        socketChannel.pipeline().addLast(new NatServerChannelHandler());
                    }
                });


        if (natServerPort == null) {
            natServerPort = Constants.defaultNatServerPort;
        }

        log.info("start netty nat server,port:{}", natServerPort);
        serverBootstrap.bind(natServerPort);
    }

}
