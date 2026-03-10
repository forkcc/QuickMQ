package io.quickmq.mqtt.subscription;

import io.netty.channel.Channel;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.mqtt.MqttQoS;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class SubscriptionTrieTest {

    private SubscriptionTrie trie;

    @BeforeEach
    void setUp() {
        trie = new SubscriptionTrie();
    }

    @Test
    void subscribeAndCollect_exactMatch() {
        Channel ch = new EmbeddedChannel();
        trie.subscribe("a/b/c", ch, MqttQoS.AT_MOST_ONCE);

        Set<Channel> seen = new HashSet<>();
        List<SubscriptionEntry> out = new ArrayList<>();
        trie.collectSubscribers("a/b/c", seen, out);

        assertEquals(1, out.size());
        assertSame(ch, out.get(0).channel());
        assertEquals(MqttQoS.AT_MOST_ONCE, out.get(0).qos());
    }

    @Test
    void subscribeAndCollect_wildcardPlus() {
        Channel ch = new EmbeddedChannel();
        trie.subscribe("a/+/c", ch, MqttQoS.AT_LEAST_ONCE);

        Set<Channel> seen = new HashSet<>();
        List<SubscriptionEntry> out = new ArrayList<>();
        trie.collectSubscribers("a/b/c", seen, out);
        assertEquals(1, out.size());

        out.clear(); seen.clear();
        trie.collectSubscribers("a/x/c", seen, out);
        assertEquals(1, out.size());

        out.clear(); seen.clear();
        trie.collectSubscribers("a/b/d", seen, out);
        assertEquals(0, out.size());
    }

    @Test
    void subscribeAndCollect_wildcardHash() {
        Channel ch = new EmbeddedChannel();
        trie.subscribe("a/#", ch, MqttQoS.EXACTLY_ONCE);

        Set<Channel> seen = new HashSet<>();
        List<SubscriptionEntry> out = new ArrayList<>();
        trie.collectSubscribers("a/b/c/d", seen, out);
        assertEquals(1, out.size());

        out.clear(); seen.clear();
        trie.collectSubscribers("a", seen, out);
        assertEquals(1, out.size());
    }

    @Test
    void unsubscribe_removes() {
        Channel ch = new EmbeddedChannel();
        trie.subscribe("a/b", ch, MqttQoS.AT_MOST_ONCE);
        trie.unsubscribe("a/b", ch);

        Set<Channel> seen = new HashSet<>();
        List<SubscriptionEntry> out = new ArrayList<>();
        trie.collectSubscribers("a/b", seen, out);
        assertEquals(0, out.size());
    }

    @Test
    void unsubscribe_hashWildcard() {
        Channel ch = new EmbeddedChannel();
        trie.subscribe("sensor/#", ch, MqttQoS.AT_MOST_ONCE);
        trie.unsubscribe("sensor/#", ch);

        Set<Channel> seen = new HashSet<>();
        List<SubscriptionEntry> out = new ArrayList<>();
        trie.collectSubscribers("sensor/temp", seen, out);
        assertEquals(0, out.size());
    }

    @Test
    void multipleSubscribers_sameFilter() {
        Channel ch1 = new EmbeddedChannel();
        Channel ch2 = new EmbeddedChannel();
        trie.subscribe("a/b", ch1, MqttQoS.AT_MOST_ONCE);
        trie.subscribe("a/b", ch2, MqttQoS.AT_LEAST_ONCE);

        Set<Channel> seen = new HashSet<>();
        List<SubscriptionEntry> out = new ArrayList<>();
        trie.collectSubscribers("a/b", seen, out);
        assertEquals(2, out.size());
    }

    @Test
    void overlappingFilters_dedup() {
        Channel ch = new EmbeddedChannel();
        trie.subscribe("a/b", ch, MqttQoS.AT_MOST_ONCE);
        trie.subscribe("a/+", ch, MqttQoS.AT_LEAST_ONCE);

        Set<Channel> seen = new HashSet<>();
        List<SubscriptionEntry> out = new ArrayList<>();
        trie.collectSubscribers("a/b", seen, out);
        assertEquals(1, out.size(), "same channel should be dedup'd");
    }

    @Test
    void removeChannel_cleansAllFilters() {
        Channel ch = new EmbeddedChannel();
        trie.subscribe("a/b", ch, MqttQoS.AT_MOST_ONCE);
        trie.subscribe("x/y", ch, MqttQoS.AT_MOST_ONCE);
        trie.removeChannel(ch, List.of("a/b", "x/y"));

        Set<Channel> seen = new HashSet<>();
        List<SubscriptionEntry> out = new ArrayList<>();
        trie.collectSubscribers("a/b", seen, out);
        assertEquals(0, out.size());
        trie.collectSubscribers("x/y", seen, out);
        assertEquals(0, out.size());
    }

    @Test
    void inactiveChannel_notCollected() {
        EmbeddedChannel ch = new EmbeddedChannel();
        trie.subscribe("a/b", ch, MqttQoS.AT_MOST_ONCE);
        ch.close();

        Set<Channel> seen = new HashSet<>();
        List<SubscriptionEntry> out = new ArrayList<>();
        trie.collectSubscribers("a/b", seen, out);
        assertEquals(0, out.size());
    }

    @Test
    void qosUpgrade_merges() {
        Channel ch = new EmbeddedChannel();
        trie.subscribe("a/b", ch, MqttQoS.AT_MOST_ONCE);
        trie.subscribe("a/b", ch, MqttQoS.EXACTLY_ONCE);

        Set<Channel> seen = new HashSet<>();
        List<SubscriptionEntry> out = new ArrayList<>();
        trie.collectSubscribers("a/b", seen, out);
        assertEquals(1, out.size());
        assertEquals(MqttQoS.EXACTLY_ONCE, out.get(0).qos());
    }

    @Test
    void unsubscribe_nonExistent_noException() {
        assertDoesNotThrow(() -> trie.unsubscribe("no/such", new EmbeddedChannel()));
    }
}
