package io.quickmq.mqtt.handler;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.mqtt.*;
import io.netty.handler.timeout.IdleStateHandler;
import io.quickmq.config.HookProperties;
import io.quickmq.config.MqttProperties;
import io.quickmq.mqtt.ChannelAttributes;
import io.quickmq.mqtt.MqttServer;
import io.quickmq.mqtt.hook.*;
import io.quickmq.mqtt.store.WillStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class ConnectMessageHandlerTest {

    private Map<String, io.netty.channel.Channel> clientMap;
    private WillStore willStore;
    private MqttProperties props;

    @BeforeEach
    void setUp() {
        clientMap = new ConcurrentHashMap<>();
        willStore = new WillStore();
        props = new MqttProperties();
        props.setHooks(new HookProperties());
        props.setDefaultKeepaliveSeconds(60);
    }

    private ConnectMessageHandler createHandler(HookManager hookManager) {
        return new ConnectMessageHandler(
                () -> false, clientMap, willStore, hookManager, () -> props, null);
    }

    private record ChannelAndCtx(EmbeddedChannel ch, ChannelHandlerContext ctx) {}

    private ChannelAndCtx createChannel() {
        var ctxRef = new AtomicReference<ChannelHandlerContext>();
        EmbeddedChannel ch = new EmbeddedChannel(new ChannelInboundHandlerAdapter() {
            @Override
            public void channelRegistered(ChannelHandlerContext ctx) {
                ctxRef.set(ctx);
                ctx.fireChannelRegistered();
            }
        });
        ch.pipeline().addFirst(MqttServer.IDLE_HANDLER_NAME,
                new IdleStateHandler(10, 0, 0, TimeUnit.SECONDS));
        ch.attr(ChannelAttributes.REAL_REMOTE_ADDRESS).set(new InetSocketAddress("127.0.0.1", 12345));
        return new ChannelAndCtx(ch, ctxRef.get());
    }

    private MqttConnectMessage buildConnect(String clientId, int keepalive, int version) {
        MqttConnectVariableHeader varHeader = new MqttConnectVariableHeader(
                "MQTT", version, false, false, false, 0, false, true, keepalive);
        MqttConnectPayload payload = new MqttConnectPayload(
                clientId, null, (byte[]) null, null, (byte[]) null);
        return new MqttConnectMessage(
                new MqttFixedHeader(MqttMessageType.CONNECT, false, MqttQoS.AT_MOST_ONCE, false, 0),
                varHeader, payload);
    }

    @Test
    void messageType() {
        assertEquals(MqttMessageType.CONNECT, createHandler(null).messageType());
    }

    @Test
    void handle_validConnect_sendsCONNACK() {
        var cc = createChannel();
        createHandler(null).handle(cc.ctx(), buildConnect("c1", 60, 4));

        MqttConnAckMessage ack = (MqttConnAckMessage) cc.ch().readOutbound();
        assertNotNull(ack);
        assertEquals(MqttConnectReturnCode.CONNECTION_ACCEPTED, ack.variableHeader().connectReturnCode());
    }

    @Test
    void handle_setsClientIdAndConnected() {
        var cc = createChannel();
        createHandler(null).handle(cc.ctx(), buildConnect("myClient", 60, 4));

        assertEquals("myClient", cc.ch().attr(ChannelAttributes.CLIENT_ID).get());
        assertTrue(cc.ch().attr(ChannelAttributes.CONNECTED).get());
    }

    @Test
    void handle_unsupportedVersion_rejects() {
        var cc = createChannel();
        createHandler(null).handle(cc.ctx(), buildConnect("c1", 60, 5));

        MqttConnAckMessage ack = (MqttConnAckMessage) cc.ch().readOutbound();
        assertEquals(MqttConnectReturnCode.CONNECTION_REFUSED_UNACCEPTABLE_PROTOCOL_VERSION,
                ack.variableHeader().connectReturnCode());
        assertFalse(cc.ch().isOpen());
    }

    @Test
    void handle_authHook_rejects() {
        MqttAuthHook rejectHook = ctx -> AuthResult.badCredentials("wrong password");
        HookManager hm = new HookManager(props, rejectHook, null);

        var cc = createChannel();
        createHandler(hm).handle(cc.ctx(), buildConnect("c1", 60, 4));

        MqttConnAckMessage ack = (MqttConnAckMessage) cc.ch().readOutbound();
        assertEquals(MqttConnectReturnCode.CONNECTION_REFUSED_BAD_USER_NAME_OR_PASSWORD,
                ack.variableHeader().connectReturnCode());
        assertFalse(cc.ch().isOpen());
    }

    @Test
    void handle_authHook_accepts() {
        MqttAuthHook acceptHook = ctx -> AuthResult.accept();
        HookManager hm = new HookManager(props, acceptHook, null);

        var cc = createChannel();
        createHandler(hm).handle(cc.ctx(), buildConnect("c1", 60, 4));

        MqttConnAckMessage ack = (MqttConnAckMessage) cc.ch().readOutbound();
        assertEquals(MqttConnectReturnCode.CONNECTION_ACCEPTED, ack.variableHeader().connectReturnCode());
    }

    @Test
    void handle_replacesIdleHandler() {
        var cc = createChannel();
        assertNotNull(cc.ch().pipeline().get(MqttServer.IDLE_HANDLER_NAME));

        createHandler(null).handle(cc.ctx(), buildConnect("c1", 120, 4));
        assertNotNull(cc.ch().pipeline().get(MqttServer.IDLE_HANDLER_NAME));
    }

    @Test
    void handle_registersInClientMap() {
        var cc = createChannel();
        createHandler(null).handle(cc.ctx(), buildConnect("c1", 60, 4));
        assertSame(cc.ch(), clientMap.get("c1"));
    }

    @Test
    void handle_duplicateClientId_kicksOld() {
        var old = createChannel();
        ConnectMessageHandler handler = createHandler(null);
        handler.handle(old.ctx(), buildConnect("dup", 60, 4));
        assertTrue(old.ch().isOpen());

        var newCh = createChannel();
        handler.handle(newCh.ctx(), buildConnect("dup", 60, 4));
        assertFalse(old.ch().isOpen(), "Old channel should be kicked");
        assertSame(newCh.ch(), clientMap.get("dup"));
    }

    @Test
    void handle_defaultAuth_allowsAll() {
        HookManager hm = new HookManager(props, null, null);
        var cc = createChannel();
        createHandler(hm).handle(cc.ctx(), buildConnect("c1", 60, 4));

        MqttConnAckMessage ack = (MqttConnAckMessage) cc.ch().readOutbound();
        assertEquals(MqttConnectReturnCode.CONNECTION_ACCEPTED, ack.variableHeader().connectReturnCode());
    }
}
