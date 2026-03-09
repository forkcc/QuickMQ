package io.quickmq.mqtt.handler;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.mqtt.MqttMessage;
import io.netty.handler.codec.mqtt.MqttMessageIdVariableHeader;
import io.netty.handler.codec.mqtt.MqttMessageType;
import io.quickmq.mqtt.MqttResponses;

/**
 * QoS 2 第二步（Broker 作为发布端时）：收到 PUBREC → 回复 PUBREL。
 */
public class PubRecMessageHandler implements MqttMessageHandler {

    @Override
    public MqttMessageType messageType() {
        return MqttMessageType.PUBREC;
    }

    @Override
    public void handle(ChannelHandlerContext ctx, MqttMessage msg) {
        int messageId = ((MqttMessageIdVariableHeader) msg.variableHeader()).messageId();
        ctx.writeAndFlush(MqttResponses.pubRel(messageId));
    }
}
