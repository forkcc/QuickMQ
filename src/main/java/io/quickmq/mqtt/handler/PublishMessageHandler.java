package io.quickmq.mqtt.handler;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.mqtt.MqttMessage;
import io.netty.handler.codec.mqtt.MqttMessageType;
import io.netty.handler.codec.mqtt.MqttPublishMessage;
import io.quickmq.mqtt.subscription.SubscriptionEntry;
import io.quickmq.mqtt.subscription.SubscriptionStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class PublishMessageHandler implements MqttMessageHandler {

    private static final java.util.function.Predicate<SubscriptionEntry> ACTIVE = e -> e.channel().isActive();

    private static final Logger log = LoggerFactory.getLogger(PublishMessageHandler.class);
    private final SubscriptionStore subscriptionStore;

    public PublishMessageHandler(SubscriptionStore subscriptionStore) {
        this.subscriptionStore = subscriptionStore;
    }

    @Override
    public MqttMessageType messageType() {
        return MqttMessageType.PUBLISH;
    }

    @Override
    public void handle(ChannelHandlerContext ctx, MqttMessage msg) {
        MqttPublishMessage publish = (MqttPublishMessage) msg;
        String topic = publish.variableHeader().topicName();
        List<SubscriptionEntry> subscribers = subscriptionStore.findSubscribers(topic);
        log.debug("PUBLISH topic={} subscribers={}", topic, subscribers.size());
        Channel publisher = ctx.channel();
        subscribers.stream()
                .filter(e -> e.channel() != publisher)
                .filter(ACTIVE)
                .forEach(e -> e.channel().writeAndFlush(publish.retainedDuplicate()));
        publish.release();
    }
}
