package io.quickmq.mqtt.store;

import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.handler.codec.mqtt.*;
import io.quickmq.mqtt.subscription.SubscriberResult;
import io.quickmq.mqtt.subscription.SubscriptionStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 遗嘱消息存储与触发。百万连接优化：write/flush 分离，背压跳过，对象池归还。
 */
public final class WillStore {

    private static final Logger log = LoggerFactory.getLogger(WillStore.class);

    private final Map<Channel, WillMessage> channelToWill = new ConcurrentHashMap<>();

    public void store(Channel channel, MqttConnectMessage connect) {
        MqttConnectVariableHeader varHeader = connect.variableHeader();
        if (!varHeader.isWillFlag()) return;

        MqttConnectPayload payload = connect.payload();
        String topic = payload.willTopic();
        byte[] message = payload.willMessageInBytes();
        int qos = varHeader.willQos();
        boolean retain = varHeader.isWillRetain();

        if (topic == null || topic.isEmpty()) return;
        channelToWill.put(channel, new WillMessage(topic, message != null ? message : new byte[0], qos, retain));
    }

    public void clear(Channel channel) {
        channelToWill.remove(channel);
    }

    public void trigger(Channel channel, SubscriptionStore subscriptionStore, RetainedStore retainedStore) {
        WillMessage will = channelToWill.remove(channel);
        if (will == null) return;

        log.debug("触发遗嘱 topic={} qos={} retain={}", will.topic(), will.qos(), will.retain());

        MqttFixedHeader fixed = new MqttFixedHeader(
                MqttMessageType.PUBLISH, false, MqttQoS.valueOf(will.qos()), will.retain(), 0);
        MqttPublishVariableHeader varHeader = new MqttPublishVariableHeader(will.topic(), 0);
        MqttPublishMessage willPublish = new MqttPublishMessage(
                fixed, varHeader, Unpooled.wrappedBuffer(will.message()));

        if (retainedStore != null) {
            retainedStore.put(will.topic(), willPublish, will.retain());
        }

        MqttFixedHeader noRetainHeader = new MqttFixedHeader(
                MqttMessageType.PUBLISH, false, MqttQoS.valueOf(will.qos()), false, 0);
        MqttPublishMessage forwarded = new MqttPublishMessage(
                noRetainHeader, varHeader, willPublish.payload().retain());

        try (SubscriberResult subscribers = subscriptionStore.findSubscribers(will.topic())) {
            for (int i = 0, size = subscribers.size(); i < size; i++) {
                Channel sub = subscribers.get(i).channel();
                if (sub != channel && sub.isActive() && sub.isWritable()) {
                    sub.write(forwarded.retainedDuplicate(), sub.voidPromise());
                }
            }
            for (int i = 0, size = subscribers.size(); i < size; i++) {
                Channel sub = subscribers.get(i).channel();
                if (sub != channel && sub.isActive()) sub.flush();
            }
        }

        forwarded.release();
        willPublish.release();
    }

    private record WillMessage(String topic, byte[] message, int qos, boolean retain) {}
}
