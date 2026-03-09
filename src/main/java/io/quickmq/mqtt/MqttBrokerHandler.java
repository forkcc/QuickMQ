package io.quickmq.mqtt;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.mqtt.MqttMessage;
import io.netty.handler.codec.mqtt.MqttMessageType;
import io.quickmq.mqtt.handler.*;
import io.quickmq.mqtt.hook.HookManager;
import io.quickmq.mqtt.hook.MqttEventHook;
import io.quickmq.mqtt.subscription.SubscriptionStore;
import io.quickmq.mqtt.store.RetainedStore;
import io.quickmq.mqtt.store.WillStore;
import io.quickmq.config.MqttProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * MQTT 协议业务 Handler：按消息类型委托适配器（Null Object 兜底），用户事件走策略链。
 * 多连接共享同一实例，SubscriptionStore 等状态线程安全。
 */
@ChannelHandler.Sharable
public class MqttBrokerHandler extends ChannelInboundHandlerAdapter {

    private static final Logger log = LoggerFactory.getLogger(MqttBrokerHandler.class);
    private static final MqttMessageHandler NO_OP = new NoOpMessageHandler();

    private volatile MqttProperties properties;
    private volatile HookManager hookManager;
    private final SubscriptionStore subscriptionStore = new SubscriptionStore();
    private final RetainedStore retainedStore = new RetainedStore();
    private final WillStore willStore = new WillStore();
    private final Map<String, Channel> clientIdToChannel = new ConcurrentHashMap<>();
    private Map<MqttMessageType, MqttMessageHandler> handlers;

    private final List<UserEventHandler> userEventHandlers = List.of(
            new IdleReaderIdleHandler(),
            new FireUserEventTriggeredHandler()
    );

    public void setProperties(MqttProperties properties) {
        this.properties = properties;
    }

    public void setHookManager(HookManager hookManager) {
        this.hookManager = hookManager;
        this.handlers = buildHandlers();
    }

    private Map<MqttMessageType, MqttMessageHandler> buildHandlers() {
        return Stream.of(
                new ConnectMessageHandler(
                        () -> properties != null && properties.isDefaultSessionPresent(),
                        clientIdToChannel, willStore, hookManager, () -> properties),
                new PingReqMessageHandler(),
                new DisconnectMessageHandler(subscriptionStore, willStore, hookManager),
                new SubscribeMessageHandler(subscriptionStore, retainedStore, hookManager),
                new PublishMessageHandler(subscriptionStore, retainedStore, hookManager),
                new UnsubscribeMessageHandler(subscriptionStore, hookManager),
                new PubAckMessageHandler(),
                new PubRecMessageHandler(),
                new PubRelMessageHandler(),
                new PubCompMessageHandler()
        ).collect(Collectors.toUnmodifiableMap(MqttMessageHandler::messageType, h -> h));
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        if (!(msg instanceof MqttMessage mqtt)) {
            ctx.fireChannelRead(msg);
            return;
        }
        MqttMessageType type = mqtt.fixedHeader().messageType();
        boolean connected = Boolean.TRUE.equals(ctx.channel().attr(ChannelAttributes.CONNECTED).get());
        if (type != MqttMessageType.CONNECT && !connected) {
            ctx.close();
            return;
        }
        if (type == MqttMessageType.CONNECT && connected) {
            ctx.close();
            return;
        }
        if (handlers != null) {
            handlers.getOrDefault(type, NO_OP).handle(ctx, mqtt);
        } else {
            NO_OP.handle(ctx, mqtt);
        }
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
        for (UserEventHandler h : userEventHandlers) {
            if (h.handle(ctx, evt)) return;
        }
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        willStore.trigger(ctx.channel(), subscriptionStore, retainedStore);
        String clientId = ctx.channel().attr(ChannelAttributes.CLIENT_ID).get();
        if (clientId != null && !clientId.isEmpty()) {
            clientIdToChannel.remove(clientId, ctx.channel());
        }
        subscriptionStore.removeChannel(ctx.channel());

        HookManager hm = hookManager;
        if (hm != null) {
            InetSocketAddress remote = ChannelAttributes.remoteAddress(ctx.channel());
            hm.fireClientDisconnected(clientId, remote, MqttEventHook.DisconnectReason.NORMAL);
        }
        ctx.fireChannelInactive();
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.warn("通道异常: {}", cause.getMessage());
        HookManager hm = hookManager;
        if (hm != null) {
            String clientId = ctx.channel().attr(ChannelAttributes.CLIENT_ID).get();
            InetSocketAddress remote = ChannelAttributes.remoteAddress(ctx.channel());
            hm.fireClientDisconnected(clientId, remote, MqttEventHook.DisconnectReason.EXCEPTION);
        }
        ctx.close();
    }
}
