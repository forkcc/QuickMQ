package io.quickmq.mqtt;

import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.mqtt.*;
import io.quickmq.config.HookProperties;
import io.quickmq.config.MqttProperties;
import io.quickmq.mqtt.hook.HookManager;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;

import static org.junit.jupiter.api.Assertions.*;

class MqttBrokerHandlerTest {

    private MqttBrokerHandler newBrokerHandler() {
        MqttBrokerHandler handler = new MqttBrokerHandler();
        MqttProperties props = new MqttProperties();
        props.setHooks(new HookProperties());
        handler.setProperties(props);
        HookManager hm = new HookManager(props, null, null);
        handler.setHookManager(hm);
        return handler;
    }

    private MqttConnectMessage buildConnect(String clientId, int keepalive) {
        MqttConnectVariableHeader varHeader = new MqttConnectVariableHeader(
                "MQTT", 4, false, false, false, 0, false, true, keepalive);
        MqttConnectPayload payload = new MqttConnectPayload(
                clientId, null, (byte[]) null, null, (byte[]) null);
        return new MqttConnectMessage(
                new MqttFixedHeader(MqttMessageType.CONNECT, false, MqttQoS.AT_MOST_ONCE, false, 0),
                varHeader, payload);
    }

    @Test
    void channelRead_nonConnectFirst_closesChannel() {
        MqttBrokerHandler handler = newBrokerHandler();
        EmbeddedChannel ch = new EmbeddedChannel(handler);
        ch.attr(ChannelAttributes.REAL_REMOTE_ADDRESS).set(new InetSocketAddress("127.0.0.1", 1234));

        MqttMessage ping = MqttMessageFactory.newMessage(
                new MqttFixedHeader(MqttMessageType.PINGREQ, false, MqttQoS.AT_MOST_ONCE, false, 0),
                null, null);
        ch.writeInbound(ping);
        assertFalse(ch.isOpen(), "Should close if first message is not CONNECT");
    }

    @Test
    void channelRead_connect_setsConnected() {
        MqttBrokerHandler handler = newBrokerHandler();
        EmbeddedChannel ch = new EmbeddedChannel(handler);
        ch.attr(ChannelAttributes.REAL_REMOTE_ADDRESS).set(new InetSocketAddress("127.0.0.1", 1234));

        ch.writeInbound(buildConnect("c1", 0));
        assertTrue(Boolean.TRUE.equals(ch.attr(ChannelAttributes.CONNECTED).get()));
        assertEquals("c1", ch.attr(ChannelAttributes.CLIENT_ID).get());
    }

    @Test
    void channelRead_doubleConnect_closesChannel() {
        MqttBrokerHandler handler = newBrokerHandler();
        EmbeddedChannel ch = new EmbeddedChannel(handler);
        ch.attr(ChannelAttributes.REAL_REMOTE_ADDRESS).set(new InetSocketAddress("127.0.0.1", 1234));

        ch.writeInbound(buildConnect("c1", 0));
        assertTrue(ch.isOpen());

        // Mark channel as connected manually (since the handler already set it)
        ch.writeInbound(buildConnect("c1", 0));
        assertFalse(ch.isOpen(), "Double CONNECT should close channel");
    }

    @Test
    void exceptionCaught_closesChannel() {
        MqttBrokerHandler handler = newBrokerHandler();
        EmbeddedChannel ch = new EmbeddedChannel(handler);
        ch.attr(ChannelAttributes.REAL_REMOTE_ADDRESS).set(new InetSocketAddress("127.0.0.1", 1234));
        ch.pipeline().fireExceptionCaught(new RuntimeException("test error"));
        assertFalse(ch.isOpen());
    }

    @Test
    void channelRead_nonMqttMessage_passThrough() {
        MqttBrokerHandler handler = newBrokerHandler();
        EmbeddedChannel ch = new EmbeddedChannel(handler);
        assertDoesNotThrow(() -> ch.writeInbound("not mqtt"));
    }
}
