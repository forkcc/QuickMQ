package io.quickmq.mqtt.store;

import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.mqtt.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class RetainedStoreTest {

    private RetainedStore store;

    @BeforeEach
    void setUp() {
        store = new RetainedStore();
    }

    private MqttPublishMessage buildPublish(String topic, byte[] payload, boolean retain) {
        MqttFixedHeader fixed = new MqttFixedHeader(
                MqttMessageType.PUBLISH, false, MqttQoS.AT_MOST_ONCE, retain, 0);
        MqttPublishVariableHeader varHeader = new MqttPublishVariableHeader(topic, 0);
        return new MqttPublishMessage(fixed, varHeader, Unpooled.wrappedBuffer(payload));
    }

    @Test
    void put_and_deliverMatching_exact() {
        MqttPublishMessage msg = buildPublish("sensor/temp", "25.5".getBytes(StandardCharsets.UTF_8), true);
        store.put("sensor/temp", msg, true);

        EmbeddedChannel ch = new EmbeddedChannel();
        store.deliverMatching("sensor/temp", ch);

        Object out = ch.readOutbound();
        assertNotNull(out, "Should deliver retained message");
        assertTrue(out instanceof MqttPublishMessage);
        MqttPublishMessage delivered = (MqttPublishMessage) out;
        assertEquals("sensor/temp", delivered.variableHeader().topicName());
        delivered.release();
        msg.release();
    }

    @Test
    void put_emptyPayload_removesRetained() {
        MqttPublishMessage msg = buildPublish("a/b", "hello".getBytes(StandardCharsets.UTF_8), true);
        store.put("a/b", msg, true);

        MqttPublishMessage empty = buildPublish("a/b", new byte[0], true);
        store.put("a/b", empty, true);

        EmbeddedChannel ch = new EmbeddedChannel();
        store.deliverMatching("a/b", ch);
        assertNull(ch.readOutbound(), "Retained should be removed");
        msg.release();
    }

    @Test
    void put_retainFalse_ignored() {
        MqttPublishMessage msg = buildPublish("a/b", "data".getBytes(StandardCharsets.UTF_8), false);
        store.put("a/b", msg, false);

        EmbeddedChannel ch = new EmbeddedChannel();
        store.deliverMatching("a/b", ch);
        assertNull(ch.readOutbound());
        msg.release();
    }

    @Test
    void deliverMatching_wildcardPlus() {
        MqttPublishMessage msg = buildPublish("sensor/temp", "data".getBytes(StandardCharsets.UTF_8), true);
        store.put("sensor/temp", msg, true);

        EmbeddedChannel ch = new EmbeddedChannel();
        store.deliverMatching("sensor/+", ch);

        Object out = ch.readOutbound();
        assertNotNull(out);
        ((MqttPublishMessage) out).release();
        msg.release();
    }

    @Test
    void deliverMatching_wildcardHash() {
        MqttPublishMessage msg1 = buildPublish("a/b", "1".getBytes(StandardCharsets.UTF_8), true);
        MqttPublishMessage msg2 = buildPublish("a/b/c", "2".getBytes(StandardCharsets.UTF_8), true);
        store.put("a/b", msg1, true);
        store.put("a/b/c", msg2, true);

        EmbeddedChannel ch = new EmbeddedChannel();
        store.deliverMatching("a/#", ch);

        int count = 0;
        Object out;
        while ((out = ch.readOutbound()) != null) {
            ((MqttPublishMessage) out).release();
            count++;
        }
        assertEquals(2, count, "Should deliver all messages under a/#");
        msg1.release();
        msg2.release();
    }

    @Test
    void deliverMatching_inactiveChannel_noDelivery() {
        MqttPublishMessage msg = buildPublish("a/b", "data".getBytes(StandardCharsets.UTF_8), true);
        store.put("a/b", msg, true);

        EmbeddedChannel ch = new EmbeddedChannel();
        ch.close();
        store.deliverMatching("a/b", ch);
        assertNull(ch.readOutbound());
        msg.release();
    }

    @Test
    void deliverMatching_noMatch_noDelivery() {
        MqttPublishMessage msg = buildPublish("a/b", "data".getBytes(StandardCharsets.UTF_8), true);
        store.put("a/b", msg, true);

        EmbeddedChannel ch = new EmbeddedChannel();
        store.deliverMatching("x/y", ch);
        assertNull(ch.readOutbound());
        msg.release();
    }

    @Test
    void put_replaces_previousRetained() {
        MqttPublishMessage msg1 = buildPublish("a/b", "old".getBytes(StandardCharsets.UTF_8), true);
        store.put("a/b", msg1, true);

        MqttPublishMessage msg2 = buildPublish("a/b", "new".getBytes(StandardCharsets.UTF_8), true);
        store.put("a/b", msg2, true);

        EmbeddedChannel ch = new EmbeddedChannel();
        store.deliverMatching("a/b", ch);
        MqttPublishMessage delivered = (MqttPublishMessage) ch.readOutbound();
        assertNotNull(delivered);
        byte[] bytes = new byte[delivered.payload().readableBytes()];
        delivered.payload().readBytes(bytes);
        assertEquals("new", new String(bytes, StandardCharsets.UTF_8));
        delivered.release();
    }
}
