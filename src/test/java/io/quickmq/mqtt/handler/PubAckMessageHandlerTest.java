package io.quickmq.mqtt.handler;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.mqtt.*;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class PubAckMessageHandlerTest {

    @Test
    void messageType_isPUBACK() {
        assertEquals(MqttMessageType.PUBACK, new PubAckMessageHandler().messageType());
    }

    @Test
    void handle_noResponseSent() {
        var ctxRef = new AtomicReference<ChannelHandlerContext>();
        EmbeddedChannel ch = HandlerTestHelper.channelWithCtx(ctxRef);

        new PubAckMessageHandler().handle(ctxRef.get(), MqttMessageFactory.newMessage(
                new MqttFixedHeader(MqttMessageType.PUBACK, false, MqttQoS.AT_MOST_ONCE, false, 0),
                MqttMessageIdVariableHeader.from(10), null));

        assertNull(ch.readOutbound());
    }
}
