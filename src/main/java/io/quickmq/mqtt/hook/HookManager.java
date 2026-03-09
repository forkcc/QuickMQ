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
import java.util.Collections;
import java.util.List;

/**
 * Hook 管理器：统一调度认证钩子和审计事件钩子。
 * <p>
 * 百万连接优化：
 * <ul>
 *   <li>{@link #hasEventHooks} 快速路径标记——无 event hook 时所有 fire* 方法立即返回，零开销</li>
 *   <li>eventHooks 使用固定大小数组遍历，避免 Iterator 分配</li>
 * </ul>
 */
@Component
public class HookManager {

    private static final Logger log = LoggerFactory.getLogger(HookManager.class);

    private final MqttAuthHook authHook;
    private final MqttEventHook[] eventHooks;
    private final boolean hasEventHooks;

    public HookManager(
            MqttProperties mqttProperties,
            @org.springframework.lang.Nullable MqttAuthHook beanAuthHook,
            @org.springframework.lang.Nullable List<MqttEventHook> beanEventHooks
    ) {
        HookProperties hookProps = mqttProperties.getHooks();
        int timeoutMs = hookProps.getHttpTimeoutMs();

        HttpClient httpClient = null;
        ObjectMapper mapper = null;

        boolean needHttp = !hookProps.getAuthUrl().isEmpty() || !hookProps.getEventUrl().isEmpty();
        if (needHttp) {
            httpClient = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofMillis(timeoutMs))
                    .build();
            mapper = new ObjectMapper();
        }

        // ---------- Auth Hook ----------
        MqttAuthHook resolvedAuth = null;
        if (!hookProps.getAuthUrl().isEmpty()) {
            resolvedAuth = new HttpAuthHook(hookProps.getAuthUrl(), timeoutMs, httpClient, mapper);
            log.info("认证钩子: HTTP {}", hookProps.getAuthUrl());
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
            log.info("事件钩子: HTTP {}", hookProps.getEventUrl());
        }
        if (beanEventHooks != null) merged.addAll(beanEventHooks);
        this.eventHooks = merged.toArray(new MqttEventHook[0]);
        this.hasEventHooks = this.eventHooks.length > 0;

        log.info("Hook 初始化完成: authHook={}, eventHooks={}个",
                this.authHook.getClass().getSimpleName(), this.eventHooks.length);
    }

    public boolean hasEventHooks() {
        return hasEventHooks;
    }

    // ==================== 认证（强制） ====================

    public AuthResult authenticate(ConnectContext ctx) {
        try {
            return authHook.authenticate(ctx);
        } catch (Exception e) {
            log.error("认证钩子异常，拒绝连接: {}", e.getMessage(), e);
            return AuthResult.reject("internal auth error");
        }
    }

    // ==================== 审计事件（可选，无 hook 时零开销） ====================

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
}
