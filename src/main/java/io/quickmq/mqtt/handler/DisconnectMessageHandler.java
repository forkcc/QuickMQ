package io.quickmq.mqtt.handler;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.mqtt.MqttMessage;
import io.netty.handler.codec.mqtt.MqttMessageType;
import io.quickmq.mqtt.hook.HookManager;
import io.quickmq.mqtt.subscription.SubscriptionStore;
import io.quickmq.mqtt.store.WillStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DisconnectMessageHandler implements MqttMessageHandler {

    private static final Logger log = LoggerFactory.getLogger(DisconnectMessageHandler.class);
    private final SubscriptionStore subscriptionStore;
    private final WillStore willStore;
    private final HookManager hookManager;

    public DisconnectMessageHandler(SubscriptionStore subscriptionStore, WillStore willStore, HookManager hookManager) {
        this.subscriptionStore = subscriptionStore;
        this.willStore = willStore;
        this.hookManager = hookManager;
    }

    @Override
    public MqttMessageType messageType() {
        return MqttMessageType.DISCONNECT;
    }

    @Override
    public void handle(ChannelHandlerContext ctx, MqttMessage msg) {
        log.debug("DISCONNECT");
        if (willStore != null) willStore.clear(ctx.channel());
        subscriptionStore.removeChannel(ctx.channel());
        ctx.close();
    }
}
