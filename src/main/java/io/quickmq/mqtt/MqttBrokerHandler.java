package io.quickmq.mqtt;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.mqtt.MqttMessage;
import io.netty.handler.codec.mqtt.MqttMessageType;
import io.quickmq.mqtt.event.FireUserEventTriggeredHandler;
import io.quickmq.mqtt.event.IdleReaderIdleHandler;
import io.quickmq.mqtt.event.UserEventHandler;
import io.quickmq.mqtt.handler.ConnectMessageHandler;
import io.quickmq.mqtt.handler.DisconnectMessageHandler;
import io.quickmq.mqtt.handler.MqttMessageHandler;
import io.quickmq.mqtt.handler.NoOpMessageHandler;
import io.quickmq.mqtt.handler.PingReqMessageHandler;
import io.quickmq.mqtt.handler.PublishMessageHandler;
import io.quickmq.mqtt.handler.SubscribeMessageHandler;
import io.quickmq.mqtt.handler.UnsubscribeMessageHandler;
import io.quickmq.mqtt.subscription.SubscriptionStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * MQTT 协议业务 Handler：按消息类型委托适配器（Null Object 兜底），用户事件走策略链。
 */
public class MqttBrokerHandler extends ChannelInboundHandlerAdapter {

    private static final Logger log = LoggerFactory.getLogger(MqttBrokerHandler.class);
    private static final MqttMessageHandler NO_OP = new NoOpMessageHandler();

    private final SubscriptionStore subscriptionStore = new SubscriptionStore();
    private final Map<MqttMessageType, MqttMessageHandler> handlers = Stream.of(
            new ConnectMessageHandler(),
            new PingReqMessageHandler(),
            new DisconnectMessageHandler(subscriptionStore),
            new SubscribeMessageHandler(subscriptionStore),
            new PublishMessageHandler(subscriptionStore),
            new UnsubscribeMessageHandler(subscriptionStore)
    ).collect(Collectors.toUnmodifiableMap(MqttMessageHandler::messageType, h -> h));

    private final List<UserEventHandler> userEventHandlers = List.of(
            new IdleReaderIdleHandler(),
            new FireUserEventTriggeredHandler()
    );

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        if (!(msg instanceof MqttMessage mqtt)) {
            ctx.fireChannelRead(msg);
            return;
        }
        MqttMessageType type = mqtt.fixedHeader().messageType();
        handlers.getOrDefault(type, NO_OP).handle(ctx, mqtt);
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
        for (UserEventHandler h : userEventHandlers) {
            if (h.handle(ctx, evt)) return;
        }
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        subscriptionStore.removeChannel(ctx.channel());
        ctx.fireChannelInactive();
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.warn("通道异常: {}", cause.getMessage());
        ctx.close();
    }
}
