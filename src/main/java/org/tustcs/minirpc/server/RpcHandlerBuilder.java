package org.tustcs.minirpc.server;

import io.netty.handler.codec.http2.*;

import java.util.Map;

import static io.netty.handler.logging.LogLevel.INFO;

/**
 * rpc handler构造器
 */
public class RpcHandlerBuilder extends AbstractHttp2ConnectionHandlerBuilder<RpcHandler, RpcHandlerBuilder> {

    private static final Http2FrameLogger logger = new Http2FrameLogger(INFO, RpcHandler.class);

    public RpcHandlerBuilder() {
        frameLogger(logger);
    }


    @Override
    protected RpcHandler build() {
        return super.build();
    }

    protected RpcHandler build(Map<String, Object> handlerMap) {
        RpcHandler handler = super.build();
        handler.setHandlerMap(handlerMap);
        return handler;
    }

    @Override
    protected RpcHandler build(Http2ConnectionDecoder decoder, Http2ConnectionEncoder encoder, Http2Settings http2Settings) {
        RpcHandler rpcHandler = new RpcHandler(decoder, encoder, http2Settings);
        frameListener(rpcHandler);
        return rpcHandler;
    }
}
