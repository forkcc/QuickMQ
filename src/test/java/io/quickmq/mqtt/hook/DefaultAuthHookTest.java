package io.quickmq.mqtt.hook;

import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;

import static org.junit.jupiter.api.Assertions.*;

class DefaultAuthHookTest {

    @Test
    void authenticate_alwaysAccepts() {
        DefaultAuthHook hook = new DefaultAuthHook();
        ConnectContext ctx = new ConnectContext(
                "client-1", "user", "pass".getBytes(),
                new InetSocketAddress("127.0.0.1", 1234), 4, true);

        AuthResult result = hook.authenticate(ctx);
        assertTrue(result.accepted());
    }

    @Test
    void authenticate_acceptsNullFields() {
        DefaultAuthHook hook = new DefaultAuthHook();
        ConnectContext ctx = new ConnectContext(null, null, null, null, 4, false);

        AuthResult result = hook.authenticate(ctx);
        assertTrue(result.accepted());
    }
}
