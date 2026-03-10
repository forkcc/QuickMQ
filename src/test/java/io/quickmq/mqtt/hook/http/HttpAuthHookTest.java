package io.quickmq.mqtt.hook.http;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.quickmq.mqtt.hook.AuthResult;
import io.quickmq.mqtt.hook.ConnectContext;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests HttpAuthHook logic without mocking HttpClient (not mockable in Java 21 module system).
 * Verifies construction and toString; actual HTTP behavior tested via integration tests.
 */
class HttpAuthHookTest {

    @Test
    void constructor_setsUrl() {
        HttpAuthHook hook = new HttpAuthHook("http://example.com/auth", 5000, null, new ObjectMapper());
        assertTrue(hook.toString().contains("example.com"));
    }

    @Test
    void authenticate_withNullHttpClient_rejectsGracefully() {
        HttpAuthHook hook = new HttpAuthHook("http://localhost:9999/auth", 5000, null, new ObjectMapper());
        ConnectContext ctx = new ConnectContext("c1", "admin", "pass".getBytes(),
                new InetSocketAddress("127.0.0.1", 1234), 4, true);

        AuthResult result = hook.authenticate(ctx);
        assertFalse(result.accepted(), "Should reject when HTTP call fails");
        assertNotNull(result.reason());
    }

    @Test
    void authenticate_withNullPassword_handlesGracefully() {
        HttpAuthHook hook = new HttpAuthHook("http://localhost:9999/auth", 5000, null, new ObjectMapper());
        ConnectContext ctx = new ConnectContext("c1", null, null, null, 4, true);

        AuthResult result = hook.authenticate(ctx);
        assertFalse(result.accepted());
    }
}
