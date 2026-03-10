package io.quickmq.mqtt.store;

import io.netty.channel.Channel;
import io.netty.handler.codec.mqtt.MqttPublishMessage;
import io.quickmq.data.PersistenceService;
import io.quickmq.data.entity.Qos2MessageEntity;
import io.quickmq.mqtt.ChannelAttributes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * QoS 2 消息状态管理器，用于跟踪 QoS 2 消息的中间状态。
 */
public class Qos2MessageStore {
    
    private static final Logger log = LoggerFactory.getLogger(Qos2MessageStore.class);
    
    private final PersistenceService persistence;
    private final Map<String, Map<Integer, Qos2MessageEntity.State>> inMemoryCache = new ConcurrentHashMap<>();
    
    public Qos2MessageStore(PersistenceService persistence) {
        this.persistence = persistence;
    }
    
    public void storePubRecReceived(Channel channel, MqttPublishMessage publish) {
        String clientId = channel.attr(ChannelAttributes.CLIENT_ID).get();
        if (clientId == null) return;
        
        int messageId = publish.variableHeader().packetId();
        String topic = publish.variableHeader().topicName();
        int qos = publish.fixedHeader().qosLevel().value();
        
        byte[] payload = new byte[publish.payload().readableBytes()];
        publish.payload().getBytes(publish.payload().readerIndex(), payload);
        
        persistence.saveQos2MessageAsync(clientId, messageId, topic, payload, qos, Qos2MessageEntity.State.PUBREC_RECEIVED);
        
        inMemoryCache.computeIfAbsent(clientId, k -> new ConcurrentHashMap<>())
                    .put(messageId, Qos2MessageEntity.State.PUBREC_RECEIVED);
        
        log.debug("存储 QoS 2 PUBREC 状态: clientId={}, messageId={}", clientId, messageId);
    }
    
    public void updateToPubRelReceived(String clientId, int messageId) {
        persistence.updateQos2MessageStateAsync(clientId, messageId, Qos2MessageEntity.State.PUBREL_RECEIVED);
        
        Map<Integer, Qos2MessageEntity.State> clientStates = inMemoryCache.get(clientId);
        if (clientStates != null) {
            clientStates.put(messageId, Qos2MessageEntity.State.PUBREL_RECEIVED);
        }
        
        log.debug("更新 QoS 2 状态为 PUBREL: clientId={}, messageId={}", clientId, messageId);
    }
    
    public void remove(String clientId, int messageId) {
        persistence.deleteQos2MessageAsync(clientId, messageId);
        
        Map<Integer, Qos2MessageEntity.State> clientStates = inMemoryCache.get(clientId);
        if (clientStates != null) {
            clientStates.remove(messageId);
        }
        
        log.debug("移除 QoS 2 消息状态: clientId={}, messageId={}", clientId, messageId);
    }
    
    public void removeAllForClient(String clientId) {
        persistence.deleteAllQos2MessagesAsync(clientId);
        inMemoryCache.remove(clientId);
        
        log.debug("移除客户端所有 QoS 2 消息状态: clientId={}", clientId);
    }
    
    public boolean hasPubRecReceived(String clientId, int messageId) {
        Map<Integer, Qos2MessageEntity.State> clientStates = inMemoryCache.get(clientId);
        if (clientStates != null) {
            Qos2MessageEntity.State state = clientStates.get(messageId);
            return state == Qos2MessageEntity.State.PUBREC_RECEIVED;
        }
        return false;
    }
    
    public boolean hasPubRelReceived(String clientId, int messageId) {
        Map<Integer, Qos2MessageEntity.State> clientStates = inMemoryCache.get(clientId);
        if (clientStates != null) {
            Qos2MessageEntity.State state = clientStates.get(messageId);
            return state == Qos2MessageEntity.State.PUBREL_RECEIVED;
        }
        return false;
    }
    
    public void loadClientStates(String clientId) {
        if (persistence != null) {
            persistence.findQos2Messages(clientId).forEach(entity -> {
                inMemoryCache.computeIfAbsent(clientId, k -> new ConcurrentHashMap<>())
                           .put(entity.getMessageId(), entity.getState());
            });
            log.debug("加载客户端 QoS 2 消息状态: clientId={}, count={}", clientId, 
                     inMemoryCache.getOrDefault(clientId, Map.of()).size());
        }
    }
    
    public void cleanupExpired(int expiryHours) {
        if (persistence != null) {
            persistence.cleanupExpiredQos2MessagesAsync(expiryHours);
        }
    }
}