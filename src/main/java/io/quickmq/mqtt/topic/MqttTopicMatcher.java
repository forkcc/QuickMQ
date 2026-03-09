package io.quickmq.mqtt.topic;

import java.util.Map;

/**
 * MQTT 主题与主题过滤符匹配：支持 +（单层）、#（多层通配）。
 * 使用策略表驱动，减少分支。
 */
public final class MqttTopicMatcher {

    private static final Map<Character, SegmentMatcher> MATCHERS = Map.of(
            '+', (filter, topic, fi, ti) -> {
                int tlen = topic.length();
                while (ti < tlen && topic.charAt(ti) != '/') ti++;
                return new int[]{fi + 1, ti};
            },
            '#', (filter, topic, fi, ti) -> new int[]{filter.length(), topic.length()}
    );
    private static final SegmentMatcher EXACT = (f, t, fi, ti) -> matchExact(f.charAt(fi), f, t, fi, ti);

    private MqttTopicMatcher() {}

    private static int[] matchExact(char ch, String filter, String topic, int fi, int ti) {
        if (ti < topic.length() && topic.charAt(ti) == ch) {
            return new int[]{fi + 1, ti + 1};
        }
        return null;
    }

    private static SegmentMatcher forChar(char c) {
        return MATCHERS.getOrDefault(c, EXACT);
    }

    /**
     * 判断发布主题 topic 是否匹配订阅过滤符 filter。
     */
    public static boolean matches(String filter, String topic) {
        int fi = 0, ti = 0;
        int flen = filter.length(), tlen = topic.length();
        while (fi < flen && ti < tlen) {
            SegmentMatcher m = forChar(filter.charAt(fi));
            int[] next = m.advance(filter, topic, fi, ti);
            if (next == null) return false;
            fi = next[0];
            ti = next[1];
        }
        return (fi == flen && ti == tlen) || (fi == flen - 1 && filter.charAt(fi) == '#');
    }
}
