package org.tustcs.minirpc.client;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.handler.codec.http.*;
import io.netty.handler.codec.http2.HttpConversionUtil;
import io.netty.util.CharsetUtil;
import io.netty.util.internal.PlatformDependent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tustcs.minirpc.protocol.Base64Util;
import org.tustcs.minirpc.protocol.RpcRequest;
import org.tustcs.minirpc.protocol.RpcResponse;
import org.tustcs.minirpc.protocol.SerializationUtil;

import java.net.SocketAddress;
import java.util.AbstractMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * netty rpc客户端handler
 */
public class RpcClientHandler extends SimpleChannelInboundHandler<FullHttpResponse> {
    private static final Logger logger = LoggerFactory.getLogger(RpcClientHandler.class);

    private ConcurrentHashMap<String, RPCFuture> pendingRPC = new ConcurrentHashMap<>();
    private final Map<Integer, Map.Entry<ChannelFuture, ChannelPromise>> streamidPromiseMap;

    private volatile Channel channel;
    private ChannelHandlerContext context;
    private SocketAddress remotePeer;
    private Http2SettingsHandler settingsHandler;
    private static int streamId = 3;
    private static final String RPC_HEADER = "rpc";

    public RpcClientHandler() {
        this.streamidPromiseMap = PlatformDependent.newConcurrentHashMap();
    }

    public void setSettingsHandler(Http2SettingsHandler settingsHandler) {
        this.settingsHandler = settingsHandler;
    }

    public Channel getChannel() {
        return channel;
    }

    public SocketAddress getRemotePeer() {
        return remotePeer;
    }

    public Map.Entry<ChannelFuture, ChannelPromise> put(int streamId, ChannelFuture writeFuture, ChannelPromise promise) {
        return streamidPromiseMap.put(streamId, new AbstractMap.SimpleEntry<>(writeFuture, promise));
    }

    public void awaitResponses(long timeout, TimeUnit unit) {
        Iterator<Map.Entry<Integer, Map.Entry<ChannelFuture, ChannelPromise>>> itr = streamidPromiseMap.entrySet().iterator();
        while (itr.hasNext()) {
            Map.Entry<Integer, Map.Entry<ChannelFuture, ChannelPromise>> entry = itr.next();
            ChannelFuture writeFuture = entry.getValue().getKey();
            if (!writeFuture.awaitUninterruptibly(timeout, unit)) {
                throw new IllegalStateException("Timed out waiting to write for stream id " + entry.getKey());
            }
            if (!writeFuture.isSuccess()) {
                throw new RuntimeException(writeFuture.cause());
            }
            ChannelPromise promise = entry.getValue().getValue();
            if (!promise.awaitUninterruptibly(timeout, unit)) {
                throw new IllegalStateException("Timed out waiting for response on stream id " + entry.getKey());
            }
            if (!promise.isSuccess()) {
                throw new RuntimeException(promise.cause());
            }
            System.out.println("---Stream id: " + entry.getKey() + " received---");
            itr.remove();
        }
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        super.channelActive(ctx);
        this.remotePeer = this.channel.remoteAddress();
    }

    @Override
    public void channelRegistered(ChannelHandlerContext ctx) throws Exception {
        super.channelRegistered(ctx);
        this.context = ctx;
        this.channel = ctx.channel();
    }

    @Override
    public void channelRead0(ChannelHandlerContext ctx, FullHttpResponse response) throws Exception {
        Integer streamId = response.headers().getInt(HttpConversionUtil.ExtensionHeaderNames.STREAM_ID.text());
        if (streamId == null) {
            System.err.println("error message received: " + response);
            return;
        }

        Map.Entry<ChannelFuture, ChannelPromise> entry = streamidPromiseMap.get(streamId);
        if (entry == null) {
            System.err.println("Message received: streamId = " + streamId);
        } else {
            // Do stuff with the message (for now just print it)
            ByteBuf content = response.content();
            if (content.isReadable()) {
                int contentLength = content.readableBytes();

                byte[] arr = new byte[contentLength];
                content.readBytes(arr);
                RpcResponse rpcResponse = SerializationUtil.deserialize(arr, RpcResponse.class);
                RPCFuture rpcFuture = pendingRPC.get(rpcResponse.getRequestId());
                if (rpcFuture != null) {
                    pendingRPC.remove(rpcResponse.getRequestId());
                    rpcFuture.done(rpcResponse);
                }
                System.out.println(new String(arr, 0, contentLength, CharsetUtil.UTF_8));

            }

            entry.getValue().setSuccess();
        }
    }

    public RPCFuture sendRequest(RpcRequest request) throws Exception {
        channel = this.getChannel();
        FullHttpRequest httpRequest = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1,
                HttpMethod.POST, "/");
        settingsHandler.awaitSettings(50, TimeUnit.SECONDS);
        RPCFuture rpcFuture = new RPCFuture(request);
        try {
            httpRequest.headers().add(RPC_HEADER, Base64Util.encodeBase64(SerializationUtil.serialize(request)));
            httpRequest.headers().add(HttpConversionUtil.
                    ExtensionHeaderNames.SCHEME.text(), "http");
            httpRequest.headers().add(HttpHeaderNames.ACCEPT_ENCODING,
                    HttpHeaderValues.GZIP);
            httpRequest.headers().add(HttpHeaderNames.ACCEPT_ENCODING,
                    HttpHeaderValues.DEFLATE);
            pendingRPC.put(request.getRequestId(), rpcFuture);
            this.put(streamId, channel.write(httpRequest),
                    channel.newPromise());
            streamId += 2;
            channel.flush();
            this.awaitResponses(30,
                    TimeUnit.SECONDS);
            return rpcFuture;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        logger.error("client caught exception", cause);
        ctx.close();
    }

    public void close() {
        channel.writeAndFlush(Unpooled.EMPTY_BUFFER).addListener(ChannelFutureListener.CLOSE);
    }

}
