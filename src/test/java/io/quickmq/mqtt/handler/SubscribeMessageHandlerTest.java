package io.quickmq.mqtt.handler;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.mqtt.*;
import io.quickmq.mqtt.ChannelAttributes;
import io.quickmq.mqtt.store.RetainedStore;
import io.quickmq.mqtt.subscription.SubscriptionStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class SubscribeMessageHandlerTest {

    private SubscriptionStore subStore;
    private RetainedStore retainedStore;
    private SubscribeMessageHandler handler;

    @BeforeEach
    void setUp() {
        subStore = new SubscriptionStore();
        retainedStore = new RetainedStore();
        handler = new SubscribeMessageHandler(subStore, retainedStore, null, null);
    }

    @Test
    void messageType() {
        assertEquals(MqttMessageType.SUBSCRIBE, handler.messageType());
    }

    @Test
    void handle_sendsSUBACK() {
        var ctxRef = new AtomicReference<ChannelHandlerContext>();
        EmbeddedChannel ch = HandlerTestHelper.channelWithCtx(ctxRef);
        ch.attr(ChannelAttributes.CLIENT_ID).set("client-1");

        handler.handle(ctxRef.get(), buildSubscribe(42,
                List.of(new MqttTopicSubscription("a/b", MqttQoS.AT_LEAST_ONCE))));

        MqttSubAckMessage subAck = (MqttSubAckMessage) ch.readOutbound();
        assertNotNull(subAck);
        assertEquals(42, subAck.variableHeader().messageId());
        assertEquals(List.of(1), subAck.payload().grantedQoSLevels());
    }

    @Test
    void handle_subscribesInStore() {
        var ctxRef = new AtomicReference<ChannelHandlerContext>();
        EmbeddedChannel ch = HandlerTestHelper.channelWithCtx(ctxRef);
        ch.attr(ChannelAttributes.CLIENT_ID).set("client-1");

        handler.handle(ctxRef.get(), buildSubscribe(1,
                List.of(new MqttTopicSubscription("sensor/#", MqttQoS.AT_MOST_ONCE))));

        var result = subStore.findSubscribers("sensor/temp");
        assertEquals(1, result.size());
        result.close();
    }

    @Test
    void handle_multipleTopics() {
        var ctxRef = new AtomicReference<ChannelHandlerContext>();
        EmbeddedChannel ch = HandlerTestHelper.channelWithCtx(ctxRef);
        ch.attr(ChannelAttributes.CLIENT_ID).set("c1");

        handler.handle(ctxRef.get(), buildSubscribe(10, List.of(
                new MqttTopicSubscription("a/b", MqttQoS.AT_MOST_ONCE),
                new MqttTopicSubscription("x/y", MqttQoS.EXACTLY_ONCE))));

        MqttSubAckMessage subAck = (MqttSubAckMessage) ch.readOutbound();
        assertEquals(List.of(0, 2), subAck.payload().grantedQoSLevels());
    }

    private MqttSubscribeMessage buildSubscribe(int messageId, List<MqttTopicSubscription> subs) {
        return new MqttSubscribeMessage(
                new MqttFixedHeader(MqttMessageType.SUBSCRIBE, false, MqttQoS.AT_LEAST_ONCE, false, 0),
                MqttMessageIdVariableHeader.from(messageId),
                new MqttSubscribePayload(subs));
    }
}
