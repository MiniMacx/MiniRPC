package org.tustcs.minirpc.server;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.*;
import io.netty.handler.codec.http2.*;
import net.sf.cglib.reflect.FastClass;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tustcs.minirpc.protocol.Base64Util;
import org.tustcs.minirpc.protocol.RpcRequest;
import org.tustcs.minirpc.protocol.RpcResponse;
import org.tustcs.minirpc.protocol.SerializationUtil;

import java.util.Map;

import static io.netty.handler.codec.http.HttpResponseStatus.OK;

/**
 * netty 服务端RPC handler
 */
public class RpcHandler extends Http2ConnectionHandler implements Http2FrameListener {

    private static final Logger logger = LoggerFactory.getLogger(RpcHandler.class);

    private static Map<String, Object> handlerMap;


    private static final String RPC_HEADER = "rpc";

    protected RpcHandler(Http2ConnectionDecoder decoder, Http2ConnectionEncoder encoder, Http2Settings initialSettings) {
        super(decoder, encoder, initialSettings);
    }

    public void setHandlerMap(Map<String, Object> map) {
        handlerMap = map;
    }

    private static Http2Headers http1HeadersToHttp2Headers(FullHttpRequest request) {
        CharSequence host = request.headers().get(HttpHeaderNames.HOST);
        Http2Headers http2Headers = new DefaultHttp2Headers()
                .method(HttpMethod.GET.asciiName())
                .path(request.uri())
                .scheme(HttpScheme.HTTP.name());
        if (http2Headers.get(RPC_HEADER) != null) {
            http2Headers.add(RPC_HEADER, request.headers().get(RPC_HEADER));
        }
        if (host != null) {
            http2Headers.authority(host);
        }
        return http2Headers;
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof HttpServerUpgradeHandler.UpgradeEvent) {
            HttpServerUpgradeHandler.UpgradeEvent upgradeEvent =
                    (HttpServerUpgradeHandler.UpgradeEvent) evt;

            onHeadersRead(ctx, 1, http1HeadersToHttp2Headers(upgradeEvent.upgradeRequest()), 0, true);
        }
        super.userEventTriggered(ctx, evt);
    }

    @Override
    public int onDataRead(ChannelHandlerContext channelHandlerContext, int i, ByteBuf byteBuf, int i1, boolean b) throws Http2Exception {
        System.out.println("dataRead");
        return 0;
    }

    @Override
    public void onHeadersRead(ChannelHandlerContext ctx, int streamId,
                              Http2Headers headers, int padding,
                              boolean endOfStream) {
        ByteBuf content = ctx.alloc().buffer();
        if (endOfStream) {
            if (headers.get(RPC_HEADER) != null) {
                RpcRequest request = null;
                try {
                    request = SerializationUtil.deserialize(
                            Base64Util.decodeBase64(headers.get(RPC_HEADER).toString()),
                            RpcRequest.class);
                } catch (Exception e) {
                    e.printStackTrace();
                }
                if(request != null){
                    RpcResponse response = new RpcResponse();
                    response.setRequestId(request.getRequestId());
                    try {
                        Object result = handle(request);
                        response.setResult(result);
                    } catch (Throwable t) {
                        response.setError(t.toString());
                        logger.error("RPC Server error", t);
                    }

                    content.writeBytes(SerializationUtil.serialize(response));
                    sendResponse(ctx, streamId, content);
                }
            } else {
                sendResponse(ctx, streamId, content);
            }
        }
    }

    private void sendResponse(ChannelHandlerContext ctx, int streamId, ByteBuf payload) {
        Http2Headers headers = new DefaultHttp2Headers().status(OK.codeAsText());
        encoder().writeHeaders(ctx, streamId, headers, 0, false, ctx.newPromise());
        encoder().writeData(ctx, streamId, payload, 0, true, ctx.newPromise());
//        ctx.writeAndFlush(payload).addListener( (ChannelFutureListener) channelFuture -> logger.info("!send!"));
    }

    @Override
    public void onHeadersRead(ChannelHandlerContext ctx, int i, Http2Headers http2Headers, int i1, short i2, boolean b, int i3, boolean b1) {
        onHeadersRead(ctx, i, http2Headers, i3, b1);
    }

    @Override
    public void onPriorityRead(ChannelHandlerContext channelHandlerContext, int i, int i1, short i2, boolean b) {

    }

    @Override
    public void onRstStreamRead(ChannelHandlerContext channelHandlerContext, int i, long l) throws Http2Exception {

    }

    @Override
    public void onSettingsAckRead(ChannelHandlerContext channelHandlerContext) throws Http2Exception {

    }

    @Override
    public void onSettingsRead(ChannelHandlerContext channelHandlerContext, Http2Settings http2Settings) throws Http2Exception {

    }

    @Override
    public void onPingRead(ChannelHandlerContext channelHandlerContext, ByteBuf byteBuf) throws Http2Exception {

    }

    @Override
    public void onPingAckRead(ChannelHandlerContext channelHandlerContext, ByteBuf byteBuf) throws Http2Exception {

    }

    @Override
    public void onPushPromiseRead(ChannelHandlerContext channelHandlerContext, int i, int i1, Http2Headers http2Headers, int i2) throws Http2Exception {

    }

    @Override
    public void onGoAwayRead(ChannelHandlerContext channelHandlerContext, int i, long l, ByteBuf byteBuf) throws Http2Exception {

    }

    @Override
    public void onWindowUpdateRead(ChannelHandlerContext channelHandlerContext, int i, int i1) throws Http2Exception {

    }

    @Override
    public void onUnknownFrame(ChannelHandlerContext channelHandlerContext, byte b, int i, Http2Flags http2Flags, ByteBuf byteBuf) throws Http2Exception {

    }


    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        logger.error("server caught exception", cause);
        ctx.close();
    }

    private Object handle(RpcRequest request) throws Throwable {
        String className = request.getClassName();
        Object serviceBean = handlerMap.get(className);

        Class<?> serviceClass = serviceBean.getClass();
        String methodName = request.getMethodName();
        Class<?>[] parameterTypes = request.getParameterTypes();
        Object[] parameters = request.getParameters();

        logger.debug(serviceClass.getName());
        logger.debug(methodName);
        for (int i = 0; i < parameterTypes.length; ++i) {
            logger.debug(parameterTypes[i].getName());
        }
        for (int i = 0; i < parameters.length; ++i) {
            logger.debug(parameters[i].toString());
        }
        // cglib
        FastClass serviceFastClass = FastClass.create(serviceClass);
        int methodIndex = serviceFastClass.getIndex(methodName, parameterTypes);
        return serviceFastClass.invoke(methodIndex, serviceBean, parameters);
    }
}
