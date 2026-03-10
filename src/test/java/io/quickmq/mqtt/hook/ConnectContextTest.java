package io.quickmq.mqtt.hook;

import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;

import static org.junit.jupiter.api.Assertions.*;

class ConnectContextTest {

    @Test
    void recordFieldsAccessible() {
        InetSocketAddress addr = new InetSocketAddress("192.168.1.100", 54321);
        byte[] pwd = "secret".getBytes();
        ConnectContext ctx = new ConnectContext("client-1", "admin", pwd, addr, 4, true);

        assertEquals("client-1", ctx.clientId());
        assertEquals("admin", ctx.username());
        assertArrayEquals(pwd, ctx.password());
        assertEquals(addr, ctx.remoteAddress());
        assertEquals(4, ctx.protocolVersion());
        assertTrue(ctx.cleanSession());
    }

    @Test
    void nullFieldsAllowed() {
        ConnectContext ctx = new ConnectContext(null, null, null, null, 4, false);
        assertNull(ctx.clientId());
        assertNull(ctx.username());
        assertNull(ctx.password());
        assertNull(ctx.remoteAddress());
    }
}
