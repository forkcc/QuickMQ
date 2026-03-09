package io.quickmq.mqtt.handler;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.mqtt.MqttConnectMessage;
import io.netty.handler.codec.mqtt.MqttMessage;
import io.netty.handler.codec.mqtt.MqttMessageType;
import io.quickmq.mqtt.ChannelAttributes;
import io.quickmq.mqtt.MqttResponses;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ConnectMessageHandler implements MqttMessageHandler {

    private static final Logger log = LoggerFactory.getLogger(ConnectMessageHandler.class);

    @Override
    public MqttMessageType messageType() {
        return MqttMessageType.CONNECT;
    }

    @Override
    public void handle(ChannelHandlerContext ctx, MqttMessage msg) {
        MqttConnectMessage connect = (MqttConnectMessage) msg;
        String clientId = connect.payload().clientIdentifier();
        log.info("CONNECT clientId={}", clientId);
        ctx.channel().attr(ChannelAttributes.CLIENT_ID).set(clientId);
        ctx.channel().writeAndFlush(MqttResponses.connAck(false));
    }
}
