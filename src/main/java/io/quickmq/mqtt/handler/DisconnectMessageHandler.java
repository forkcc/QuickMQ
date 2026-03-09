package io.quickmq.mqtt.handler;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.mqtt.MqttMessage;
import io.netty.handler.codec.mqtt.MqttMessageType;
import io.quickmq.mqtt.subscription.SubscriptionStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DisconnectMessageHandler implements MqttMessageHandler {

    private static final Logger log = LoggerFactory.getLogger(DisconnectMessageHandler.class);
    private final SubscriptionStore subscriptionStore;

    public DisconnectMessageHandler(SubscriptionStore subscriptionStore) {
        this.subscriptionStore = subscriptionStore;
    }

    @Override
    public MqttMessageType messageType() {
        return MqttMessageType.DISCONNECT;
    }

    @Override
    public void handle(ChannelHandlerContext ctx, MqttMessage msg) {
        log.debug("DISCONNECT");
        subscriptionStore.removeChannel(ctx.channel());
        ctx.close();
    }
}
