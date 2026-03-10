package io.quickmq.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class HookPropertiesTest {

    @Test
    void defaults() {
        HookProperties props = new HookProperties();
        assertEquals("", props.getAuthUrl());
        assertEquals("", props.getEventUrl());
        assertEquals(5000, props.getHttpTimeoutMs());
    }

    @Test
    void setAuthUrl_trimmed() {
        HookProperties props = new HookProperties();
        props.setAuthUrl("  http://example.com/auth  ");
        assertEquals("http://example.com/auth", props.getAuthUrl());
    }

    @Test
    void setAuthUrl_null_becomesEmpty() {
        HookProperties props = new HookProperties();
        props.setAuthUrl(null);
        assertEquals("", props.getAuthUrl());
    }

    @Test
    void setEventUrl_trimmed() {
        HookProperties props = new HookProperties();
        props.setEventUrl("  http://example.com/event  ");
        assertEquals("http://example.com/event", props.getEventUrl());
    }

    @Test
    void setEventUrl_null_becomesEmpty() {
        HookProperties props = new HookProperties();
        props.setEventUrl(null);
        assertEquals("", props.getEventUrl());
    }

    @Test
    void setHttpTimeoutMs_negativeClamped() {
        HookProperties props = new HookProperties();
        props.setHttpTimeoutMs(-1);
        assertEquals(5000, props.getHttpTimeoutMs());
        props.setHttpTimeoutMs(0);
        assertEquals(5000, props.getHttpTimeoutMs());
    }

    @Test
    void setHttpTimeoutMs_positive() {
        HookProperties props = new HookProperties();
        props.setHttpTimeoutMs(3000);
        assertEquals(3000, props.getHttpTimeoutMs());
    }
}
