package io.quickmq.mqtt.handler;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.mqtt.MqttMessage;
import io.netty.handler.codec.mqtt.MqttMessageIdVariableHeader;
import io.netty.handler.codec.mqtt.MqttMessageType;
import io.quickmq.mqtt.ChannelAttributes;
import io.quickmq.mqtt.MqttResponses;
import io.quickmq.mqtt.store.Qos2MessageStore;

/**
 * QoS 2 第三步：收到 PUBREL → 回复 PUBCOMP（MQTT-4.3.3-1）。
 */
public class PubRelMessageHandler implements MqttMessageHandler {

    private final Qos2MessageStore qos2MessageStore;
    
    public PubRelMessageHandler() {
        this(null);
    }
    
    public PubRelMessageHandler(Qos2MessageStore qos2MessageStore) {
        this.qos2MessageStore = qos2MessageStore;
    }

    @Override
    public MqttMessageType messageType() {
        return MqttMessageType.PUBREL;
    }

    @Override
    public void handle(ChannelHandlerContext ctx, MqttMessage msg) {
        int messageId = ((MqttMessageIdVariableHeader) msg.variableHeader()).messageId();
        String clientId = ctx.channel().attr(ChannelAttributes.CLIENT_ID).get();
        
        if (qos2MessageStore != null && clientId != null) {
            qos2MessageStore.updateToPubRelReceived(clientId, messageId);
        }
        
        ctx.writeAndFlush(MqttResponses.pubComp(messageId));
        
        if (qos2MessageStore != null && clientId != null) {
            qos2MessageStore.remove(clientId, messageId);
        }
    }
}
