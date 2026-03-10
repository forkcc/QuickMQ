package io.quickmq.mqtt.handler;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.mqtt.*;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class PubRelMessageHandlerTest {

    @Test
    void messageType_isPUBREL() {
        assertEquals(MqttMessageType.PUBREL, new PubRelMessageHandler().messageType());
    }

    @Test
    void handle_sendsPUBCOMP() {
        var ctxRef = new AtomicReference<ChannelHandlerContext>();
        EmbeddedChannel ch = HandlerTestHelper.channelWithCtx(ctxRef);

        new PubRelMessageHandler().handle(ctxRef.get(), MqttMessageFactory.newMessage(
                new MqttFixedHeader(MqttMessageType.PUBREL, false, MqttQoS.AT_LEAST_ONCE, false, 0),
                MqttMessageIdVariableHeader.from(77), null));

        MqttMessage resp = (MqttMessage) ch.readOutbound();
        assertNotNull(resp);
        assertEquals(MqttMessageType.PUBCOMP, resp.fixedHeader().messageType());
        assertEquals(77, ((MqttMessageIdVariableHeader) resp.variableHeader()).messageId());
    }
}
