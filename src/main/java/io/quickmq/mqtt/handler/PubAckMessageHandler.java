package io.quickmq.mqtt.handler;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.mqtt.MqttMessage;
import io.netty.handler.codec.mqtt.MqttMessageType;

/**
 * 收到客户端的 PUBACK（QoS 1 确认）。当前内存模式无需重传，仅消费该消息。
 */
public class PubAckMessageHandler implements MqttMessageHandler {

    @Override
    public MqttMessageType messageType() {
        return MqttMessageType.PUBACK;
    }

    @Override
    public void handle(ChannelHandlerContext ctx, MqttMessage msg) {
        // QoS 1 ACK received — in-memory broker, no retransmit queue to clear
    }
}
