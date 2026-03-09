package io.quickmq.mqtt.handler;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.mqtt.MqttMessage;
import io.netty.handler.codec.mqtt.MqttMessageType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Null Object：未注册类型的 MQTT 报文仅打日志，不抛错。
 */
public class NoOpMessageHandler implements MqttMessageHandler {

    private static final Logger log = LoggerFactory.getLogger(NoOpMessageHandler.class);

    @Override
    public MqttMessageType messageType() {
        throw new UnsupportedOperationException("NoOp 仅作 getOrDefault 默认值，不应按 type 查找");
    }

    @Override
    public void handle(ChannelHandlerContext ctx, MqttMessage msg) {
        log.debug("未处理的消息类型: {}", msg.fixedHeader().messageType());
    }
}
