package io.quickmq.mqtt.hook;

import io.netty.handler.codec.mqtt.MqttConnectReturnCode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AuthResultTest {

    @Test
    void accept_returnsAccepted() {
        AuthResult result = AuthResult.accept();
        assertTrue(result.accepted());
        assertEquals(MqttConnectReturnCode.CONNECTION_ACCEPTED, result.returnCode());
        assertNull(result.reason());
    }

    @Test
    void reject_returnsNotAuthorized() {
        AuthResult result = AuthResult.reject("forbidden");
        assertFalse(result.accepted());
        assertEquals(MqttConnectReturnCode.CONNECTION_REFUSED_NOT_AUTHORIZED, result.returnCode());
        assertEquals("forbidden", result.reason());
    }

    @Test
    void badCredentials_returnsBadUserPassword() {
        AuthResult result = AuthResult.badCredentials("wrong password");
        assertFalse(result.accepted());
        assertEquals(MqttConnectReturnCode.CONNECTION_REFUSED_BAD_USER_NAME_OR_PASSWORD, result.returnCode());
        assertEquals("wrong password", result.reason());
    }

    @Test
    void reject_withCustomCode() {
        AuthResult result = AuthResult.reject(
                MqttConnectReturnCode.CONNECTION_REFUSED_SERVER_UNAVAILABLE, "maintenance");
        assertFalse(result.accepted());
        assertEquals(MqttConnectReturnCode.CONNECTION_REFUSED_SERVER_UNAVAILABLE, result.returnCode());
        assertEquals("maintenance", result.reason());
    }
}
