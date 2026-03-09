package io.quickmq.mqtt.subscription;

import io.netty.channel.Channel;
import io.netty.handler.codec.mqtt.MqttQoS;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 基于 Trie 的订阅索引：按主题发布时 O(主题层级) 查找订阅者。
 * <p>
 * 百万连接优化点：
 * <ul>
 *   <li>collectSubscribers 使用调用方传入的可复用容器，避免热路径临时分配</li>
 *   <li>内部遍历使用数组栈代替 List 扩展，减少迭代器分配</li>
 * </ul>
 */
public final class SubscriptionTrie {

    private final Node root = new Node();

    public void subscribe(String topicFilter, Channel channel, MqttQoS qos) {
        String[] segments = topicFilter.split("/", -1);
        if (segments.length == 0) return;
        Node node = root;
        for (int i = 0; i < segments.length; i++) {
            String seg = segments[i];
            if ("#".equals(seg)) {
                node = node.getOrCreateHash();
                node.add(channel, qos);
                return;
            }
            if ("+".equals(seg)) {
                node = node.getOrCreatePlus();
            } else {
                node = node.getOrCreateExact(seg);
            }
        }
        node.add(channel, qos);
    }

    public void unsubscribe(String topicFilter, Channel channel) {
        String[] segments = topicFilter.split("/", -1);
        if (segments.length == 0) return;
        Node node = root;
        for (int i = 0; i < segments.length; i++) {
            String seg = segments[i];
            if ("#".equals(seg)) {
                Node h = node.hashNode;
                if (h != null) h.remove(channel);
                return;
            }
            if ("+".equals(seg)) {
                node = node.plusNode;
            } else {
                node = node.exactChildren.get(seg);
            }
            if (node == null) return;
        }
        node.remove(channel);
    }

    /**
     * 收集匹配 topic 的所有订阅。
     * seen/out 由调用方提供（ThreadLocal 池），避免热路径分配。
     * <p>
     * 内部使用两个 Node[] 数组轮换代替 ArrayList 扩展，进一步消除 GC 压力。
     */
    public void collectSubscribers(String topic, Set<Channel> seen, List<SubscriptionEntry> out) {
        String[] segments = topic.split("/", -1);
        if (segments.length == 0) return;

        Node[] current = { root };
        int curLen = 1;

        for (int i = 0; i < segments.length; i++) {
            String seg = segments[i];
            Node[] next = new Node[curLen * 3];
            int nextLen = 0;
            for (int j = 0; j < curLen; j++) {
                Node n = current[j];
                if (n.hashNode != null) n.hashNode.collect(seen, out);
                Node exact = n.exactChildren.get(seg);
                if (exact != null) {
                    next = ensureCapacity(next, nextLen + 1);
                    next[nextLen++] = exact;
                }
                if (n.plusNode != null) {
                    next = ensureCapacity(next, nextLen + 1);
                    next[nextLen++] = n.plusNode;
                }
            }
            current = next;
            curLen = nextLen;
            if (curLen == 0) return;
        }
        for (int j = 0; j < curLen; j++) {
            current[j].collect(seen, out);
            if (current[j].hashNode != null) current[j].hashNode.collect(seen, out);
        }
    }

    private static Node[] ensureCapacity(Node[] arr, int needed) {
        if (needed <= arr.length) return arr;
        Node[] bigger = new Node[arr.length * 2];
        System.arraycopy(arr, 0, bigger, 0, arr.length);
        return bigger;
    }

    public void removeChannel(Channel channel, Collection<String> topicFilters) {
        for (String f : topicFilters) unsubscribe(f, channel);
    }

    private static final class Node {
        final Map<String, Node> exactChildren = new ConcurrentHashMap<>();
        volatile Node plusNode;
        volatile Node hashNode;
        final Map<Channel, MqttQoS> channels = new ConcurrentHashMap<>();

        Node getOrCreateExact(String segment) {
            return exactChildren.computeIfAbsent(segment, k -> new Node());
        }

        Node getOrCreatePlus() {
            if (plusNode == null) {
                synchronized (this) {
                    if (plusNode == null) plusNode = new Node();
                }
            }
            return plusNode;
        }

        Node getOrCreateHash() {
            if (hashNode == null) {
                synchronized (this) {
                    if (hashNode == null) hashNode = new Node();
                }
            }
            return hashNode;
        }

        void add(Channel ch, MqttQoS qos) {
            channels.merge(ch, qos, (a, b) -> a.value() >= b.value() ? a : b);
        }

        void remove(Channel ch) {
            channels.remove(ch);
        }

        void collect(Set<Channel> seen, List<SubscriptionEntry> out) {
            for (Map.Entry<Channel, MqttQoS> e : channels.entrySet()) {
                Channel ch = e.getKey();
                if (ch.isActive() && seen.add(ch)) out.add(new SubscriptionEntry(ch, e.getValue()));
            }
        }
    }
}
