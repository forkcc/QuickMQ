package io.quickmq.mqtt.handler;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.mqtt.*;
import java.net.InetSocketAddress;
import io.quickmq.data.PersistenceService;
import io.quickmq.mqtt.ChannelAttributes;
import io.quickmq.mqtt.MqttResponses;
import io.quickmq.mqtt.hook.AclCheckContext;
import io.quickmq.mqtt.hook.AclResult;
import io.quickmq.mqtt.hook.HookManager;
import io.quickmq.mqtt.TopicValidator;
import io.quickmq.mqtt.store.Qos2MessageStore;
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
    private final Qos2MessageStore qos2MessageStore;
    private final HookManager hookManager;
    private final PersistenceService persistence;

    public PublishMessageHandler(SubscriptionStore subscriptionStore, RetainedStore retainedStore,
                                 HookManager hookManager, PersistenceService persistence) {
        this(subscriptionStore, retainedStore, null, hookManager, persistence);
    }
    
    public PublishMessageHandler(SubscriptionStore subscriptionStore, RetainedStore retainedStore,
                                 Qos2MessageStore qos2MessageStore, HookManager hookManager, PersistenceService persistence) {
        this.subscriptionStore = subscriptionStore;
        this.retainedStore = retainedStore;
        this.qos2MessageStore = qos2MessageStore;
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
            
            if (!TopicValidator.isPublishableTopic(topic)) {
                log.warn("无效的发布主题: {}", topic);
                ctx.close();
                return;
            }
            
            String clientId = ctx.channel().attr(ChannelAttributes.CLIENT_ID).get();
            if (hookManager != null && clientId != null) {
                InetSocketAddress remoteAddr = ChannelAttributes.remoteAddress(ctx.channel());
                AclCheckContext aclContext = AclCheckContext.forPublish(clientId, null, remoteAddr, topic);
                AclResult aclResult = hookManager.checkAcl(aclContext);
                if (!aclResult.allowed()) {
                    log.warn("客户端 {} 无权限发布主题 {}", clientId, topic);
                    return;
                }
            }

            if (pubQos == MqttQoS.AT_LEAST_ONCE) {
                ctx.writeAndFlush(MqttResponses.pubAck(publish.variableHeader().packetId()));
            } else if (pubQos == MqttQoS.EXACTLY_ONCE) {
                int messageId = publish.variableHeader().packetId();
                ctx.writeAndFlush(MqttResponses.pubRec(messageId));
                
                if (qos2MessageStore != null) {
                    qos2MessageStore.storePubRecReceived(ctx.channel(), publish);
                }
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
