package io.quickmq.mqtt.handler;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.mqtt.MqttMessage;
import io.netty.handler.codec.mqtt.MqttMessageType;
import io.netty.handler.codec.mqtt.MqttUnsubscribeMessage;
import io.quickmq.mqtt.MqttResponses;
import io.quickmq.mqtt.subscription.SubscriptionStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class UnsubscribeMessageHandler implements MqttMessageHandler {

    private static final Logger log = LoggerFactory.getLogger(UnsubscribeMessageHandler.class);
    private final SubscriptionStore subscriptionStore;

    public UnsubscribeMessageHandler(SubscriptionStore subscriptionStore) {
        this.subscriptionStore = subscriptionStore;
    }

    @Override
    public MqttMessageType messageType() {
        return MqttMessageType.UNSUBSCRIBE;
    }

    @Override
    public void handle(ChannelHandlerContext ctx, MqttMessage msg) {
        MqttUnsubscribeMessage unsubscribe = (MqttUnsubscribeMessage) msg;
        int messageId = unsubscribe.variableHeader().messageId();
        List<String> topicFilters = unsubscribe.payload().topics();
        for (String filter : topicFilters) {
            subscriptionStore.unsubscribe(ctx.channel(), filter);
            log.debug("UNSUBSCRIBE filter={}", filter);
        }
        ctx.writeAndFlush(MqttResponses.unsubAck(messageId));
    }
}
