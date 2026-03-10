package io.quickmq.mqtt.handler;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.mqtt.*;
import java.net.InetSocketAddress;
import io.quickmq.data.PersistenceService;
import io.quickmq.mqtt.ChannelAttributes;
import io.quickmq.mqtt.MqttResponses;
import io.quickmq.mqtt.TopicValidator;
import io.quickmq.mqtt.hook.AclCheckContext;
import io.quickmq.mqtt.hook.AclResult;
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
    private final PersistenceService persistence;

    public SubscribeMessageHandler(SubscriptionStore subscriptionStore, RetainedStore retainedStore,
                                   HookManager hookManager, PersistenceService persistence) {
        this.subscriptionStore = subscriptionStore;
        this.retainedStore = retainedStore;
        this.hookManager = hookManager;
        this.persistence = persistence;
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
        String clientId = ctx.channel().attr(ChannelAttributes.CLIENT_ID).get();

        for (MqttTopicSubscription sub : topicSubscriptions) {
            String filter = sub.topicFilter();
            MqttQoS qos = sub.qualityOfService();
            
            if (!TopicValidator.isSubscribableFilter(filter)) {
                log.warn("无效的订阅过滤器: {}", filter);
                grantedQos.add(0x80); // 0x80 表示失败
                continue;
            }
            
            if (hookManager != null && clientId != null) {
                InetSocketAddress remoteAddr = ChannelAttributes.remoteAddress(ctx.channel());
                AclCheckContext aclContext = AclCheckContext.forSubscribe(clientId, null, remoteAddr, filter);
                AclResult aclResult = hookManager.checkAcl(aclContext);
                if (!aclResult.allowed()) {
                    log.warn("客户端 {} 无权限订阅主题 {}", clientId, filter);
                    grantedQos.add(0x80); // 0x80 表示失败
                    continue;
                }
            }
            
            subscriptionStore.subscribe(ctx.channel(), filter, qos);
            grantedQos.add(qos.value());
            filters.add(filter);
            if (retainedStore != null) {
                retainedStore.deliverMatching(filter, ctx.channel());
            }
            if (persistence != null && clientId != null) {
                persistence.saveSubscriptionAsync(clientId, filter, qos.value());
            }
            log.debug("SUBSCRIBE filter={} qos={}", filter, qos);
        }
        ctx.writeAndFlush(MqttResponses.subAck(messageId, grantedQos));

        if (hookManager != null) {
            hookManager.fireClientSubscribe(clientId, filters);
        }
    }
}
