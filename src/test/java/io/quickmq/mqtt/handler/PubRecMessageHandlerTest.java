package io.quickmq.mqtt.handler;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.mqtt.*;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class PubRecMessageHandlerTest {

    @Test
    void messageType_isPUBREC() {
        assertEquals(MqttMessageType.PUBREC, new PubRecMessageHandler().messageType());
    }

    @Test
    void handle_sendsPUBREL() {
        var ctxRef = new AtomicReference<ChannelHandlerContext>();
        EmbeddedChannel ch = HandlerTestHelper.channelWithCtx(ctxRef);

        new PubRecMessageHandler().handle(ctxRef.get(), MqttMessageFactory.newMessage(
                new MqttFixedHeader(MqttMessageType.PUBREC, false, MqttQoS.AT_MOST_ONCE, false, 0),
                MqttMessageIdVariableHeader.from(55), null));

        MqttMessage resp = (MqttMessage) ch.readOutbound();
        assertNotNull(resp);
        assertEquals(MqttMessageType.PUBREL, resp.fixedHeader().messageType());
        assertEquals(55, ((MqttMessageIdVariableHeader) resp.variableHeader()).messageId());
    }
}
