package io.quickmq.mqtt.handler;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.mqtt.*;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class NoOpMessageHandlerTest {

    @Test
    void messageType_throwsUnsupported() {
        assertThrows(UnsupportedOperationException.class, () -> new NoOpMessageHandler().messageType());
    }

    @Test
    void handle_logsButDoesNothing() {
        var ctxRef = new AtomicReference<ChannelHandlerContext>();
        EmbeddedChannel ch = HandlerTestHelper.channelWithCtx(ctxRef);

        assertDoesNotThrow(() -> new NoOpMessageHandler().handle(ctxRef.get(), MqttMessageFactory.newMessage(
                new MqttFixedHeader(MqttMessageType.PINGREQ, false, MqttQoS.AT_MOST_ONCE, false, 0),
                null, null)));

        assertNull(ch.readOutbound());
    }
}
