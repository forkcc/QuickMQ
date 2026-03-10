package io.quickmq.mqtt.subscription;

import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.mqtt.MqttQoS;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SubscriptionStoreTest {

    private SubscriptionStore store;

    @BeforeEach
    void setUp() {
        store = new SubscriptionStore();
    }

    @Test
    void subscribe_and_find() {
        EmbeddedChannel ch = new EmbeddedChannel();
        store.subscribe(ch, "sensor/+/temp", MqttQoS.AT_LEAST_ONCE);

        try (SubscriberResult result = store.findSubscribers("sensor/room1/temp")) {
            assertEquals(1, result.size());
            assertSame(ch, result.get(0).channel());
        }
    }

    @Test
    void unsubscribe_removes() {
        EmbeddedChannel ch = new EmbeddedChannel();
        store.subscribe(ch, "a/b", MqttQoS.AT_MOST_ONCE);
        store.unsubscribe(ch, "a/b");

        try (SubscriberResult result = store.findSubscribers("a/b")) {
            assertEquals(0, result.size());
        }
    }

    @Test
    void removeChannel_cleansAll() {
        EmbeddedChannel ch = new EmbeddedChannel();
        store.subscribe(ch, "a/b", MqttQoS.AT_MOST_ONCE);
        store.subscribe(ch, "x/y", MqttQoS.AT_MOST_ONCE);
        store.removeChannel(ch);

        try (SubscriberResult r1 = store.findSubscribers("a/b")) {
            assertEquals(0, r1.size());
        }
        try (SubscriberResult r2 = store.findSubscribers("x/y")) {
            assertEquals(0, r2.size());
        }
    }

    @Test
    void multipleChannels_multipleTopics() {
        EmbeddedChannel ch1 = new EmbeddedChannel();
        EmbeddedChannel ch2 = new EmbeddedChannel();
        store.subscribe(ch1, "a/b", MqttQoS.AT_MOST_ONCE);
        store.subscribe(ch2, "a/+", MqttQoS.AT_LEAST_ONCE);

        try (SubscriberResult result = store.findSubscribers("a/b")) {
            assertEquals(2, result.size());
        }
    }

    @Test
    void findSubscribers_emptyStore() {
        try (SubscriberResult result = store.findSubscribers("no/match")) {
            assertEquals(0, result.size());
        }
    }

    @Test
    void subscriberResult_isRecycled() {
        EmbeddedChannel ch = new EmbeddedChannel();
        store.subscribe(ch, "a/b", MqttQoS.AT_MOST_ONCE);

        SubscriberResult r1;
        try (SubscriberResult result = store.findSubscribers("a/b")) {
            assertEquals(1, result.size());
            r1 = result;
        }

        try (SubscriberResult r2 = store.findSubscribers("a/b")) {
            assertSame(r1, r2, "Recycler should return the same object");
        }
    }

    @Test
    void removeChannel_nonExistent_noException() {
        assertDoesNotThrow(() -> store.removeChannel(new EmbeddedChannel()));
    }
}
