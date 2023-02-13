package cn.iinti.sekiro3.open.handlers;

import cn.iinti.sekiro3.business.netty.bootstrap.Bootstrap;
import cn.iinti.sekiro3.business.netty.channel.ChannelFutureListener;
import cn.iinti.sekiro3.business.netty.channel.ChannelHandlerContext;
import cn.iinti.sekiro3.business.netty.channel.ChannelInboundHandlerAdapter;
import cn.iinti.sekiro3.business.netty.channel.ChannelInitializer;
import cn.iinti.sekiro3.business.netty.channel.nio.NioEventLoopGroup;
import cn.iinti.sekiro3.business.netty.channel.socket.SocketChannel;
import cn.iinti.sekiro3.business.netty.channel.socket.nio.NioSocketChannel;
import cn.iinti.sekiro3.business.netty.handler.codec.http.*;
import cn.iinti.sekiro3.business.netty.util.ReferenceCountUtil;
import cn.iinti.sekiro3.business.netty.util.concurrent.DefaultThreadFactory;
import cn.iinti.sekiro3.business.netty.util.internal.ThrowableUtil;
import cn.iinti.sekiro3.open.core.Session;
import cn.iinti.sekiro3.open.framework.trace.Recorder;
import cn.iinti.sekiro3.open.core.ServiceHttp;
import cn.iinti.sekiro3.open.utils.IpAndPort;
import cn.iinti.sekiro3.open.utils.NettyUtils;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Queue;

public class HttpFirstPacketHandler extends ChannelInboundHandlerAdapter {
    private Queue<HttpObject> httpObjects;

    private static final NioEventLoopGroup proxyWorkerGroup =
            new NioEventLoopGroup(Runtime.getRuntime().availableProcessors() * 3, new DefaultThreadFactory("proxy-worker"));

    private ChannelHandlerContext ctx;
    private Session session;

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        this.ctx = ctx;
        this.session = Session.get(ctx.channel());
        Recorder recorder = session.getRecorder();
        HttpRequest httpRequest;
        if (msg instanceof HttpRequest) {
            if (httpObjects != null) {
                for (HttpObject httpObject : httpObjects) {
                    ReferenceCountUtil.release(httpObject);
                }
            }
            httpObjects = new ArrayDeque<>();
            httpRequest = (HttpRequest) msg;
            recorder.recordEvent(() -> "http request");
        } else if (msg instanceof HttpObject) {
            httpObjects.add((HttpObject) msg);
            return;
        } else {
            recorder.recordEvent(() -> "not handle http message:" + msg);
            ReferenceCountUtil.release(msg);
            ctx.close();
            return;
        }

        httpObjects.add(httpRequest);
        String uriPath = resolveURIPath(httpRequest);
        if (isSekiroWebsocket(uriPath, httpRequest)
                || isSekiroHttpRequest(uriPath)) {
            recorder.recordEvent("this is sekiro  request, handle netty http turning");
            ctx.pipeline().addLast(
                    new HttpObjectAggregator(1 << 25),
                    // sekiroV3脱离了nginx，此时压缩需要由netty层来实现，避免报文过大
                    new HttpContentCompressor(),
                    new ServiceHttp()

            );
            ctx.pipeline().remove(HttpFirstPacketHandler.class);
            return;
        }

        if (session.getProxyTarget() == null) {
            httpRequest.headers().add("sekiro-demo", "true");
            session.setProxyTarget(new IpAndPort("sekiro.iinti.cn", 5612));
        }

        connectUpstream();
    }

    private final String[] sekiroHttpRequestPrefix = new String[]{
            "/business/invoke", "/business/groupList", "/business/clientQueue",
            "/business-demo/invoke", "/business-demo/groupList", "/business-demo/clientQueue",
    };

    //////// 下面两种场景，是需要在netty端聚合为http请求，并且在http端处理 ////
    private boolean isSekiroHttpRequest(String uriPath) {
        return Arrays.stream(sekiroHttpRequestPrefix).anyMatch(s -> s.equalsIgnoreCase(uriPath));
    }

    private final String[] sekiroWebSocketRequestPrefix = new String[]{
            "/business/register", "/register", "/business-demo/register",
    };

    private boolean isSekiroWebsocket(String uriPath, HttpRequest httpRequest) {
        if (!"websocket".equals(httpRequest.headers().get("Upgrade"))) {
            return false;
        }
        return Arrays.stream(sekiroWebSocketRequestPrefix).anyMatch(s -> s.equalsIgnoreCase(uriPath));
    }

    private void connectUpstream() {
        Bootstrap bootstrap = new Bootstrap().group(proxyWorkerGroup)
                .channelFactory(NioSocketChannel::new)
                .handler(new ChannelInitializer<SocketChannel>() {

                    @Override
                    protected void initChannel(SocketChannel ch) {
                        ch.pipeline().addLast(new HttpClientCodec());
                    }
                });
        IpAndPort proxyTarget = session.getProxyTarget();
        bootstrap.connect(proxyTarget.getIp(), proxyTarget.getPort())
                .addListener((ChannelFutureListener) future -> {
                    if (!future.isSuccess()) {
                        session.getRecorder().recordEvent(() -> "connect upstream failed", future.cause());
                        NettyUtils.httpResponseText(ctx.channel(), HttpResponseStatus.BAD_GATEWAY, ThrowableUtil.stackTraceToString(future.cause()));
                        return;
                    }

                    if (!ctx.channel().isActive()) {
                        future.channel().close();
                        return;
                    }

                    session.onUpstreamChannel(future.channel());
                    session.replay();

                    ctx.pipeline().remove(HttpFirstPacketHandler.class);
                });

    }

    private String resolveURIPath(HttpRequest httpRequest) {
        String uri = httpRequest.getUri();
        if (uri.startsWith("http://") || uri.startsWith("https://")) {
            // http代理模式，起url路径是绝对路径，此时我们需要裁剪他，否则可以被服务器认定为代理请求（具体看服务器的具体实现，但是按照标准来说全路径是http代理）
            // http://www.baidu.com/xxx?a=b
            int splitStartIndex = uri.indexOf("//");
            int index = uri.indexOf('/', splitStartIndex + 2);
            if (index > 0) {
                uri = uri.substring(index);
            } else {
                uri = "/";
            }
            httpRequest.setUri(uri);
        }
        int paramIndex = uri.indexOf('?');
        if (paramIndex > 0) {
            uri = uri.substring(0, paramIndex);
        }
        uri = uri.trim();
        if (!uri.startsWith("/")) {
            uri = "/" + uri;
        }
        return uri;
    }

    @Override
    public void handlerRemoved(ChannelHandlerContext ctx) {
        if (httpObjects != null) {
            HttpObject b;
            while ((b = httpObjects.poll()) != null) {
                ctx.fireChannelRead(b);
            }
        }
        ctx.flush();
        httpObjects = null;
    }
}
