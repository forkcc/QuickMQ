package io.quickmq.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * MQTT 业务可配置项，绑定 application.yml 中 mqtt.*。
 */
@Component
@ConfigurationProperties(prefix = "mqtt")
public class MqttProperties {

    /** MQTT TCP 监听端口列表。为空时默认 [1883]。 */
    private List<Integer> tcpPorts = new ArrayList<>();

    /** MQTT over WebSocket 监听端口列表。为空则不启 WebSocket。 */
    private List<Integer> wsPorts = new ArrayList<>();

    /** WebSocket 子协议路径。 */
    private String wsPath = "/mqtt";

    /** 单条 MQTT 报文最大字节数（解码限制）。 */
    private int maxMessageSize = 262144;

    /** WebSocket HTTP 聚合体最大 body 大小（字节）。 */
    private int wsMaxHttpBodySize = 65536;

    /** CONNACK 默认 sessionPresent（无持久会话时为 false）。 */
    private boolean defaultSessionPresent = false;

    // ==================== Keepalive ====================

    /**
     * 服务端允许的最大 Keepalive 秒数。0 = 不限制。
     * 客户端 CONNECT 中声明的 keepalive 超过此值时，服务端按此值执行。
     */
    private int maxKeepaliveSeconds = 0;

    /**
     * 客户端 CONNECT 中 keepalive=0 时服务端使用的默认值（秒）。
     * 0 = 客户端声明 0 则真正不做空闲检测。
     */
    private int defaultKeepaliveSeconds = 60;

    /**
     * TCP 连接建立后等待 CONNECT 报文的超时秒数。超时未收到 CONNECT 即关闭。
     * 0 = 不限制（不推荐）。
     */
    private int connectTimeoutSeconds = 10;

    // ==================== Session Expiry ====================

    /**
     * 会话过期时间（小时）。cleanSession=false 的客户端断开连接后，其会话数据保留的时间。
     * 0 = 永不过期。
     */
    private int sessionExpiryHours = 24;

    /**
     * QoS 2 消息状态过期时间（小时）。未完成的 QoS 2 消息状态保留的时间。
     * 0 = 永不过期。
     */
    private int qos2MessageExpiryHours = 24;

    // ==================== Hooks ====================

    /** Hook 动态配置。 */
    private HookProperties hooks = new HookProperties();

    // ==================== Proxy Protocol ====================

    /**
     * 是否启用 HAProxy PROXY protocol（v1/v2）。
     * 启用后 Broker 前的代理（如 HAProxy/Nginx）可透传客户端真实 IP。
     * 默认关闭；未走代理时必须关闭，否则首包解析失败会断开连接。
     */
    private boolean proxyProtocol = false;

    // ==================== SSL/TLS ====================

    /**
     * MQTT over TLS/SSL 监听端口列表。为空则不启 SSL。
     */
    private List<Integer> sslPorts = new ArrayList<>();

    /**
     * WebSocket over TLS/SSL 监听端口列表。为空则不启 WSS。
     */
    private List<Integer> wssPorts = new ArrayList<>();

    /**
     * SSL 证书文件路径（PEM 格式）。
     */
    private String sslCertPath = "";

    /**
     * SSL 私钥文件路径（PEM 格式）。
     */
    private String sslKeyPath = "";

    /**
     * SSL 信任证书文件路径（PEM 格式，用于客户端证书验证）。
     */
    private String sslTrustCertPath = "";

    /**
     * 是否需要客户端证书验证（mTLS）。
     */
    private boolean sslClientAuth = false;

    // ==================== getter / setter ====================

    public List<Integer> getTcpPorts() { return tcpPorts; }
    public void setTcpPorts(List<Integer> tcpPorts) { this.tcpPorts = tcpPorts != null ? tcpPorts : new ArrayList<>(); }

    public List<Integer> getWsPorts() { return wsPorts; }
    public void setWsPorts(List<Integer> wsPorts) { this.wsPorts = wsPorts != null ? wsPorts : new ArrayList<>(); }

    public String getWsPath() { return wsPath; }
    public void setWsPath(String wsPath) { this.wsPath = wsPath != null ? wsPath : "/mqtt"; }

    public int getMaxMessageSize() { return maxMessageSize; }
    public void setMaxMessageSize(int maxMessageSize) { this.maxMessageSize = maxMessageSize > 0 ? maxMessageSize : 262144; }

    public int getWsMaxHttpBodySize() { return wsMaxHttpBodySize; }
    public void setWsMaxHttpBodySize(int wsMaxHttpBodySize) { this.wsMaxHttpBodySize = wsMaxHttpBodySize > 0 ? wsMaxHttpBodySize : 65536; }

    public boolean isDefaultSessionPresent() { return defaultSessionPresent; }
    public void setDefaultSessionPresent(boolean defaultSessionPresent) { this.defaultSessionPresent = defaultSessionPresent; }

    public int getMaxKeepaliveSeconds() { return maxKeepaliveSeconds; }
    public void setMaxKeepaliveSeconds(int v) { this.maxKeepaliveSeconds = Math.max(0, v); }

    public int getDefaultKeepaliveSeconds() { return defaultKeepaliveSeconds; }
    public void setDefaultKeepaliveSeconds(int v) { this.defaultKeepaliveSeconds = Math.max(0, v); }

    public int getConnectTimeoutSeconds() { return connectTimeoutSeconds; }
    public void setConnectTimeoutSeconds(int v) { this.connectTimeoutSeconds = Math.max(0, v); }

    public int getSessionExpiryHours() { return sessionExpiryHours; }
    public void setSessionExpiryHours(int v) { this.sessionExpiryHours = Math.max(0, v); }

    public int getQos2MessageExpiryHours() { return qos2MessageExpiryHours; }
    public void setQos2MessageExpiryHours(int v) { this.qos2MessageExpiryHours = Math.max(0, v); }

    public boolean isProxyProtocol() { return proxyProtocol; }
    public void setProxyProtocol(boolean proxyProtocol) { this.proxyProtocol = proxyProtocol; }

    public List<Integer> getSslPorts() { return sslPorts; }
    public void setSslPorts(List<Integer> sslPorts) { this.sslPorts = sslPorts != null ? sslPorts : new ArrayList<>(); }

    public List<Integer> getWssPorts() { return wssPorts; }
    public void setWssPorts(List<Integer> wssPorts) { this.wssPorts = wssPorts != null ? wssPorts : new ArrayList<>(); }

    public String getSslCertPath() { return sslCertPath; }
    public void setSslCertPath(String sslCertPath) { this.sslCertPath = sslCertPath != null ? sslCertPath : ""; }

    public String getSslKeyPath() { return sslKeyPath; }
    public void setSslKeyPath(String sslKeyPath) { this.sslKeyPath = sslKeyPath != null ? sslKeyPath : ""; }

    public String getSslTrustCertPath() { return sslTrustCertPath; }
    public void setSslTrustCertPath(String sslTrustCertPath) { this.sslTrustCertPath = sslTrustCertPath != null ? sslTrustCertPath : ""; }

    public boolean isSslClientAuth() { return sslClientAuth; }
    public void setSslClientAuth(boolean sslClientAuth) { this.sslClientAuth = sslClientAuth; }

    public HookProperties getHooks() { return hooks; }
    public void setHooks(HookProperties hooks) { this.hooks = hooks != null ? hooks : new HookProperties(); }

    public List<Integer> resolveTcpPorts() {
        return Optional.ofNullable(tcpPorts).filter(l -> !l.isEmpty()).map(List::copyOf).orElse(List.of(1883));
    }

    public List<Integer> resolveWsPorts() {
        return Optional.ofNullable(wsPorts).filter(l -> !l.isEmpty()).map(List::copyOf).orElse(List.of());
    }

    public List<Integer> resolveSslPorts() {
        return Optional.ofNullable(sslPorts).filter(l -> !l.isEmpty()).map(List::copyOf).orElse(List.of());
    }

    public List<Integer> resolveWssPorts() {
        return Optional.ofNullable(wssPorts).filter(l -> !l.isEmpty()).map(List::copyOf).orElse(List.of());
    }

    /**
     * 根据客户端声明的 keepalive 和服务端配置，计算实际的读空闲超时秒数。
     * MQTT-3.1.2-24: 服务端在 1.5 倍 keepalive 时间内未收到报文须断开。
     *
     * @param clientKeepalive 客户端 CONNECT 中声明的 keepalive（秒）
     * @return 实际超时秒数，0 = 不检测
     */
    public int resolveIdleSeconds(int clientKeepalive) {
        int effective = clientKeepalive;
        if (effective == 0) {
            effective = defaultKeepaliveSeconds;
        }
        if (maxKeepaliveSeconds > 0 && effective > maxKeepaliveSeconds) {
            effective = maxKeepaliveSeconds;
        }
        if (effective <= 0) return 0;
        return (int) Math.ceil(effective * 1.5);
    }
}
