package io.quickmq.mqtt.handler;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.mqtt.*;
import io.quickmq.data.PersistenceService;
import io.quickmq.mqtt.ChannelAttributes;
import io.quickmq.mqtt.MqttResponses;
import io.quickmq.mqtt.hook.HookManager;
import io.quickmq.mqtt.store.RetainedStore;
import io.quickmq.mqtt.subscription.SubscriberResult;
import io.quickmq.mqtt.subscription.SubscriptionStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * PUBLISH 处理器：write/flush 分离、背压检查、对象池、保留消息持久化。
 */
public class PublishMessageHandler implements MqttMessageHandler {

    private static final Logger log = LoggerFactory.getLogger(PublishMessageHandler.class);
    private final SubscriptionStore subscriptionStore;
    private final RetainedStore retainedStore;
    private final HookManager hookManager;
    private final PersistenceService persistence;

    public PublishMessageHandler(SubscriptionStore subscriptionStore, RetainedStore retainedStore,
                                 HookManager hookManager, PersistenceService persistence) {
        this.subscriptionStore = subscriptionStore;
        this.retainedStore = retainedStore;
        this.hookManager = hookManager;
        this.persistence = persistence;
    }

    @Override
    public MqttMessageType messageType() {
        return MqttMessageType.PUBLISH;
    }

    @Override
    public void handle(ChannelHandlerContext ctx, MqttMessage msg) {
        MqttPublishMessage publish = (MqttPublishMessage) msg;
        try {
            String topic = publish.variableHeader().topicName();
            MqttQoS pubQos = publish.fixedHeader().qosLevel();
            boolean retain = publish.fixedHeader().isRetain();
            int payloadSize = publish.payload().readableBytes();

            if (pubQos == MqttQoS.AT_LEAST_ONCE) {
                ctx.writeAndFlush(MqttResponses.pubAck(publish.variableHeader().packetId()));
            } else if (pubQos == MqttQoS.EXACTLY_ONCE) {
                ctx.writeAndFlush(MqttResponses.pubRec(publish.variableHeader().packetId()));
            }

            if (retainedStore != null) {
                retainedStore.put(topic, publish, retain);
            }

            if (retain && persistence != null) {
                if (payloadSize == 0) {
                    persistence.deleteRetainedAsync(topic);
                } else {
                    byte[] payload = new byte[payloadSize];
                    publish.payload().getBytes(publish.payload().readerIndex(), payload);
                    persistence.saveRetainedAsync(topic, payload, pubQos.value());
                }
            }

            String clientId = ctx.channel().attr(ChannelAttributes.CLIENT_ID).get();
            if (hookManager != null) {
                hookManager.fireMessagePublish(clientId, topic, pubQos, retain, payloadSize);
            }

            Channel publisher = ctx.channel();
            int delivered = 0;

            try (SubscriberResult subscribers = subscriptionStore.findSubscribers(topic)) {
                for (int i = 0, size = subscribers.size(); i < size; i++) {
                    var e = subscribers.get(i);
                    Channel sub = e.channel();
                    if (sub == publisher || !sub.isActive()) continue;
                    if (!sub.isWritable()) continue;

                    MqttQoS effectiveQos = pubQos.value() <= e.qos().value() ? pubQos : e.qos();
                    MqttQoS deliverQos = effectiveQos.value() <= 0 ? MqttQoS.AT_MOST_ONCE : effectiveQos;
                    MqttFixedHeader fwdHeader = new MqttFixedHeader(
                            MqttMessageType.PUBLISH, false, deliverQos, false, 0);
                    MqttPublishVariableHeader fwdVarHeader = new MqttPublishVariableHeader(
                            topic, deliverQos.value() > 0 ? publish.variableHeader().packetId() : 0);
                    MqttPublishMessage fwd = new MqttPublishMessage(
                            fwdHeader, fwdVarHeader, publish.payload().retainedDuplicate());
                    sub.write(fwd, sub.voidPromise());
                    delivered++;
                }

                if (delivered > 0) {
                    for (int i = 0, size = subscribers.size(); i < size; i++) {
                        Channel sub = subscribers.get(i).channel();
                        if (sub != publisher && sub.isActive()) sub.flush();
                    }
                }
            }

            if (hookManager != null && delivered > 0) {
                hookManager.fireMessageDelivered(clientId, topic, delivered);
            }
        } finally {
            publish.release();
        }
    }
}
