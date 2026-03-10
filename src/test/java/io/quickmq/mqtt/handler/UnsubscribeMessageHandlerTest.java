package io.quickmq.mqtt.handler;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.mqtt.*;
import io.quickmq.mqtt.ChannelAttributes;
import io.quickmq.mqtt.subscription.SubscriptionStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class UnsubscribeMessageHandlerTest {

    private SubscriptionStore subStore;
    private UnsubscribeMessageHandler handler;

    @BeforeEach
    void setUp() {
        subStore = new SubscriptionStore();
        handler = new UnsubscribeMessageHandler(subStore, null, null);
    }

    @Test
    void messageType() {
        assertEquals(MqttMessageType.UNSUBSCRIBE, handler.messageType());
    }

    @Test
    void handle_sendsUNSUBACK() {
        var ctxRef = new AtomicReference<ChannelHandlerContext>();
        EmbeddedChannel ch = HandlerTestHelper.channelWithCtx(ctxRef);
        ch.attr(ChannelAttributes.CLIENT_ID).set("c1");
        subStore.subscribe(ch, "a/b", MqttQoS.AT_MOST_ONCE);

        handler.handle(ctxRef.get(), buildUnsub(33, List.of("a/b")));

        MqttUnsubAckMessage ack = (MqttUnsubAckMessage) ch.readOutbound();
        assertNotNull(ack);
        assertEquals(33, ack.variableHeader().messageId());
    }

    @Test
    void handle_removesFromStore() {
        var ctxRef = new AtomicReference<ChannelHandlerContext>();
        EmbeddedChannel ch = HandlerTestHelper.channelWithCtx(ctxRef);
        ch.attr(ChannelAttributes.CLIENT_ID).set("c1");
        subStore.subscribe(ch, "a/b", MqttQoS.AT_MOST_ONCE);

        handler.handle(ctxRef.get(), buildUnsub(1, List.of("a/b")));

        var result = subStore.findSubscribers("a/b");
        assertEquals(0, result.size());
        result.close();
    }

    private MqttUnsubscribeMessage buildUnsub(int messageId, List<String> topics) {
        return new MqttUnsubscribeMessage(
                new MqttFixedHeader(MqttMessageType.UNSUBSCRIBE, false, MqttQoS.AT_LEAST_ONCE, false, 0),
                MqttMessageIdVariableHeader.from(messageId),
                new MqttUnsubscribePayload(topics));
    }
}
