package io.quickmq.mqtt.handler;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.mqtt.MqttMessage;
import io.netty.handler.codec.mqtt.MqttMessageIdVariableHeader;
import io.netty.handler.codec.mqtt.MqttMessageType;
import io.quickmq.mqtt.MqttResponses;

/**
 * QoS 2 第三步：收到 PUBREL → 回复 PUBCOMP（MQTT-4.3.3-1）。
 */
public class PubRelMessageHandler implements MqttMessageHandler {

    @Override
    public MqttMessageType messageType() {
        return MqttMessageType.PUBREL;
    }

    @Override
    public void handle(ChannelHandlerContext ctx, MqttMessage msg) {
        int messageId = ((MqttMessageIdVariableHeader) msg.variableHeader()).messageId();
        ctx.writeAndFlush(MqttResponses.pubComp(messageId));
    }
}
