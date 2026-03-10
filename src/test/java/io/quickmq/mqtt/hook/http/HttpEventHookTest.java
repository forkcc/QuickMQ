package io.quickmq.mqtt.hook.http;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.handler.codec.mqtt.MqttQoS;
import io.quickmq.mqtt.hook.MqttEventHook;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests HttpEventHook logic without mocking HttpClient.
 * Since HTTP calls are async fire-and-forget, failures are silently logged.
 */
class HttpEventHookTest {

    @Test
    void constructor_setsUrl() {
        HttpEventHook hook = new HttpEventHook("http://example.com/events", 3000, null, new ObjectMapper());
        assertTrue(hook.toString().contains("example.com"));
    }

    @Test
    void allEventMethods_withNullClient_doNotThrow() {
        HttpEventHook hook = new HttpEventHook("http://localhost:9999/events", 3000, null, new ObjectMapper());
        InetSocketAddress addr = new InetSocketAddress("10.0.0.1", 1234);

        assertDoesNotThrow(() -> hook.onClientConnected("c1", addr));
        assertDoesNotThrow(() -> hook.onClientDisconnected("c1", addr, MqttEventHook.DisconnectReason.NORMAL));
        assertDoesNotThrow(() -> hook.onMessagePublish("c1", "t", MqttQoS.AT_MOST_ONCE, false, 100));
        assertDoesNotThrow(() -> hook.onMessageDelivered("c1", "t", 5));
        assertDoesNotThrow(() -> hook.onClientSubscribe("c1", List.of("a/b")));
        assertDoesNotThrow(() -> hook.onClientUnsubscribe("c1", List.of("a/b")));
        assertDoesNotThrow(() -> hook.onConnectRejected("c1", addr, "reason"));
        assertDoesNotThrow(() -> hook.onClientKicked("c1", addr));
    }
}
