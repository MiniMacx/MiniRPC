package org.tustcs.minirpc.client;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.*;
import io.netty.handler.codec.http2.*;

import static io.netty.handler.logging.LogLevel.INFO;

/**
 * netty rpc客户端初始化类
 */
public class RpcClientInitializer extends ChannelInitializer<SocketChannel> {

    private static final Http2FrameLogger logger = new Http2FrameLogger(INFO, RpcClientInitializer.class);

    private HttpToHttp2ConnectionHandler connectionHandler;

    private RpcClientHandler rpcClientHandler;

    private Http2SettingsHandler settingsHandler;

    @Override
    protected void initChannel(SocketChannel ch) {
        final Http2Connection connection = new DefaultHttp2Connection(false);
        connectionHandler = new HttpToHttp2ConnectionHandlerBuilder()
                .frameListener(new DelegatingDecompressorFrameListener(
                        connection,
                        new InboundHttp2ToHttpAdapterBuilder(connection)
                                .maxContentLength(Integer.MAX_VALUE)
                                .propagateSettings(true)
                                .build()))
                .frameLogger(logger)
                .connection(connection)
                .build();
        rpcClientHandler = new RpcClientHandler();
        settingsHandler = new Http2SettingsHandler(ch.newPromise());
        rpcClientHandler.setSettingsHandler(settingsHandler);
        ChannelPipeline cp = ch.pipeline();
        HttpClientCodec sourceCodec = new HttpClientCodec();
        Http2ClientUpgradeCodec upgradeCodec = new Http2ClientUpgradeCodec(connectionHandler);
        HttpClientUpgradeHandler upgradeHandler = new HttpClientUpgradeHandler(sourceCodec, upgradeCodec, 65536);
        cp.addLast(sourceCodec,
                upgradeHandler,
                new UpgradeRequestHandler(),
                rpcClientHandler,
                new UserEventLogger());
    }

    private final class UpgradeRequestHandler extends ChannelInboundHandlerAdapter {
        @Override
        public void channelActive(ChannelHandlerContext ctx) {
            DefaultFullHttpRequest upgradeRequest =
                    new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/");
            ctx.writeAndFlush(upgradeRequest);
            ctx.fireChannelActive();
            ctx.pipeline().remove(this);
            configureEndOfPipeline(ctx.pipeline());
        }
    }

    /**
     * 记录用户行为的事件监听器
     */
    private static class UserEventLogger extends ChannelInboundHandlerAdapter {
        @Override
        public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
            System.out.println("User Event Triggered: " + evt);
            ctx.fireUserEventTriggered(evt);
        }
    }

    private void configureEndOfPipeline(ChannelPipeline pipeline) {
        pipeline.addLast(settingsHandler);
    }
}
