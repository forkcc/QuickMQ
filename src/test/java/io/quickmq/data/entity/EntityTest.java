package io.quickmq.data.entity;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class EntityTest {

    @Test
    void clientSessionEntity_gettersSetters() {
        ClientSessionEntity e = new ClientSessionEntity();
        e.setId(1L);
        e.setClientId("c1");
        e.setUsername("admin");
        e.setCleanSession(false);
        Instant now = Instant.now();
        e.setCreatedAt(now);
        e.setLastSeenAt(now);

        assertEquals(1L, e.getId());
        assertEquals("c1", e.getClientId());
        assertEquals("admin", e.getUsername());
        assertFalse(e.isCleanSession());
        assertEquals(now, e.getCreatedAt());
        assertEquals(now, e.getLastSeenAt());
    }

    @Test
    void subscriptionEntity_gettersSetters() {
        SubscriptionEntity e = new SubscriptionEntity();
        e.setId(2L);
        e.setClientId("c1");
        e.setTopicFilter("sensor/#");
        e.setQos(1);

        assertEquals(2L, e.getId());
        assertEquals("c1", e.getClientId());
        assertEquals("sensor/#", e.getTopicFilter());
        assertEquals(1, e.getQos());
    }

    @Test
    void retainedMessageEntity_gettersSetters() {
        RetainedMessageEntity e = new RetainedMessageEntity();
        e.setId(3L);
        e.setTopic("a/b");
        byte[] payload = "hello".getBytes();
        e.setPayload(payload);
        e.setQos(2);
        Instant now = Instant.now();
        e.setUpdatedAt(now);

        assertEquals(3L, e.getId());
        assertEquals("a/b", e.getTopic());
        assertArrayEquals(payload, e.getPayload());
        assertEquals(2, e.getQos());
        assertEquals(now, e.getUpdatedAt());
    }
}
