package io.quickmq.mqtt.hook.http;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quickmq.mqtt.hook.AuthResult;
import io.quickmq.mqtt.hook.ConnectContext;
import io.quickmq.mqtt.hook.MqttAuthHook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 通过 HTTP POST JSON 调用外部服务做认证。
 * <p>
 * 请求体:
 * <pre>{@code
 * {
 *   "clientId": "client-1",
 *   "username": "admin",
 *   "password": "base64encoded",
 *   "remoteAddress": "192.168.1.100",
 *   "remotePort": 54321,
 *   "protocolVersion": 4,
 *   "cleanSession": true
 * }
 * }</pre>
 * 响应体:
 * <pre>{@code
 * {"result": "allow"}
 * {"result": "deny", "reason": "bad credentials"}
 * }</pre>
 * result 取值: allow / deny / ignore（ignore 等同 allow，用于链式认证）。
 * HTTP 异常或超时 → 拒绝连接（安全默认）。
 */
public class HttpAuthHook implements MqttAuthHook {

    private static final Logger log = LoggerFactory.getLogger(HttpAuthHook.class);

    private final URI uri;
    private final HttpClient httpClient;
    private final ObjectMapper mapper;
    private final Duration timeout;

    public HttpAuthHook(String url, int timeoutMs, HttpClient httpClient, ObjectMapper mapper) {
        this.uri = URI.create(url);
        this.timeout = Duration.ofMillis(timeoutMs);
        this.httpClient = httpClient;
        this.mapper = mapper;
    }

    @Override
    public AuthResult authenticate(ConnectContext ctx) {
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("clientId", ctx.clientId());
            body.put("username", ctx.username());
            body.put("password", ctx.password() != null ? Base64.getEncoder().encodeToString(ctx.password()) : null);
            body.put("remoteAddress", ctx.remoteAddress() != null ? ctx.remoteAddress().getAddress().getHostAddress() : null);
            body.put("remotePort", ctx.remoteAddress() != null ? ctx.remoteAddress().getPort() : 0);
            body.put("protocolVersion", ctx.protocolVersion());
            body.put("cleanSession", ctx.cleanSession());

            String json = mapper.writeValueAsString(body);

            HttpRequest request = HttpRequest.newBuilder(uri)
                    .header("Content-Type", "application/json")
                    .timeout(timeout)
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                log.warn("HTTP auth 返回非 200: status={}, body={}", response.statusCode(), response.body());
                return AuthResult.reject("http auth returned " + response.statusCode());
            }

            JsonNode root = mapper.readTree(response.body());
            String result = root.path("result").asText("deny");

            return switch (result) {
                case "allow", "ignore" -> AuthResult.accept();
                case "deny" -> {
                    String reason = root.path("reason").asText("denied by http auth");
                    yield AuthResult.reject(reason);
                }
                default -> {
                    log.warn("HTTP auth 未知 result: {}", result);
                    yield AuthResult.reject("unknown auth result: " + result);
                }
            };
        } catch (Exception e) {
            log.error("HTTP auth 调用失败: {}", e.getMessage());
            return AuthResult.reject("http auth error: " + e.getMessage());
        }
    }

    @Override
    public String toString() {
        return "HttpAuthHook{url=" + uri + "}";
    }
}
