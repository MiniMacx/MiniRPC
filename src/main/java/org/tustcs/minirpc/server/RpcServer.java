package org.tustcs.minirpc.server;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.HttpServerUpgradeHandler;
import io.netty.handler.codec.http2.CleartextHttp2ServerUpgradeHandler;
import io.netty.handler.codec.http2.Http2CodecUtil;
import io.netty.handler.codec.http2.Http2ServerUpgradeCodec;
import io.netty.util.AsciiString;
import org.apache.commons.collections4.MapUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.tustcs.minirpc.registry.ServiceRegistry;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * rpc 服务端
 */
public class RpcServer implements ApplicationContextAware, InitializingBean {

    private static final Logger logger = LoggerFactory.getLogger(RpcServer.class);

    private String serverAddress;
    private ServiceRegistry serviceRegistry;

    private Map<String, Object> handlerMap = new HashMap<>();
    private static ThreadPoolExecutor threadPoolExecutor;

    private EventLoopGroup bossGroup = null;
    private EventLoopGroup workerGroup = null;

    public RpcServer(String serverAddress) {
        this.serverAddress = serverAddress;
    }

    public RpcServer(String serverAddress, ServiceRegistry serviceRegistry) {
        this.serverAddress = serverAddress;
        this.serviceRegistry = serviceRegistry;
    }

    @Override
    public void setApplicationContext(ApplicationContext ctx) throws BeansException {
        Map<String, Object> serviceBeanMap = ctx.getBeansWithAnnotation(RpcService.class);
        if (MapUtils.isNotEmpty(serviceBeanMap)) {
            for (Object serviceBean : serviceBeanMap.values()) {
                String interfaceName = serviceBean.getClass()
                        .getAnnotation(RpcService.class)
                        .value()
                        .getName();
                logger.info("service: {}", interfaceName);
                handlerMap.put(interfaceName, serviceBean);
            }
        }
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        start();
    }

    public void stop() {
        if (bossGroup != null) {
            bossGroup.shutdownGracefully();
        }
        if (workerGroup != null) {
            workerGroup.shutdownGracefully();
        }
    }

    public static void submit(Runnable task) {
        if (threadPoolExecutor == null) {
            synchronized (RpcServer.class) {
                if (threadPoolExecutor == null) {
                    threadPoolExecutor = new ThreadPoolExecutor(16, 16, 600L,
                            TimeUnit.SECONDS, new ArrayBlockingQueue<Runnable>(65536));
                }
            }
        }
        threadPoolExecutor.submit(task);
    }

    public RpcServer addService(String interfaceName, Object serviceBean) {
        if (!handlerMap.containsKey(interfaceName)) {
            logger.info("Loading service: {}", interfaceName);
            handlerMap.put(interfaceName, serviceBean);
        }

        return this;
    }

    public void start() throws Exception {
        if (bossGroup == null && workerGroup == null) {
            bossGroup = new NioEventLoopGroup();
            workerGroup = new NioEventLoopGroup();
            ServerBootstrap bootstrap = new ServerBootstrap();
            bootstrap.group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        public void initChannel(SocketChannel channel) {
                            final ChannelPipeline p = channel.pipeline();
                            final HttpServerCodec sourceCodec = new HttpServerCodec();
                            final HttpServerUpgradeHandler upgradeHandler = new
                                    HttpServerUpgradeHandler(sourceCodec, UPGRADE_CODEC_FACTORY);
                            final CleartextHttp2ServerUpgradeHandler cleartextHttp2ServerUpgradeHandler =
                                    new CleartextHttp2ServerUpgradeHandler(sourceCodec, upgradeHandler,
                                            new RpcHandlerBuilder().build(handlerMap));
                            p.addLast(cleartextHttp2ServerUpgradeHandler);
                            p.addLast(new UserEventLogger());
                        }
                    })
                    .option(ChannelOption.SO_BACKLOG, 1024)
                    .childOption(ChannelOption.SO_KEEPALIVE, true);

            String[] array = serverAddress.split(":");
            String host = array[0];
            int port = Integer.parseInt(array[1]);
            ChannelFuture future = bootstrap.bind(host, port).sync();
            logger.info("Server:{} started", port);
            if (serviceRegistry != null) {
                serviceRegistry.register(serverAddress);
            }
            future.channel().closeFuture().sync();
        }
    }

    private static final HttpServerUpgradeHandler.UpgradeCodecFactory UPGRADE_CODEC_FACTORY = protocol -> {
        if (AsciiString.contentEquals(Http2CodecUtil.HTTP_UPGRADE_PROTOCOL_NAME, protocol)) {
            return new Http2ServerUpgradeCodec(new RpcHandlerBuilder().build());
        } else {
            return null;
        }
    };

    private static class UserEventLogger extends ChannelInboundHandlerAdapter {
        @Override
        public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
            System.out.println("User Event Triggered: " + evt);
            ctx.fireUserEventTriggered(evt);
        }
    }

}
