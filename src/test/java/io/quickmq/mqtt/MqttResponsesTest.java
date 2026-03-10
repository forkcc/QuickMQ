package io.quickmq.mqtt;

import io.netty.handler.codec.mqtt.*;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MqttResponsesTest {

    @Test
    void connAck_accepted() {
        MqttConnAckMessage msg = MqttResponses.connAck(true);
        assertEquals(MqttMessageType.CONNACK, msg.fixedHeader().messageType());
        assertEquals(MqttConnectReturnCode.CONNECTION_ACCEPTED,
                msg.variableHeader().connectReturnCode());
        assertTrue(msg.variableHeader().isSessionPresent());
    }

    @Test
    void connAck_rejected() {
        MqttConnAckMessage msg = MqttResponses.connAck(
                MqttConnectReturnCode.CONNECTION_REFUSED_NOT_AUTHORIZED, false);
        assertEquals(MqttConnectReturnCode.CONNECTION_REFUSED_NOT_AUTHORIZED,
                msg.variableHeader().connectReturnCode());
        assertFalse(msg.variableHeader().isSessionPresent());
    }

    @Test
    void pingResp() {
        MqttMessage msg = MqttResponses.pingResp();
        assertEquals(MqttMessageType.PINGRESP, msg.fixedHeader().messageType());
    }

    @Test
    void subAck() {
        MqttSubAckMessage msg = MqttResponses.subAck(42, List.of(0, 1, 2));
        assertEquals(MqttMessageType.SUBACK, msg.fixedHeader().messageType());
        assertEquals(42, msg.variableHeader().messageId());
        assertEquals(List.of(0, 1, 2), msg.payload().grantedQoSLevels());
    }

    @Test
    void unsubAck() {
        MqttUnsubAckMessage msg = MqttResponses.unsubAck(99);
        assertEquals(MqttMessageType.UNSUBACK, msg.fixedHeader().messageType());
        assertEquals(99, msg.variableHeader().messageId());
    }

    @Test
    void pubAck() {
        MqttMessage msg = MqttResponses.pubAck(10);
        assertEquals(MqttMessageType.PUBACK, msg.fixedHeader().messageType());
        assertEquals(10, ((MqttMessageIdVariableHeader) msg.variableHeader()).messageId());
    }

    @Test
    void pubRec() {
        MqttMessage msg = MqttResponses.pubRec(20);
        assertEquals(MqttMessageType.PUBREC, msg.fixedHeader().messageType());
        assertEquals(20, ((MqttMessageIdVariableHeader) msg.variableHeader()).messageId());
    }

    @Test
    void pubRel() {
        MqttMessage msg = MqttResponses.pubRel(30);
        assertEquals(MqttMessageType.PUBREL, msg.fixedHeader().messageType());
        assertEquals(MqttQoS.AT_LEAST_ONCE, msg.fixedHeader().qosLevel());
        assertEquals(30, ((MqttMessageIdVariableHeader) msg.variableHeader()).messageId());
    }

    @Test
    void pubComp() {
        MqttMessage msg = MqttResponses.pubComp(40);
        assertEquals(MqttMessageType.PUBCOMP, msg.fixedHeader().messageType());
        assertEquals(40, ((MqttMessageIdVariableHeader) msg.variableHeader()).messageId());
    }
}
