package io.quickmq.mqtt;

import io.netty.handler.codec.mqtt.MqttConnAckMessage;
import io.netty.handler.codec.mqtt.MqttConnAckVariableHeader;
import io.netty.handler.codec.mqtt.MqttConnectReturnCode;
import io.netty.handler.codec.mqtt.MqttFixedHeader;
import io.netty.handler.codec.mqtt.MqttMessageFactory;
import io.netty.handler.codec.mqtt.MqttMessageIdVariableHeader;
import io.netty.handler.codec.mqtt.MqttMessageType;
import io.netty.handler.codec.mqtt.MqttQoS;
import io.netty.handler.codec.mqtt.MqttSubAckMessage;
import io.netty.handler.codec.mqtt.MqttSubAckPayload;
import io.netty.handler.codec.mqtt.MqttUnsubAckMessage;

import java.util.List;

/**
 * MQTT 服务端响应构造（CONNACK、PINGRESP 等）。
 */
public final class MqttResponses {

    private MqttResponses() {}

    public static MqttConnAckMessage connAck(boolean sessionPresent) {
        MqttConnAckVariableHeader varHeader = new MqttConnAckVariableHeader(
                MqttConnectReturnCode.CONNECTION_ACCEPTED,
                sessionPresent
        );
        MqttFixedHeader fixed = new MqttFixedHeader(MqttMessageType.CONNACK, false, MqttQoS.AT_MOST_ONCE, false, 0);
        return (MqttConnAckMessage) MqttMessageFactory.newMessage(fixed, varHeader, null);
    }

    public static io.netty.handler.codec.mqtt.MqttMessage pingResp() {
        return MqttMessageFactory.newMessage(
                new MqttFixedHeader(MqttMessageType.PINGRESP, false, MqttQoS.AT_MOST_ONCE, false, 0),
                null,
                null
        );
    }

    public static MqttSubAckMessage subAck(int messageId, List<Integer> grantedQos) {
        MqttFixedHeader fixed = new MqttFixedHeader(MqttMessageType.SUBACK, false, MqttQoS.AT_MOST_ONCE, false, 0);
        MqttMessageIdVariableHeader varHeader = MqttMessageIdVariableHeader.from(messageId);
        MqttSubAckPayload payload = new MqttSubAckPayload(grantedQos);
        return new MqttSubAckMessage(fixed, varHeader, payload);
    }

    public static MqttUnsubAckMessage unsubAck(int messageId) {
        MqttFixedHeader fixed = new MqttFixedHeader(MqttMessageType.UNSUBACK, false, MqttQoS.AT_MOST_ONCE, false, 0);
        MqttMessageIdVariableHeader varHeader = MqttMessageIdVariableHeader.from(messageId);
        return new MqttUnsubAckMessage(fixed, varHeader, null);
    }
}
