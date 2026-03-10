package io.quickmq.mqtt.handler;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.mqtt.MqttConnectMessage;
import io.netty.handler.codec.mqtt.MqttConnectReturnCode;
import io.netty.handler.codec.mqtt.MqttMessage;
import io.netty.handler.codec.mqtt.MqttMessageType;
import io.netty.handler.timeout.IdleStateHandler;
import io.quickmq.config.MqttProperties;
import io.quickmq.data.PersistenceService;
import io.quickmq.data.entity.Qos2MessageEntity;
import io.quickmq.mqtt.ChannelAttributes;
import io.quickmq.mqtt.MqttResponses;
import io.quickmq.mqtt.MqttServer;
import io.quickmq.mqtt.ServerStatusManager;
import io.quickmq.mqtt.hook.AuthResult;
import io.quickmq.mqtt.hook.ConnectContext;
import io.quickmq.mqtt.hook.HookManager;
import io.quickmq.mqtt.store.Qos2MessageStore;
import io.quickmq.mqtt.store.WillStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

public class ConnectMessageHandler implements MqttMessageHandler {

    private static final Logger log = LoggerFactory.getLogger(ConnectMessageHandler.class);

    private final BooleanSupplier sessionPresentSupplier;
    private final Map<String, Channel> clientIdToChannel;
    private final WillStore willStore;
    private final Qos2MessageStore qos2MessageStore;
    private final HookManager hookManager;
    private final Supplier<MqttProperties> propsSupplier;
    private final PersistenceService persistence;
    private final ServerStatusManager serverStatus;

    public ConnectMessageHandler(BooleanSupplier sessionPresentSupplier, Map<String, Channel> clientIdToChannel,
                                 WillStore willStore, HookManager hookManager, Supplier<MqttProperties> propsSupplier,
                                 PersistenceService persistence) {
        this(sessionPresentSupplier, clientIdToChannel, willStore, null, hookManager, propsSupplier, persistence, null);
    }
    
    public ConnectMessageHandler(BooleanSupplier sessionPresentSupplier, Map<String, Channel> clientIdToChannel,
                                 WillStore willStore, Qos2MessageStore qos2MessageStore, HookManager hookManager, 
                                 Supplier<MqttProperties> propsSupplier, PersistenceService persistence, 
                                 ServerStatusManager serverStatus) {
        this.sessionPresentSupplier = sessionPresentSupplier != null ? sessionPresentSupplier : () -> false;
        this.clientIdToChannel = clientIdToChannel;
        this.willStore = willStore;
        this.qos2MessageStore = qos2MessageStore;
        this.hookManager = hookManager;
        this.propsSupplier = propsSupplier;
        this.persistence = persistence;
        this.serverStatus = serverStatus != null ? serverStatus : new ServerStatusManager();
    }

    @Override
    public MqttMessageType messageType() {
        return MqttMessageType.CONNECT;
    }

    @Override
    public void handle(ChannelHandlerContext ctx, MqttMessage msg) {
        MqttConnectMessage connect = (MqttConnectMessage) msg;
        String clientId = connect.payload().clientIdentifier();
        InetSocketAddress remote = ChannelAttributes.remoteAddress(ctx.channel());
        boolean cleanSession = connect.variableHeader().isCleanSession();
        String username = connect.payload().userName();
        log.debug("CONNECT clientId={}", clientId);

        int version = connect.variableHeader().version();
        if (version != 3 && version != 4) {
            log.debug("不支持的协议版本 {} [MQTT-3.1.2-2]", version);
            ctx.writeAndFlush(MqttResponses.connAck(
                    MqttConnectReturnCode.CONNECTION_REFUSED_UNACCEPTABLE_PROTOCOL_VERSION, false));
            if (hookManager != null) hookManager.fireConnectRejected(clientId, remote, "unsupported protocol version " + version);
            ctx.close();
            return;
        }

        if (clientId == null || clientId.isEmpty()) {
            log.debug("客户端 ID 为空 [MQTT-3.1.3-8]");
            ctx.writeAndFlush(MqttResponses.connAck(
                    MqttConnectReturnCode.CONNECTION_REFUSED_IDENTIFIER_REJECTED, false));
            if (hookManager != null) hookManager.fireConnectRejected(clientId, remote, "client identifier rejected");
            ctx.close();
            return;
        }

        if (clientId.length() > 23) {
            log.debug("客户端 ID 过长: {} [MQTT-3.1.3-5]", clientId.length());
            ctx.writeAndFlush(MqttResponses.connAck(
                    MqttConnectReturnCode.CONNECTION_REFUSED_IDENTIFIER_REJECTED, false));
            if (hookManager != null) hookManager.fireConnectRejected(clientId, remote, "client identifier too long");
            ctx.close();
            return;
        }

        if (!serverStatus.isAcceptingConnections()) {
            log.debug("服务器当前不接受新连接 clientId={}", clientId);
            ctx.writeAndFlush(MqttResponses.connAck(
                    MqttConnectReturnCode.CONNECTION_REFUSED_SERVER_UNAVAILABLE, false));
            if (hookManager != null) hookManager.fireConnectRejected(clientId, remote, "server unavailable");
            ctx.close();
            return;
        }

        if (hookManager != null) {
            ConnectContext connectCtx = new ConnectContext(
                    clientId, username, connect.payload().passwordInBytes(),
                    remote, version, cleanSession
            );
            AuthResult result = hookManager.authenticate(connectCtx);
            if (!result.accepted()) {
                log.debug("认证钩子拒绝 clientId={} reason={}", clientId, result.reason());
                ctx.writeAndFlush(MqttResponses.connAck(result.returnCode(), false));
                hookManager.fireConnectRejected(clientId, remote, result.reason());
                ctx.close();
                return;
            }
        }

        if (clientId != null && !clientId.isEmpty()) {
            Channel old = clientIdToChannel.put(clientId, ctx.channel());
            if (old != null && old != ctx.channel() && old.isActive()) {
                log.debug("同 clientId={} 旧连接被踢 [MQTT-3.1.4-2]", clientId);
                if (hookManager != null) hookManager.fireClientKicked(clientId, ChannelAttributes.remoteAddress(old));
                old.close();
            }
        }

        ctx.channel().attr(ChannelAttributes.CLIENT_ID).set(clientId);
        ctx.channel().attr(ChannelAttributes.CONNECTED).set(Boolean.TRUE);
        if (willStore != null) {
            willStore.store(ctx.channel(), connect);
        }

        replaceIdleHandler(ctx, connect.variableHeader().keepAliveTimeSeconds());

        boolean sessionPresent = calculateSessionPresent(clientId, cleanSession);
        ctx.channel().writeAndFlush(MqttResponses.connAck(sessionPresent));

        if (hookManager != null) hookManager.fireClientConnected(clientId, remote);

        if (persistence != null && clientId != null && !clientId.isEmpty()) {
            if (cleanSession) {
                persistence.deleteAllSubscriptionsAsync(clientId);
                persistence.deleteSessionAsync(clientId);
                if (qos2MessageStore != null) {
                    qos2MessageStore.removeAllForClient(clientId);
                }
            } else {
                persistence.saveSessionAsync(clientId, username, false);
                if (qos2MessageStore != null) {
                    qos2MessageStore.loadClientStates(clientId);
                }
            }
        }
    }

    private boolean calculateSessionPresent(String clientId, boolean cleanSession) {
        if (cleanSession) {
            return false;
        }
        
        if (clientId == null || clientId.isEmpty()) {
            return false;
        }
        
        if (persistence == null) {
            return false;
        }
        
        try {
            boolean hasSubscriptions = !persistence.findSubscriptions(clientId).isEmpty();
            boolean hasQos2Messages = !persistence.findQos2Messages(clientId).isEmpty();
            
            return hasSubscriptions || hasQos2Messages;
        } catch (Exception e) {
            log.warn("计算 Session Present 失败 clientId={}: {}", clientId, e.getMessage());
            return false;
        }
    }

    private void replaceIdleHandler(ChannelHandlerContext ctx, int clientKeepalive) {
        MqttProperties props = propsSupplier != null ? propsSupplier.get() : null;
        int idleSeconds = props != null ? props.resolveIdleSeconds(clientKeepalive) : 0;

        var pipeline = ctx.pipeline();
        if (pipeline.get(MqttServer.IDLE_HANDLER_NAME) != null) {
            pipeline.remove(MqttServer.IDLE_HANDLER_NAME);
        }
        if (idleSeconds > 0) {
            pipeline.addBefore(ctx.name(), MqttServer.IDLE_HANDLER_NAME,
                    new IdleStateHandler(idleSeconds, 0, 0, TimeUnit.SECONDS));
            log.debug("Keepalive: client={}s -> idle={}s", clientKeepalive, idleSeconds);
        }
    }
}
