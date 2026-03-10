package io.quickmq.mqtt.handler;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.embedded.EmbeddedChannel;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Helper to obtain a valid ChannelHandlerContext from EmbeddedChannel.
 */
final class HandlerTestHelper {

    private HandlerTestHelper() {}

    static EmbeddedChannel channelWithCtx(AtomicReference<ChannelHandlerContext> ctxRef) {
        return new EmbeddedChannel(new ChannelInboundHandlerAdapter() {
            @Override
            public void channelRegistered(ChannelHandlerContext ctx) {
                ctxRef.set(ctx);
                ctx.fireChannelRegistered();
            }
        });
    }
}
