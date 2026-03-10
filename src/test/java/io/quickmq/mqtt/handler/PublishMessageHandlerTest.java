package io.quickmq.mqtt.handler;

import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.mqtt.*;
import io.quickmq.mqtt.ChannelAttributes;
import io.quickmq.mqtt.store.RetainedStore;
import io.quickmq.mqtt.subscription.SubscriptionStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class PublishMessageHandlerTest {

    private SubscriptionStore subStore;
    private RetainedStore retainedStore;
    private PublishMessageHandler handler;

    @BeforeEach
    void setUp() {
        subStore = new SubscriptionStore();
        retainedStore = new RetainedStore();
        handler = new PublishMessageHandler(subStore, retainedStore, null, null);
    }

    @Test
    void messageType() {
        assertEquals(MqttMessageType.PUBLISH, handler.messageType());
    }

    @Test
    void handle_qos0_forwardsToSubscriber() {
        var pubCtxRef = new AtomicReference<ChannelHandlerContext>();
        EmbeddedChannel publisher = HandlerTestHelper.channelWithCtx(pubCtxRef);
        publisher.attr(ChannelAttributes.CLIENT_ID).set("pub");

        EmbeddedChannel subscriber = new EmbeddedChannel();
        subStore.subscribe(subscriber, "test/topic", MqttQoS.AT_MOST_ONCE);

        handler.handle(pubCtxRef.get(), buildPublish("test/topic", "hello", MqttQoS.AT_MOST_ONCE, false, 0));

        Object out = subscriber.readOutbound();
        assertNotNull(out, "Subscriber should receive published message");
        assertTrue(out instanceof MqttPublishMessage);
        MqttPublishMessage fwd = (MqttPublishMessage) out;
        assertEquals("test/topic", fwd.variableHeader().topicName());
        fwd.release();
    }

    @Test
    void handle_qos1_sendsPUBACK() {
        var ctxRef = new AtomicReference<ChannelHandlerContext>();
        EmbeddedChannel publisher = HandlerTestHelper.channelWithCtx(ctxRef);
        publisher.attr(ChannelAttributes.CLIENT_ID).set("pub");

        handler.handle(ctxRef.get(), buildPublish("t", "data", MqttQoS.AT_LEAST_ONCE, false, 42));

        MqttMessage ack = (MqttMessage) publisher.readOutbound();
        assertNotNull(ack);
        assertEquals(MqttMessageType.PUBACK, ack.fixedHeader().messageType());
        assertEquals(42, ((MqttMessageIdVariableHeader) ack.variableHeader()).messageId());
    }

    @Test
    void handle_qos2_sendsPUBREC() {
        var ctxRef = new AtomicReference<ChannelHandlerContext>();
        EmbeddedChannel publisher = HandlerTestHelper.channelWithCtx(ctxRef);
        publisher.attr(ChannelAttributes.CLIENT_ID).set("pub");

        handler.handle(ctxRef.get(), buildPublish("t", "data", MqttQoS.EXACTLY_ONCE, false, 99));

        MqttMessage rec = (MqttMessage) publisher.readOutbound();
        assertNotNull(rec);
        assertEquals(MqttMessageType.PUBREC, rec.fixedHeader().messageType());
    }

    @Test
    void handle_retain_storesMessage() {
        var ctxRef = new AtomicReference<ChannelHandlerContext>();
        EmbeddedChannel publisher = HandlerTestHelper.channelWithCtx(ctxRef);
        publisher.attr(ChannelAttributes.CLIENT_ID).set("pub");

        handler.handle(ctxRef.get(), buildPublish("retained/topic", "persisted", MqttQoS.AT_MOST_ONCE, true, 0));

        EmbeddedChannel newSub = new EmbeddedChannel();
        retainedStore.deliverMatching("retained/topic", newSub);
        Object retained = newSub.readOutbound();
        assertNotNull(retained, "Retained message should be delivered to new subscriber");
        ((MqttPublishMessage) retained).release();
    }

    @Test
    void handle_publisherNotForwarded() {
        var ctxRef = new AtomicReference<ChannelHandlerContext>();
        EmbeddedChannel publisher = HandlerTestHelper.channelWithCtx(ctxRef);
        publisher.attr(ChannelAttributes.CLIENT_ID).set("pub");
        subStore.subscribe(publisher, "loop/test", MqttQoS.AT_MOST_ONCE);

        handler.handle(ctxRef.get(), buildPublish("loop/test", "data", MqttQoS.AT_MOST_ONCE, false, 0));

        assertNull(publisher.readOutbound(), "Publisher should not receive its own message");
    }

    @Test
    void handle_noSubscribers_noError() {
        var ctxRef = new AtomicReference<ChannelHandlerContext>();
        EmbeddedChannel publisher = HandlerTestHelper.channelWithCtx(ctxRef);
        publisher.attr(ChannelAttributes.CLIENT_ID).set("pub");

        assertDoesNotThrow(() -> handler.handle(ctxRef.get(),
                buildPublish("nobody/listens", "data", MqttQoS.AT_MOST_ONCE, false, 0)));
    }

    private MqttPublishMessage buildPublish(String topic, String payload, MqttQoS qos, boolean retain, int packetId) {
        return new MqttPublishMessage(
                new MqttFixedHeader(MqttMessageType.PUBLISH, false, qos, retain, 0),
                new MqttPublishVariableHeader(topic, packetId),
                Unpooled.wrappedBuffer(payload.getBytes(StandardCharsets.UTF_8)));
    }
}
