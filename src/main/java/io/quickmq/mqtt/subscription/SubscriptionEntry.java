package io.quickmq.mqtt.subscription;

import io.netty.channel.Channel;
import io.netty.handler.codec.mqtt.MqttQoS;

/**
 * 单条订阅：某 Channel 以指定 QoS 订阅某主题过滤符。
 */
public record SubscriptionEntry(Channel channel, MqttQoS qos) {}
