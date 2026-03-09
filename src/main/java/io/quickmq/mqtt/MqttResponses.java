package io.quickmq.mqtt;

import io.netty.handler.codec.mqtt.*;

import java.util.List;

/**
 * MQTT 服务端响应工厂。
 */
public final class MqttResponses {

    private MqttResponses() {}

    public static MqttConnAckMessage connAck(boolean sessionPresent) {
        return connAck(MqttConnectReturnCode.CONNECTION_ACCEPTED, sessionPresent);
    }

    public static MqttConnAckMessage connAck(MqttConnectReturnCode code, boolean sessionPresent) {
        MqttFixedHeader fixed = new MqttFixedHeader(MqttMessageType.CONNACK, false, MqttQoS.AT_MOST_ONCE, false, 0);
        return (MqttConnAckMessage) MqttMessageFactory.newMessage(fixed, new MqttConnAckVariableHeader(code, sessionPresent), null);
    }

    public static MqttMessage pingResp() {
        return MqttMessageFactory.newMessage(
                new MqttFixedHeader(MqttMessageType.PINGRESP, false, MqttQoS.AT_MOST_ONCE, false, 0), null, null);
    }

    public static MqttSubAckMessage subAck(int messageId, List<Integer> grantedQos) {
        return new MqttSubAckMessage(
                new MqttFixedHeader(MqttMessageType.SUBACK, false, MqttQoS.AT_MOST_ONCE, false, 0),
                MqttMessageIdVariableHeader.from(messageId),
                new MqttSubAckPayload(grantedQos));
    }

    public static MqttUnsubAckMessage unsubAck(int messageId) {
        return new MqttUnsubAckMessage(
                new MqttFixedHeader(MqttMessageType.UNSUBACK, false, MqttQoS.AT_MOST_ONCE, false, 0),
                MqttMessageIdVariableHeader.from(messageId), null);
    }

    public static MqttMessage pubAck(int messageId) {
        return newIdMsg(MqttMessageType.PUBACK, MqttQoS.AT_MOST_ONCE, messageId);
    }

    public static MqttMessage pubRec(int messageId) {
        return newIdMsg(MqttMessageType.PUBREC, MqttQoS.AT_MOST_ONCE, messageId);
    }

    public static MqttMessage pubRel(int messageId) {
        return newIdMsg(MqttMessageType.PUBREL, MqttQoS.AT_LEAST_ONCE, messageId);
    }

    public static MqttMessage pubComp(int messageId) {
        return newIdMsg(MqttMessageType.PUBCOMP, MqttQoS.AT_MOST_ONCE, messageId);
    }

    private static MqttMessage newIdMsg(MqttMessageType type, MqttQoS qos, int messageId) {
        return MqttMessageFactory.newMessage(
                new MqttFixedHeader(type, false, qos, false, 0),
                MqttMessageIdVariableHeader.from(messageId), null);
    }
}
