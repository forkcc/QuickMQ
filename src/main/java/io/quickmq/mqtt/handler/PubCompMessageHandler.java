package io.quickmq.mqtt.handler;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.mqtt.MqttMessage;
import io.netty.handler.codec.mqtt.MqttMessageType;

/**
 * 收到客户端的 PUBCOMP（QoS 2 最终确认）。当前内存模式无需额外清理。
 */
public class PubCompMessageHandler implements MqttMessageHandler {

    @Override
    public MqttMessageType messageType() {
        return MqttMessageType.PUBCOMP;
    }

    @Override
    public void handle(ChannelHandlerContext ctx, MqttMessage msg) {
        // QoS 2 flow complete — in-memory broker, no in-flight state to clear
    }
}
