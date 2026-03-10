package io.quickmq.mqtt.handler;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.mqtt.*;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class PingReqMessageHandlerTest {

    @Test
    void messageType_isPINGREQ() {
        assertEquals(MqttMessageType.PINGREQ, new PingReqMessageHandler().messageType());
    }

    @Test
    void handle_sendsPINGRESP() {
        var ctxRef = new AtomicReference<ChannelHandlerContext>();
        EmbeddedChannel ch = HandlerTestHelper.channelWithCtx(ctxRef);

        new PingReqMessageHandler().handle(ctxRef.get(), MqttMessageFactory.newMessage(
                new MqttFixedHeader(MqttMessageType.PINGREQ, false, MqttQoS.AT_MOST_ONCE, false, 0),
                null, null));

        MqttMessage resp = (MqttMessage) ch.readOutbound();
        assertNotNull(resp);
        assertEquals(MqttMessageType.PINGRESP, resp.fixedHeader().messageType());
    }
}
