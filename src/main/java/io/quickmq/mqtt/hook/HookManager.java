package io.quickmq.mqtt.hook;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.handler.codec.mqtt.MqttQoS;
import io.quickmq.config.HookProperties;
import io.quickmq.config.MqttProperties;
import io.quickmq.mqtt.hook.http.HttpAuthHook;
import io.quickmq.mqtt.hook.http.HttpEventHook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;

/**
 * Hook 管理器：统一调度认证钩子和审计事件钩子。
 * <p>
 * HTTP Webhook 使用虚拟线程 Executor（JDK 21+），阻塞 IO 不占用平台线程，
 * 与 Netty EventLoop 完全隔离，百万连接下 Hook 调用不影响消息路径。
 */
@Component
public class HookManager {

    private static final Logger log = LoggerFactory.getLogger(HookManager.class);

    private final MqttAuthHook authHook;
    private final MqttAclHook aclHook;
    private final MqttEventHook[] eventHooks;
    private final boolean hasEventHooks;

    public HookManager(
            MqttProperties mqttProperties,
            @org.springframework.lang.Nullable MqttAuthHook beanAuthHook,
            @org.springframework.lang.Nullable List<MqttEventHook> beanEventHooks,
            @org.springframework.lang.Nullable MqttAclHook beanAclHook
    ) {
        HookProperties hookProps = mqttProperties.getHooks();
        int timeoutMs = hookProps.getHttpTimeoutMs();

        HttpClient httpClient = null;
        ObjectMapper mapper = null;

        boolean needHttp = !hookProps.getAuthUrl().isEmpty() || !hookProps.getEventUrl().isEmpty();
        if (needHttp) {
            httpClient = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofMillis(timeoutMs))
                    .executor(Executors.newVirtualThreadPerTaskExecutor())
                    .build();
            mapper = new ObjectMapper();
        }

        // ---------- Auth Hook ----------
        MqttAuthHook resolvedAuth = null;
        if (!hookProps.getAuthUrl().isEmpty()) {
            resolvedAuth = new HttpAuthHook(hookProps.getAuthUrl(), timeoutMs, httpClient, mapper);
            log.info("认证钩子: HTTP {} (虚拟线程)", hookProps.getAuthUrl());
        }
        if (resolvedAuth == null && beanAuthHook != null) {
            resolvedAuth = beanAuthHook;
            log.info("认证钩子: Spring Bean {}", beanAuthHook.getClass().getSimpleName());
        }
        if (resolvedAuth == null) {
            resolvedAuth = new DefaultAuthHook();
        }
        this.authHook = resolvedAuth;

        // ---------- Event Hooks ----------
        List<MqttEventHook> merged = new ArrayList<>();
        if (!hookProps.getEventUrl().isEmpty()) {
            merged.add(new HttpEventHook(hookProps.getEventUrl(), timeoutMs, httpClient, mapper));
            log.info("事件钩子: HTTP {} (虚拟线程)", hookProps.getEventUrl());
        }
        if (beanEventHooks != null) merged.addAll(beanEventHooks);
        this.eventHooks = merged.toArray(new MqttEventHook[0]);
        this.hasEventHooks = this.eventHooks.length > 0;

        // ---------- ACL Hook ----------
        MqttAclHook resolvedAcl = null;
        if (beanAclHook != null) {
            resolvedAcl = beanAclHook;
            log.info("ACL 钩子: Spring Bean {}", beanAclHook.getClass().getSimpleName());
        }
        if (resolvedAcl == null) {
            resolvedAcl = new DefaultAclHook();
        }
        this.aclHook = resolvedAcl;

        log.info("Hook 初始化完成: authHook={}, aclHook={}, eventHooks={}个",
                this.authHook.getClass().getSimpleName(), 
                this.aclHook.getClass().getSimpleName(),
                this.eventHooks.length);
    }

    public boolean hasEventHooks() { return hasEventHooks; }

    public AuthResult authenticate(ConnectContext ctx) {
        try {
            return authHook.authenticate(ctx);
        } catch (Exception e) {
            log.error("认证钩子异常，拒绝连接: {}", e.getMessage(), e);
            return AuthResult.reject("internal auth error");
        }
    }

    public void fireClientConnected(String clientId, InetSocketAddress remote) {
        if (!hasEventHooks) return;
        for (MqttEventHook hook : eventHooks) {
            try { hook.onClientConnected(clientId, remote); }
            catch (Exception e) { log.warn("eventHook.onClientConnected 异常: {}", e.getMessage()); }
        }
    }

    public void fireClientDisconnected(String clientId, InetSocketAddress remote, MqttEventHook.DisconnectReason reason) {
        if (!hasEventHooks) return;
        for (MqttEventHook hook : eventHooks) {
            try { hook.onClientDisconnected(clientId, remote, reason); }
            catch (Exception e) { log.warn("eventHook.onClientDisconnected 异常: {}", e.getMessage()); }
        }
    }

    public void fireMessagePublish(String clientId, String topic, MqttQoS qos, boolean retain, int payloadSize) {
        if (!hasEventHooks) return;
        for (MqttEventHook hook : eventHooks) {
            try { hook.onMessagePublish(clientId, topic, qos, retain, payloadSize); }
            catch (Exception e) { log.warn("eventHook.onMessagePublish 异常: {}", e.getMessage()); }
        }
    }

    public void fireMessageDelivered(String clientId, String topic, int subscriberCount) {
        if (!hasEventHooks) return;
        for (MqttEventHook hook : eventHooks) {
            try { hook.onMessageDelivered(clientId, topic, subscriberCount); }
            catch (Exception e) { log.warn("eventHook.onMessageDelivered 异常: {}", e.getMessage()); }
        }
    }

    public void fireClientSubscribe(String clientId, List<String> topicFilters) {
        if (!hasEventHooks) return;
        for (MqttEventHook hook : eventHooks) {
            try { hook.onClientSubscribe(clientId, topicFilters); }
            catch (Exception e) { log.warn("eventHook.onClientSubscribe 异常: {}", e.getMessage()); }
        }
    }

    public void fireClientUnsubscribe(String clientId, List<String> topicFilters) {
        if (!hasEventHooks) return;
        for (MqttEventHook hook : eventHooks) {
            try { hook.onClientUnsubscribe(clientId, topicFilters); }
            catch (Exception e) { log.warn("eventHook.onClientUnsubscribe 异常: {}", e.getMessage()); }
        }
    }

    public void fireConnectRejected(String clientId, InetSocketAddress remote, String reason) {
        if (!hasEventHooks) return;
        for (MqttEventHook hook : eventHooks) {
            try { hook.onConnectRejected(clientId, remote, reason); }
            catch (Exception e) { log.warn("eventHook.onConnectRejected 异常: {}", e.getMessage()); }
        }
    }

    public void fireClientKicked(String clientId, InetSocketAddress remote) {
        if (!hasEventHooks) return;
        for (MqttEventHook hook : eventHooks) {
            try { hook.onClientKicked(clientId, remote); }
            catch (Exception e) { log.warn("eventHook.onClientKicked 异常: {}", e.getMessage()); }
        }
    }
    
    /**
     * 检查客户端 ACL 权限。
     */
    public AclResult checkAcl(AclCheckContext context) {
        if (aclHook != null) {
            try {
                return aclHook.checkAcl(context);
            } catch (Exception e) {
                log.error("ACL 钩子异常: {}", e.getMessage(), e);
                return AclResult.deny("internal acl error");
            }
        }
        // 默认允许所有
        return AclResult.allow();
    }
}
