package io.quickmq.mqtt.hook.http;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.handler.codec.mqtt.MqttQoS;
import io.quickmq.mqtt.hook.MqttEventHook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 通过 HTTP POST JSON 异步推送 MQTT 审计事件到外部服务。
 * <p>
 * 请求体:
 * <pre>{@code
 * {
 *   "action": "client_connected",
 *   "clientId": "client-1",
 *   "remoteAddress": "192.168.1.100",
 *   "remotePort": 54321,
 *   ... (action-specific fields)
 * }
 * }</pre>
 * 所有请求异步发送（fire-and-forget），失败只记日志不影响业务。
 */
public class HttpEventHook implements MqttEventHook {

    private static final Logger log = LoggerFactory.getLogger(HttpEventHook.class);

    private final URI uri;
    private final HttpClient httpClient;
    private final ObjectMapper mapper;
    private final Duration timeout;

    public HttpEventHook(String url, int timeoutMs, HttpClient httpClient, ObjectMapper mapper) {
        this.uri = URI.create(url);
        this.timeout = Duration.ofMillis(timeoutMs);
        this.httpClient = httpClient;
        this.mapper = mapper;
    }

    @Override
    public void onClientConnected(String clientId, InetSocketAddress remoteAddress) {
        Map<String, Object> body = newBody("client_connected", clientId, remoteAddress);
        postAsync(body);
    }

    @Override
    public void onClientDisconnected(String clientId, InetSocketAddress remoteAddress, DisconnectReason reason) {
        Map<String, Object> body = newBody("client_disconnected", clientId, remoteAddress);
        body.put("reason", reason.name());
        postAsync(body);
    }

    @Override
    public void onMessagePublish(String clientId, String topic, MqttQoS qos, boolean retain, int payloadSize) {
        Map<String, Object> body = newBody("message_publish", clientId, null);
        body.put("topic", topic);
        body.put("qos", qos.value());
        body.put("retain", retain);
        body.put("payloadSize", payloadSize);
        postAsync(body);
    }

    @Override
    public void onMessageDelivered(String clientId, String topic, int subscriberCount) {
        Map<String, Object> body = newBody("message_delivered", clientId, null);
        body.put("topic", topic);
        body.put("subscriberCount", subscriberCount);
        postAsync(body);
    }

    @Override
    public void onClientSubscribe(String clientId, List<String> topicFilters) {
        Map<String, Object> body = newBody("client_subscribe", clientId, null);
        body.put("topicFilters", topicFilters);
        postAsync(body);
    }

    @Override
    public void onClientUnsubscribe(String clientId, List<String> topicFilters) {
        Map<String, Object> body = newBody("client_unsubscribe", clientId, null);
        body.put("topicFilters", topicFilters);
        postAsync(body);
    }

    @Override
    public void onConnectRejected(String clientId, InetSocketAddress remoteAddress, String reason) {
        Map<String, Object> body = newBody("connect_rejected", clientId, remoteAddress);
        body.put("reason", reason);
        postAsync(body);
    }

    @Override
    public void onClientKicked(String clientId, InetSocketAddress remoteAddress) {
        Map<String, Object> body = newBody("client_kicked", clientId, remoteAddress);
        postAsync(body);
    }

    private Map<String, Object> newBody(String action, String clientId, InetSocketAddress remote) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("action", action);
        body.put("clientId", clientId);
        if (remote != null) {
            body.put("remoteAddress", remote.getAddress().getHostAddress());
            body.put("remotePort", remote.getPort());
        }
        body.put("timestamp", System.currentTimeMillis());
        return body;
    }

    private void postAsync(Map<String, Object> body) {
        try {
            String json = mapper.writeValueAsString(body);
            HttpRequest request = HttpRequest.newBuilder(uri)
                    .header("Content-Type", "application/json")
                    .timeout(timeout)
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();

            httpClient.sendAsync(request, HttpResponse.BodyHandlers.discarding())
                    .whenComplete((resp, err) -> {
                        if (err != null) {
                            log.warn("HTTP event hook 推送失败 [{}]: {}", body.get("action"), err.getMessage());
                        } else if (resp.statusCode() != 200) {
                            log.warn("HTTP event hook 返回非 200 [{}]: {}", body.get("action"), resp.statusCode());
                        }
                    });
        } catch (Exception e) {
            log.warn("HTTP event hook 序列化失败 [{}]: {}", body.get("action"), e.getMessage());
        }
    }

    @Override
    public String toString() {
        return "HttpEventHook{url=" + uri + "}";
    }
}
