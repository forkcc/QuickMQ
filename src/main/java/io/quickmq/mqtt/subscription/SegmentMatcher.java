package io.quickmq.mqtt.subscription;

/**
 * 主题过滤符单字符匹配策略：根据当前字符推进 filter/topic 下标。
 */
@FunctionalInterface
interface SegmentMatcher {

    /**
     * @return 推进后的 (filterIndex, topicIndex)，若匹配失败返回 null。
     */
    int[] advance(String filter, String topic, int fi, int ti);
}
