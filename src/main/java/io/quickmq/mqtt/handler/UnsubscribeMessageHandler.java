package io.quickmq.mqtt.handler;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.mqtt.MqttMessage;
import io.netty.handler.codec.mqtt.MqttMessageType;
import io.netty.handler.codec.mqtt.MqttUnsubscribeMessage;
import io.quickmq.data.PersistenceService;
import io.quickmq.mqtt.ChannelAttributes;
import io.quickmq.mqtt.MqttResponses;
import io.quickmq.mqtt.hook.HookManager;
import io.quickmq.mqtt.subscription.SubscriptionStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class UnsubscribeMessageHandler implements MqttMessageHandler {

    private static final Logger log = LoggerFactory.getLogger(UnsubscribeMessageHandler.class);
    private final SubscriptionStore subscriptionStore;
    private final HookManager hookManager;
    private final PersistenceService persistence;

    public UnsubscribeMessageHandler(SubscriptionStore subscriptionStore, HookManager hookManager,
                                     PersistenceService persistence) {
        this.subscriptionStore = subscriptionStore;
        this.hookManager = hookManager;
        this.persistence = persistence;
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
        String clientId = ctx.channel().attr(ChannelAttributes.CLIENT_ID).get();

        for (String filter : topicFilters) {
            subscriptionStore.unsubscribe(ctx.channel(), filter);
            if (persistence != null && clientId != null) {
                persistence.deleteSubscriptionAsync(clientId, filter);
            }
            log.debug("UNSUBSCRIBE filter={}", filter);
        }
        ctx.writeAndFlush(MqttResponses.unsubAck(messageId));

        if (hookManager != null) {
            hookManager.fireClientUnsubscribe(clientId, topicFilters);
        }
    }
}
