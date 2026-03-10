package io.quickmq.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MqttPropertiesTest {

    @Test
    void defaults() {
        MqttProperties props = new MqttProperties();
        assertEquals(List.of(), props.getTcpPorts());
        assertEquals(List.of(), props.getWsPorts());
        assertEquals("/mqtt", props.getWsPath());
        assertEquals(262144, props.getMaxMessageSize());
        assertEquals(65536, props.getWsMaxHttpBodySize());
        assertFalse(props.isDefaultSessionPresent());
        assertEquals(0, props.getMaxKeepaliveSeconds());
        assertEquals(60, props.getDefaultKeepaliveSeconds());
        assertEquals(10, props.getConnectTimeoutSeconds());
        assertFalse(props.isProxyProtocol());
        assertNotNull(props.getHooks());
    }

    @Test
    void resolveTcpPorts_defaultWhenEmpty() {
        MqttProperties props = new MqttProperties();
        assertEquals(List.of(1883), props.resolveTcpPorts());
    }

    @Test
    void resolveTcpPorts_returnsConfigured() {
        MqttProperties props = new MqttProperties();
        props.setTcpPorts(List.of(1884, 1885));
        assertEquals(List.of(1884, 1885), props.resolveTcpPorts());
    }

    @Test
    void resolveWsPorts_defaultEmpty() {
        MqttProperties props = new MqttProperties();
        assertEquals(List.of(), props.resolveWsPorts());
    }

    @ParameterizedTest(name = "client={0}, default={1}, max={2} → idle={3}")
    @CsvSource({
            "60,  60, 0,   90",    // 60 * 1.5 = 90
            "0,   60, 0,   90",    // client=0, use default=60 → 60*1.5=90
            "0,   0,  0,   0",     // client=0, default=0 → no detection
            "120, 60, 100, 150",   // client=120 > max=100 → use 100*1.5=150
            "60,  60, 100, 90",    // client=60 < max=100 → 60*1.5=90
            "0,   60, 30,  45",    // client=0 → default=60 > max=30 → 30*1.5=45
            "10,  60, 0,   15",    // 10*1.5=15
    })
    void resolveIdleSeconds(int clientKeepalive, int defaultKeepalive, int maxKeepalive, int expectedIdle) {
        MqttProperties props = new MqttProperties();
        props.setDefaultKeepaliveSeconds(defaultKeepalive);
        props.setMaxKeepaliveSeconds(maxKeepalive);
        assertEquals(expectedIdle, props.resolveIdleSeconds(clientKeepalive));
    }

    @Test
    void setters_nullSafe() {
        MqttProperties props = new MqttProperties();
        props.setTcpPorts(null);
        assertNotNull(props.getTcpPorts());
        props.setWsPorts(null);
        assertNotNull(props.getWsPorts());
        props.setWsPath(null);
        assertEquals("/mqtt", props.getWsPath());
        props.setHooks(null);
        assertNotNull(props.getHooks());
    }

    @Test
    void setters_negativeClamped() {
        MqttProperties props = new MqttProperties();
        props.setMaxKeepaliveSeconds(-1);
        assertEquals(0, props.getMaxKeepaliveSeconds());
        props.setDefaultKeepaliveSeconds(-5);
        assertEquals(0, props.getDefaultKeepaliveSeconds());
        props.setConnectTimeoutSeconds(-1);
        assertEquals(0, props.getConnectTimeoutSeconds());
        props.setMaxMessageSize(-1);
        assertEquals(262144, props.getMaxMessageSize());
        props.setWsMaxHttpBodySize(0);
        assertEquals(65536, props.getWsMaxHttpBodySize());
    }
}
