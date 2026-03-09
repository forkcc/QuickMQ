package io.quickmq.mqtt.handler;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.mqtt.MqttMessage;
import io.netty.handler.codec.mqtt.MqttMessageType;
import io.quickmq.mqtt.MqttResponses;

public class PingReqMessageHandler implements MqttMessageHandler {

    @Override
    public MqttMessageType messageType() {
        return MqttMessageType.PINGREQ;
    }

    @Override
    public void handle(ChannelHandlerContext ctx, MqttMessage msg) {
        ctx.writeAndFlush(MqttResponses.pingResp());
    }
}
