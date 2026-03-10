package io.quickmq.mqtt.hook;

import io.netty.handler.codec.mqtt.MqttQoS;
import io.quickmq.config.HookProperties;
import io.quickmq.config.MqttProperties;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class HookManagerTest {

    private MqttProperties defaultProps() {
        MqttProperties props = new MqttProperties();
        props.setHooks(new HookProperties());
        return props;
    }

    @Test
    void defaultAuth_noBean_noUrl_allowsAll() {
        HookManager hm = new HookManager(defaultProps(), null, null);
        ConnectContext ctx = new ConnectContext("c1", "user", null,
                new InetSocketAddress("127.0.0.1", 1234), 4, true);

        AuthResult result = hm.authenticate(ctx);
        assertTrue(result.accepted());
    }

    @Test
    void beanAuth_usedWhenProvided() {
        MqttAuthHook customAuth = ctx -> AuthResult.reject("custom reject");
        HookManager hm = new HookManager(defaultProps(), customAuth, null);
        ConnectContext ctx = new ConnectContext("c1", null, null, null, 4, true);

        AuthResult result = hm.authenticate(ctx);
        assertFalse(result.accepted());
        assertEquals("custom reject", result.reason());
    }

    @Test
    void authException_rejectsConnection() {
        MqttAuthHook badHook = ctx -> { throw new RuntimeException("boom"); };
        HookManager hm = new HookManager(defaultProps(), badHook, null);
        ConnectContext ctx = new ConnectContext("c1", null, null, null, 4, true);

        AuthResult result = hm.authenticate(ctx);
        assertFalse(result.accepted());
        assertEquals("internal auth error", result.reason());
    }

    @Test
    void noEventHooks_hasEventHooksFalse() {
        HookManager hm = new HookManager(defaultProps(), null, null);
        assertFalse(hm.hasEventHooks());
    }

    @Test
    void beanEventHooks_registered() {
        AtomicBoolean called = new AtomicBoolean(false);
        MqttEventHook eventHook = new MqttEventHook() {
            @Override
            public void onClientConnected(String clientId, InetSocketAddress remoteAddress) {
                called.set(true);
            }
        };

        HookManager hm = new HookManager(defaultProps(), null, List.of(eventHook));
        assertTrue(hm.hasEventHooks());

        hm.fireClientConnected("c1", new InetSocketAddress("127.0.0.1", 1234));
        assertTrue(called.get());
    }

    @Test
    void multipleEventHooks_allCalled() {
        AtomicInteger count = new AtomicInteger(0);
        MqttEventHook hook1 = new MqttEventHook() {
            @Override
            public void onClientConnected(String clientId, InetSocketAddress remoteAddress) {
                count.incrementAndGet();
            }
        };
        MqttEventHook hook2 = new MqttEventHook() {
            @Override
            public void onClientConnected(String clientId, InetSocketAddress remoteAddress) {
                count.incrementAndGet();
            }
        };

        HookManager hm = new HookManager(defaultProps(), null, List.of(hook1, hook2));
        hm.fireClientConnected("c1", new InetSocketAddress("127.0.0.1", 1234));
        assertEquals(2, count.get());
    }

    @Test
    void eventHookException_doesNotPropagateAndOthersCalled() {
        AtomicBoolean secondCalled = new AtomicBoolean(false);
        MqttEventHook badHook = new MqttEventHook() {
            @Override
            public void onClientConnected(String clientId, InetSocketAddress remoteAddress) {
                throw new RuntimeException("boom");
            }
        };
        MqttEventHook goodHook = new MqttEventHook() {
            @Override
            public void onClientConnected(String clientId, InetSocketAddress remoteAddress) {
                secondCalled.set(true);
            }
        };

        HookManager hm = new HookManager(defaultProps(), null, List.of(badHook, goodHook));
        assertDoesNotThrow(() -> hm.fireClientConnected("c1", new InetSocketAddress("127.0.0.1", 1234)));
        assertTrue(secondCalled.get());
    }

    @Test
    void fireAllEventTypes_noException() {
        AtomicInteger count = new AtomicInteger(0);
        MqttEventHook hook = new MqttEventHook() {
            @Override public void onClientConnected(String c, InetSocketAddress r) { count.incrementAndGet(); }
            @Override public void onClientDisconnected(String c, InetSocketAddress r, DisconnectReason reason) { count.incrementAndGet(); }
            @Override public void onMessagePublish(String c, String t, MqttQoS q, boolean retain, int ps) { count.incrementAndGet(); }
            @Override public void onMessageDelivered(String c, String t, int sc) { count.incrementAndGet(); }
            @Override public void onClientSubscribe(String c, List<String> f) { count.incrementAndGet(); }
            @Override public void onClientUnsubscribe(String c, List<String> f) { count.incrementAndGet(); }
            @Override public void onConnectRejected(String c, InetSocketAddress r, String reason) { count.incrementAndGet(); }
            @Override public void onClientKicked(String c, InetSocketAddress r) { count.incrementAndGet(); }
        };

        InetSocketAddress addr = new InetSocketAddress("127.0.0.1", 1234);
        HookManager hm = new HookManager(defaultProps(), null, List.of(hook));
        hm.fireClientConnected("c1", addr);
        hm.fireClientDisconnected("c1", addr, MqttEventHook.DisconnectReason.NORMAL);
        hm.fireMessagePublish("c1", "t", MqttQoS.AT_MOST_ONCE, false, 100);
        hm.fireMessageDelivered("c1", "t", 5);
        hm.fireClientSubscribe("c1", List.of("a/b"));
        hm.fireClientUnsubscribe("c1", List.of("a/b"));
        hm.fireConnectRejected("c1", addr, "test");
        hm.fireClientKicked("c1", addr);

        assertEquals(8, count.get());
    }

    @Test
    void noEventHooks_fireDoesNothing() {
        HookManager hm = new HookManager(defaultProps(), null, null);
        assertDoesNotThrow(() -> {
            hm.fireClientConnected("c1", new InetSocketAddress("127.0.0.1", 1234));
            hm.fireClientDisconnected("c1", null, MqttEventHook.DisconnectReason.IDLE_TIMEOUT);
            hm.fireMessagePublish("c1", "t", MqttQoS.AT_MOST_ONCE, false, 0);
            hm.fireMessageDelivered("c1", "t", 0);
            hm.fireClientSubscribe("c1", List.of());
            hm.fireClientUnsubscribe("c1", List.of());
            hm.fireConnectRejected("c1", null, "x");
            hm.fireClientKicked("c1", null);
        });
    }
}
