package io.quickmq.mqtt.handler;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.mqtt.*;
import io.quickmq.mqtt.subscription.SubscriptionStore;
import io.quickmq.mqtt.store.RetainedStore;
import io.quickmq.mqtt.store.WillStore;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class DisconnectMessageHandlerTest {

    @Test
    void messageType() {
        assertEquals(MqttMessageType.DISCONNECT,
                new DisconnectMessageHandler(new SubscriptionStore(), new WillStore(), null).messageType());
    }

    @Test
    void handle_closesChannel() {
        SubscriptionStore subStore = new SubscriptionStore();
        WillStore willStore = new WillStore();
        DisconnectMessageHandler handler = new DisconnectMessageHandler(subStore, willStore, null);

        var ctxRef = new AtomicReference<ChannelHandlerContext>();
        EmbeddedChannel ch = HandlerTestHelper.channelWithCtx(ctxRef);
        subStore.subscribe(ch, "a/b", MqttQoS.AT_MOST_ONCE);

        handler.handle(ctxRef.get(), MqttMessageFactory.newMessage(
                new MqttFixedHeader(MqttMessageType.DISCONNECT, false, MqttQoS.AT_MOST_ONCE, false, 0),
                null, null));

        assertFalse(ch.isOpen(), "Channel should be closed after DISCONNECT");
    }

    @Test
    void handle_clearsWill() {
        WillStore willStore = new WillStore();
        SubscriptionStore subStore = new SubscriptionStore();
        DisconnectMessageHandler handler = new DisconnectMessageHandler(subStore, willStore, null);

        var pubCtxRef = new AtomicReference<ChannelHandlerContext>();
        EmbeddedChannel publisher = HandlerTestHelper.channelWithCtx(pubCtxRef);

        var subCtxRef = new AtomicReference<ChannelHandlerContext>();
        EmbeddedChannel subscriber = HandlerTestHelper.channelWithCtx(subCtxRef);
        subStore.subscribe(subscriber, "will/t", MqttQoS.AT_MOST_ONCE);

        MqttConnectMessage connect = new MqttConnectMessage(
                new MqttFixedHeader(MqttMessageType.CONNECT, false, MqttQoS.AT_MOST_ONCE, false, 0),
                new MqttConnectVariableHeader("MQTT", 4, false, false, false, 0, true, true, 60),
                new MqttConnectPayload("c1", null, "will/t", "bye".getBytes(), null, (byte[]) null));
        willStore.store(publisher, connect);

        handler.handle(pubCtxRef.get(), MqttMessageFactory.newMessage(
                new MqttFixedHeader(MqttMessageType.DISCONNECT, false, MqttQoS.AT_MOST_ONCE, false, 0),
                null, null));

        willStore.trigger(publisher, subStore, new RetainedStore());
        assertNull(subscriber.readOutbound(), "Will should have been cleared on DISCONNECT");
    }
}
