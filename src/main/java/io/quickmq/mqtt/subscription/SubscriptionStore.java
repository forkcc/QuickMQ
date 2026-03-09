package io.quickmq.mqtt.subscription;

import io.netty.channel.Channel;
import io.quickmq.mqtt.topic.MqttTopicMatcher;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 内存订阅表：主题过滤符 -> 订阅列表；连接断开时按 Channel 清理。
 * 使用 Optional / Stream 减少 null 与分支。
 */
public class SubscriptionStore {

    private final Map<String, Set<SubscriptionEntry>> filterToSubs = new ConcurrentHashMap<>();
    private final Map<Channel, Set<String>> channelToFilters = new ConcurrentHashMap<>();

    public void subscribe(Channel channel, String topicFilter, io.netty.handler.codec.mqtt.MqttQoS qos) {
        filterToSubs.computeIfAbsent(topicFilter, k -> ConcurrentHashMap.newKeySet()).add(new SubscriptionEntry(channel, qos));
        channelToFilters.computeIfAbsent(channel, k -> ConcurrentHashMap.newKeySet()).add(topicFilter);
    }

    public void unsubscribe(Channel channel, String topicFilter) {
        Optional.ofNullable(filterToSubs.get(topicFilter)).ifPresent(subs -> {
            subs.removeIf(e -> e.channel() == channel);
            if (subs.isEmpty()) filterToSubs.remove(topicFilter);
        });
        Optional.ofNullable(channelToFilters.get(channel)).ifPresent(f -> f.remove(topicFilter));
    }

    /**
     * 返回所有匹配 topic 的订阅（每 Channel 仅一次，QoS 取该 Channel 下匹配订阅的最大值）。
     */
    public List<SubscriptionEntry> findSubscribers(String topic) {
        Set<Channel> seen = new HashSet<>();
        List<SubscriptionEntry> out = new ArrayList<>();
        filterToSubs.entrySet().stream()
                .filter(e -> MqttTopicMatcher.matches(e.getKey(), topic))
                .flatMap(e -> e.getValue().stream())
                .filter(s -> s.channel().isActive() && seen.add(s.channel()))
                .forEach(out::add);
        return out;
    }

    public void removeChannel(Channel channel) {
        Optional.ofNullable(channelToFilters.remove(channel)).ifPresent(filters ->
                filters.forEach(f -> Optional.ofNullable(filterToSubs.get(f)).ifPresent(subs -> {
                    subs.removeIf(e -> e.channel() == channel);
                    if (subs.isEmpty()) filterToSubs.remove(f);
                }))
        );
    }
}
