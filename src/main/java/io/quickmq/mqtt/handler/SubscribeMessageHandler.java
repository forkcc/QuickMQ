package io.quickmq.mqtt.handler;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.mqtt.*;
import io.quickmq.mqtt.ChannelAttributes;
import io.quickmq.mqtt.MqttResponses;
import io.quickmq.mqtt.hook.HookManager;
import io.quickmq.mqtt.store.RetainedStore;
import io.quickmq.mqtt.subscription.SubscriptionStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class SubscribeMessageHandler implements MqttMessageHandler {

    private static final Logger log = LoggerFactory.getLogger(SubscribeMessageHandler.class);
    private final SubscriptionStore subscriptionStore;
    private final RetainedStore retainedStore;
    private final HookManager hookManager;

    public SubscribeMessageHandler(SubscriptionStore subscriptionStore, RetainedStore retainedStore, HookManager hookManager) {
        this.subscriptionStore = subscriptionStore;
        this.retainedStore = retainedStore;
        this.hookManager = hookManager;
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
        List<String> filters = new ArrayList<>(topicSubscriptions.size());
        for (MqttTopicSubscription sub : topicSubscriptions) {
            String filter = sub.topicFilter();
            MqttQoS qos = sub.qualityOfService();
            subscriptionStore.subscribe(ctx.channel(), filter, qos);
            grantedQos.add(qos.value());
            filters.add(filter);
            if (retainedStore != null) {
                retainedStore.deliverMatching(filter, ctx.channel());
            }
            log.debug("SUBSCRIBE filter={} qos={}", filter, qos);
        }
        ctx.writeAndFlush(MqttResponses.subAck(messageId, grantedQos));

        if (hookManager != null) {
            String clientId = ctx.channel().attr(ChannelAttributes.CLIENT_ID).get();
            hookManager.fireClientSubscribe(clientId, filters);
        }
    }
}
