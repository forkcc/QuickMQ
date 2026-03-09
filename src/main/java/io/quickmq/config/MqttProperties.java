package io.quickmq.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * MQTT 相关配置，绑定 application.yml 中 mqtt.*。
 * 支持多 TCP 端口、多 WebSocket 端口。
 */
@Component
@ConfigurationProperties(prefix = "mqtt")
public class MqttProperties {

    /** MQTT TCP 监听端口列表。为空时默认 [1883]。 */
    private List<Integer> tcpPorts = new ArrayList<>();

    /** MQTT over WebSocket 监听端口列表。 */
    private List<Integer> wsPorts = new ArrayList<>();

    /** WebSocket 子协议路径，如 /mqtt。 */
    private String wsPath = "/mqtt";

    public List<Integer> getTcpPorts() {
        return tcpPorts;
    }

    public void setTcpPorts(List<Integer> tcpPorts) {
        this.tcpPorts = tcpPorts != null ? tcpPorts : new ArrayList<>();
    }

    public List<Integer> getWsPorts() {
        return wsPorts;
    }

    public void setWsPorts(List<Integer> wsPorts) {
        this.wsPorts = wsPorts != null ? wsPorts : new ArrayList<>();
    }

    public String getWsPath() {
        return wsPath;
    }

    public void setWsPath(String wsPath) {
        this.wsPath = wsPath != null ? wsPath : "/mqtt";
    }

    /** 实际使用的 TCP 端口列表：tcp-ports 非空用 tcp-ports，否则默认 [1883]。 */
    public List<Integer> resolveTcpPorts() {
        return Optional.ofNullable(tcpPorts).filter(l -> !l.isEmpty()).map(List::copyOf).orElse(List.of(1883));
    }

    /** 实际使用的 WebSocket 端口列表（只读）。 */
    public List<Integer> resolveWsPorts() {
        return Optional.ofNullable(wsPorts).filter(l -> !l.isEmpty()).map(List::copyOf).orElse(List.of());
    }
}
