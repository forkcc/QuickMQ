package io.quickmq.mqtt;

import io.quickmq.config.MqttProperties;
import io.quickmq.data.PersistenceService;
import io.quickmq.mqtt.hook.HookManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class MqttServerRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(MqttServerRunner.class);
    private final MqttProperties mqttProperties;
    private final MqttServer mqttServer;
    private final HookManager hookManager;
    private final PersistenceService persistence;

    public MqttServerRunner(MqttProperties mqttProperties, MqttServer mqttServer,
                            HookManager hookManager, PersistenceService persistence) {
        this.mqttProperties = mqttProperties;
        this.mqttServer = mqttServer;
        this.hookManager = hookManager;
        this.persistence = persistence;
    }

    @Override
    public void run(String... args) throws Exception {
        log.info("启动 MQTT 服务，TCP 端口: {}, WebSocket 端口: {}",
                mqttProperties.resolveTcpPorts(), mqttProperties.resolveWsPorts());
        mqttServer.start(mqttProperties, hookManager, persistence);
    }
}
