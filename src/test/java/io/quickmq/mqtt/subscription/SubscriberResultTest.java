package io.quickmq.mqtt.subscription;

import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.mqtt.MqttQoS;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SubscriberResultTest {

    @Test
    void acquire_returnsNewInstance() {
        SubscriberResult r = SubscriberResult.acquire();
        assertNotNull(r);
        assertEquals(0, r.size());
        r.close();
    }

    @Test
    void close_clearsAndRecycles() {
        SubscriberResult r = SubscriberResult.acquire();
        r.seen.add(new EmbeddedChannel());
        r.entries.add(new SubscriptionEntry(new EmbeddedChannel(), MqttQoS.AT_MOST_ONCE));
        assertEquals(1, r.size());

        r.close();

        SubscriberResult r2 = SubscriberResult.acquire();
        assertSame(r, r2, "Recycler should reuse the same object");
        assertEquals(0, r2.size());
        r2.close();
    }

    @Test
    void get_returnsCorrectEntry() {
        SubscriberResult r = SubscriberResult.acquire();
        EmbeddedChannel ch = new EmbeddedChannel();
        SubscriptionEntry entry = new SubscriptionEntry(ch, MqttQoS.EXACTLY_ONCE);
        r.entries.add(entry);

        assertEquals(1, r.size());
        assertSame(entry, r.get(0));
        r.close();
    }

    @Test
    void autoCloseable_worksWithTryWithResources() {
        SubscriberResult captured;
        try (SubscriberResult r = SubscriberResult.acquire()) {
            r.entries.add(new SubscriptionEntry(new EmbeddedChannel(), MqttQoS.AT_MOST_ONCE));
            captured = r;
        }
        SubscriberResult r2 = SubscriberResult.acquire();
        assertSame(captured, r2);
        assertEquals(0, r2.size());
        r2.close();
    }
}
