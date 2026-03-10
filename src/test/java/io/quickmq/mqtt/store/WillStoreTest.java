package io.quickmq.mqtt.store;

import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.mqtt.*;
import io.quickmq.mqtt.subscription.SubscriptionStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class WillStoreTest {

    private WillStore willStore;
    private SubscriptionStore subStore;
    private RetainedStore retainedStore;

    @BeforeEach
    void setUp() {
        willStore = new WillStore();
        subStore = new SubscriptionStore();
        retainedStore = new RetainedStore();
    }

    private MqttConnectMessage buildConnect(String clientId, String willTopic,
                                            byte[] willMessage, int willQos,
                                            boolean willRetain, boolean willFlag) {
        MqttConnectVariableHeader varHeader = new MqttConnectVariableHeader(
                "MQTT", 4, false, false, willRetain, willQos, willFlag, true, 60);
        MqttConnectPayload payload = new MqttConnectPayload(
                clientId, null, willMessage, null, null);
        // Use the constructor that takes willTopic
        MqttConnectPayload payloadWithWill = new MqttConnectPayload(
                clientId, null, willTopic, willMessage, null, (byte[]) null);
        MqttFixedHeader fixed = new MqttFixedHeader(
                MqttMessageType.CONNECT, false, MqttQoS.AT_MOST_ONCE, false, 0);
        return new MqttConnectMessage(fixed, varHeader, payloadWithWill);
    }

    @Test
    void store_and_trigger_delivers() {
        EmbeddedChannel publisher = new EmbeddedChannel();
        EmbeddedChannel subscriber = new EmbeddedChannel();
        subStore.subscribe(subscriber, "will/topic", MqttQoS.AT_MOST_ONCE);

        MqttConnectMessage connect = buildConnect("client1", "will/topic",
                "goodbye".getBytes(), 0, false, true);
        willStore.store(publisher, connect);

        willStore.trigger(publisher, subStore, retainedStore);

        Object msg = subscriber.readOutbound();
        assertNotNull(msg, "Should deliver will message");
        assertTrue(msg instanceof MqttPublishMessage);
        ((MqttPublishMessage) msg).release();
    }

    @Test
    void clear_preventsTriger() {
        EmbeddedChannel publisher = new EmbeddedChannel();
        EmbeddedChannel subscriber = new EmbeddedChannel();
        subStore.subscribe(subscriber, "will/topic", MqttQoS.AT_MOST_ONCE);

        MqttConnectMessage connect = buildConnect("client1", "will/topic",
                "goodbye".getBytes(), 0, false, true);
        willStore.store(publisher, connect);
        willStore.clear(publisher);

        willStore.trigger(publisher, subStore, retainedStore);
        assertNull(subscriber.readOutbound(), "Should not deliver after clear");
    }

    @Test
    void store_noWillFlag_doesNotStore() {
        EmbeddedChannel ch = new EmbeddedChannel();
        MqttConnectMessage connect = buildConnect("c1", null, null, 0, false, false);
        willStore.store(ch, connect);

        EmbeddedChannel sub = new EmbeddedChannel();
        subStore.subscribe(sub, "#", MqttQoS.AT_MOST_ONCE);
        willStore.trigger(ch, subStore, retainedStore);
        assertNull(sub.readOutbound());
    }

    @Test
    void trigger_noSubscribers_noError() {
        EmbeddedChannel publisher = new EmbeddedChannel();
        MqttConnectMessage connect = buildConnect("c1", "will/t",
                "bye".getBytes(), 0, false, true);
        willStore.store(publisher, connect);

        assertDoesNotThrow(() -> willStore.trigger(publisher, subStore, retainedStore));
    }

    @Test
    void trigger_nonExistentChannel_noError() {
        assertDoesNotThrow(() ->
                willStore.trigger(new EmbeddedChannel(), subStore, retainedStore));
    }
}
