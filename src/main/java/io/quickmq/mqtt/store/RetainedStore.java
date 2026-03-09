package io.quickmq.mqtt.store;

import io.netty.channel.Channel;
import io.netty.handler.codec.mqtt.MqttPublishMessage;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 保留消息存储，百万连接优化版。
 * <p>
 * deliverMatching 使用简易 Trie 按 topic 层级匹配 +/# 通配符，
 * 避免遍历全部 retained 消息（原 O(N) 退化为 O(层级 × 分支因子)）。
 */
public final class RetainedStore {

    private final TrieNode root = new TrieNode();

    public void put(String topic, MqttPublishMessage message, boolean retain) {
        if (!retain) return;
        String[] segments = topic.split("/", -1);
        if (message.payload().readableBytes() == 0) {
            TrieNode node = findNode(segments);
            if (node != null && node.message != null) {
                MqttPublishMessage old = node.message;
                node.message = null;
                old.release();
            }
        } else {
            TrieNode node = root;
            for (String seg : segments) {
                node = node.children.computeIfAbsent(seg, k -> new TrieNode());
            }
            MqttPublishMessage prev = node.message;
            node.message = message.retainedDuplicate();
            if (prev != null) prev.release();
        }
    }

    /**
     * 向 channel 下发匹配 topicFilter 的保留消息（新订阅时调用）。
     * 按 segment 逐层走 Trie，支持 + 和 # 通配符。
     */
    public void deliverMatching(String topicFilter, Channel channel) {
        if (!channel.isActive()) return;
        String[] segments = topicFilter.split("/", -1);
        deliverRecursive(root, segments, 0, channel);
    }

    private void deliverRecursive(TrieNode node, String[] segments, int depth, Channel channel) {
        if (!channel.isActive()) return;

        if (depth == segments.length) {
            if (node.message != null) {
                channel.writeAndFlush(node.message.retainedDuplicate());
            }
            return;
        }

        String seg = segments[depth];
        if ("#".equals(seg)) {
            deliverAll(node, channel);
            return;
        }
        if ("+".equals(seg)) {
            for (TrieNode child : node.children.values()) {
                deliverRecursive(child, segments, depth + 1, channel);
            }
            return;
        }
        TrieNode child = node.children.get(seg);
        if (child != null) {
            deliverRecursive(child, segments, depth + 1, channel);
        }
    }

    private void deliverAll(TrieNode node, Channel channel) {
        if (!channel.isActive()) return;
        if (node.message != null) {
            channel.writeAndFlush(node.message.retainedDuplicate());
        }
        for (TrieNode child : node.children.values()) {
            deliverAll(child, channel);
        }
    }

    private TrieNode findNode(String[] segments) {
        TrieNode node = root;
        for (String seg : segments) {
            node = node.children.get(seg);
            if (node == null) return null;
        }
        return node;
    }

    private static final class TrieNode {
        final Map<String, TrieNode> children = new ConcurrentHashMap<>();
        volatile MqttPublishMessage message;
    }
}
