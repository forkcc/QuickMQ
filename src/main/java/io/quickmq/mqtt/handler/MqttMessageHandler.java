package io.quickmq.mqtt.handler;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.mqtt.MqttMessage;
import io.netty.handler.codec.mqtt.MqttMessageType;

/**
 * MQTT 报文类型适配器：仅处理一种消息类型。
 */
public interface MqttMessageHandler {

    MqttMessageType messageType();

    void handle(ChannelHandlerContext ctx, MqttMessage msg);
}
