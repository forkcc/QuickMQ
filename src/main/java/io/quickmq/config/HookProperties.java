package io.quickmq.config;

/**
 * Hook 动态配置，绑定 mqtt.hooks.*。
 * <p>
 * 支持两种 Hook 注册方式（按优先级）：
 * <ol>
 *   <li><b>HTTP URL</b>：配置 auth-url / event-url，Broker 通过 HTTP POST JSON 调用外部服务</li>
 *   <li><b>Spring Bean</b>：实现接口并注册为 @Component</li>
 * </ol>
 */
public class HookProperties {

    /**
     * 认证 HTTP Webhook URL。
     * CONNECT 时 POST JSON，期望返回 {"result":"allow"} 或 {"result":"deny","reason":"..."} 。
     * 留空则不使用 HTTP 认证。
     */
    private String authUrl = "";

    /**
     * 审计事件 HTTP Webhook URL。
     * 所有事件异步 POST JSON 到此 URL（fire-and-forget）。
     * 留空则不使用 HTTP 事件推送。
     */
    private String eventUrl = "";

    /** HTTP Webhook 超时毫秒数（连接+读取）。 */
    private int httpTimeoutMs = 5000;

    // ==================== getter / setter ====================

    public String getAuthUrl() { return authUrl; }
    public void setAuthUrl(String v) { this.authUrl = v != null ? v.trim() : ""; }

    public String getEventUrl() { return eventUrl; }
    public void setEventUrl(String v) { this.eventUrl = v != null ? v.trim() : ""; }

    public int getHttpTimeoutMs() { return httpTimeoutMs; }
    public void setHttpTimeoutMs(int v) { this.httpTimeoutMs = v > 0 ? v : 5000; }
}
