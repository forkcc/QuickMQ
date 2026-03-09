package io.quickmq.mqtt.handler;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.mqtt.MqttMessage;
import io.netty.handler.codec.mqtt.MqttMessageType;
import io.netty.handler.codec.mqtt.MqttQoS;
import io.netty.handler.codec.mqtt.MqttSubscribeMessage;
import io.netty.handler.codec.mqtt.MqttTopicSubscription;
import io.quickmq.mqtt.MqttResponses;
import io.quickmq.mqtt.subscription.SubscriptionStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class SubscribeMessageHandler implements MqttMessageHandler {

    private static final Logger log = LoggerFactory.getLogger(SubscribeMessageHandler.class);
    private final SubscriptionStore subscriptionStore;

    public SubscribeMessageHandler(SubscriptionStore subscriptionStore) {
        this.subscriptionStore = subscriptionStore;
    }

    @Override
    public MqttMessageType messageType() {
        return MqttMessageType.SUBSCRIBE;
    }

    @Override
    public void handle(ChannelHandlerContext ctx, MqttMessage msg) {
        MqttSubscribeMessage subscribe = (MqttSubscribeMessage) msg;
        int messageId = subscribe.variableHeader().messageId();
        List<MqttTopicSubscription> topicSubscriptions = subscribe.payload().topicSubscriptions();
        List<Integer> grantedQos = new ArrayList<>(topicSubscriptions.size());
        for (MqttTopicSubscription sub : topicSubscriptions) {
            String filter = sub.topicFilter();
            MqttQoS qos = sub.qualityOfService();
            subscriptionStore.subscribe(ctx.channel(), filter, qos);
            grantedQos.add(qos.value());
            log.debug("SUBSCRIBE filter={} qos={}", filter, qos);
        }
        ctx.writeAndFlush(MqttResponses.subAck(messageId, grantedQos));
    }
}
