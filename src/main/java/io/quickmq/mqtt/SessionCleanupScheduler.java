package io.quickmq.mqtt;

import io.quickmq.config.MqttProperties;
import io.quickmq.data.PersistenceService;
import io.quickmq.mqtt.store.Qos2MessageStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.util.Optional;

/**
 * 会话清理调度器，定期清理过期会话和 QoS 2 消息状态。
 */
@Component
public class SessionCleanupScheduler {
    
    private static final Logger log = LoggerFactory.getLogger(SessionCleanupScheduler.class);
    
    private final MqttProperties properties;
    private final PersistenceService persistence;
    private final Optional<Qos2MessageStore> qos2MessageStore;
    
    public SessionCleanupScheduler(MqttProperties properties, PersistenceService persistence,
                                   Optional<Qos2MessageStore> qos2MessageStore) {
        this.properties = properties;
        this.persistence = persistence;
        this.qos2MessageStore = qos2MessageStore;
    }
    
    /**
     * 每小时执行一次清理任务。
     */
    @Scheduled(fixedDelay = 3600000) // 1小时
    public void cleanupExpiredData() {
        int sessionExpiryHours = properties.getSessionExpiryHours();
        int qos2ExpiryHours = properties.getQos2MessageExpiryHours();
        
        if (sessionExpiryHours > 0) {
            log.debug("清理过期会话，过期时间: {}小时", sessionExpiryHours);
            persistence.cleanupExpiredSessionsAsync(sessionExpiryHours);
        }
        
        if (qos2ExpiryHours > 0) {
            log.debug("清理过期 QoS 2 消息状态，过期时间: {}小时", qos2ExpiryHours);
            persistence.cleanupExpiredQos2MessagesAsync(qos2ExpiryHours);
        }
        
        if (qos2ExpiryHours > 0) {
            qos2MessageStore.ifPresent(store -> store.cleanupExpired(qos2ExpiryHours));
        }
    }
    
    /**
     * 应用启动时执行一次清理。
     */
    @Scheduled(initialDelay = 10000, fixedDelay = Long.MAX_VALUE)
    public void cleanupOnStartup() {
        log.info("应用启动，执行初始数据清理");
        cleanupExpiredData();
    }
}