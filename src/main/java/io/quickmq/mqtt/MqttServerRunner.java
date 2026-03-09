package io.quickmq.mqtt;

import io.quickmq.config.MqttProperties;
import io.quickmq.mqtt.hook.HookManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * 应用启动后拉起 Netty MQTT 服务。
 */
@Component
public class MqttServerRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(MqttServerRunner.class);
    private final MqttProperties mqttProperties;
    private final MqttServer mqttServer;
    private final HookManager hookManager;

    public MqttServerRunner(MqttProperties mqttProperties, MqttServer mqttServer, HookManager hookManager) {
        this.mqttProperties = mqttProperties;
        this.mqttServer = mqttServer;
        this.hookManager = hookManager;
    }

    @Override
    public void run(String... args) throws Exception {
        log.info("启动 MQTT 服务，TCP 端口: {}, WebSocket 端口: {}",
                mqttProperties.resolveTcpPorts(), mqttProperties.resolveWsPorts());
        mqttServer.start(mqttProperties, hookManager);
    }
}
