package io.quickmq.mqtt.subscription;

import io.netty.channel.Channel;
import io.netty.handler.codec.mqtt.MqttQoS;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 内存订阅表：Trie 索引 + Channel->Filters 反向表。
 * <p>
 * findSubscribers 通过 {@link SubscriberResult} 的 Netty Recycler 对象池复用 Set/List，
 * 消除每次 PUBLISH 的临时对象分配。调用方必须 try-with-resources 归还。
 */
public class SubscriptionStore {

    private final SubscriptionTrie trie = new SubscriptionTrie();
    private final Map<Channel, Set<String>> channelToFilters = new ConcurrentHashMap<>();

    public void subscribe(Channel channel, String topicFilter, MqttQoS qos) {
        trie.subscribe(topicFilter, channel, qos);
        channelToFilters.computeIfAbsent(channel, k -> ConcurrentHashMap.newKeySet()).add(topicFilter);
    }

    public void unsubscribe(Channel channel, String topicFilter) {
        trie.unsubscribe(topicFilter, channel);
        Set<String> filters = channelToFilters.get(channel);
        if (filters != null) filters.remove(topicFilter);
    }

    /**
     * 查找匹配 topic 的所有订阅者，从 Recycler 对象池借出 {@link SubscriberResult}。
     * <b>调用方必须用 try-with-resources 归还。</b>
     */
    public SubscriberResult findSubscribers(String topic) {
        SubscriberResult result = SubscriberResult.acquire();
        trie.collectSubscribers(topic, result.seen, result.entries);
        return result;
    }

    public void removeChannel(Channel channel) {
        Set<String> filters = channelToFilters.remove(channel);
        if (filters != null && !filters.isEmpty()) trie.removeChannel(channel, filters);
    }
}
