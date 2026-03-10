package io.quickmq.data;

import io.quickmq.data.entity.ClientSessionEntity;
import io.quickmq.data.entity.RetainedMessageEntity;
import io.quickmq.data.entity.SubscriptionEntity;
import io.quickmq.data.repository.ClientSessionRepository;
import io.quickmq.data.repository.RetainedMessageRepository;
import io.quickmq.data.repository.SubscriptionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 数据持久化服务：所有 JPA 操作通过虚拟线程异步执行，不阻塞 Netty EventLoop。
 * <p>
 * 写操作（save/delete）fire-and-forget，读操作同步返回（调用方需在非 EventLoop 线程调用）。
 */
@Service
public class PersistenceService {

    private static final Logger log = LoggerFactory.getLogger(PersistenceService.class);

    private final ClientSessionRepository sessionRepo;
    private final SubscriptionRepository subscriptionRepo;
    private final RetainedMessageRepository retainedRepo;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    public PersistenceService(ClientSessionRepository sessionRepo,
                              SubscriptionRepository subscriptionRepo,
                              RetainedMessageRepository retainedRepo) {
        this.sessionRepo = sessionRepo;
        this.subscriptionRepo = subscriptionRepo;
        this.retainedRepo = retainedRepo;
    }

    // ==================== 会话 ====================

    public void saveSessionAsync(String clientId, String username, boolean cleanSession) {
        executor.execute(() -> {
            try {
                saveSession(clientId, username, cleanSession);
            } catch (Exception e) {
                log.warn("持久化会话失败 clientId={}: {}", clientId, e.getMessage());
            }
        });
    }

    @Transactional
    public void saveSession(String clientId, String username, boolean cleanSession) {
        ClientSessionEntity entity = sessionRepo.findByClientId(clientId).orElse(null);
        if (entity == null) {
            entity = new ClientSessionEntity();
            entity.setClientId(clientId);
            entity.setCreatedAt(Instant.now());
        }
        entity.setUsername(username);
        entity.setCleanSession(cleanSession);
        entity.setLastSeenAt(Instant.now());
        sessionRepo.save(entity);
    }

    public void deleteSessionAsync(String clientId) {
        executor.execute(() -> {
            try {
                deleteSession(clientId);
            } catch (Exception e) {
                log.warn("删除会话失败 clientId={}: {}", clientId, e.getMessage());
            }
        });
    }

    @Transactional
    public void deleteSession(String clientId) {
        sessionRepo.deleteByClientId(clientId);
    }

    // ==================== 订阅 ====================

    public void saveSubscriptionAsync(String clientId, String topicFilter, int qos) {
        executor.execute(() -> {
            try {
                saveSubscription(clientId, topicFilter, qos);
            } catch (Exception e) {
                log.warn("持久化订阅失败 clientId={} filter={}: {}", clientId, topicFilter, e.getMessage());
            }
        });
    }

    @Transactional
    public void saveSubscription(String clientId, String topicFilter, int qos) {
        List<SubscriptionEntity> existing = subscriptionRepo.findByClientId(clientId);
        SubscriptionEntity entity = existing.stream()
                .filter(s -> s.getTopicFilter().equals(topicFilter))
                .findFirst().orElse(null);
        if (entity == null) {
            entity = new SubscriptionEntity();
            entity.setClientId(clientId);
            entity.setTopicFilter(topicFilter);
        }
        entity.setQos(qos);
        subscriptionRepo.save(entity);
    }

    public void deleteSubscriptionAsync(String clientId, String topicFilter) {
        executor.execute(() -> {
            try {
                deleteSubscription(clientId, topicFilter);
            } catch (Exception e) {
                log.warn("删除订阅失败 clientId={} filter={}: {}", clientId, topicFilter, e.getMessage());
            }
        });
    }

    @Transactional
    public void deleteSubscription(String clientId, String topicFilter) {
        subscriptionRepo.deleteByClientIdAndTopicFilter(clientId, topicFilter);
    }

    public void deleteAllSubscriptionsAsync(String clientId) {
        executor.execute(() -> {
            try {
                deleteAllSubscriptions(clientId);
            } catch (Exception e) {
                log.warn("删除全部订阅失败 clientId={}: {}", clientId, e.getMessage());
            }
        });
    }

    @Transactional
    public void deleteAllSubscriptions(String clientId) {
        subscriptionRepo.deleteByClientId(clientId);
    }

    /** 同步读取，供 CONNECT 恢复持久会话使用（在虚拟线程中调用）。 */
    @Transactional(readOnly = true)
    public List<SubscriptionEntity> findSubscriptions(String clientId) {
        return subscriptionRepo.findByClientId(clientId);
    }

    // ==================== 保留消息 ====================

    public void saveRetainedAsync(String topic, byte[] payload, int qos) {
        executor.execute(() -> {
            try {
                saveRetained(topic, payload, qos);
            } catch (Exception e) {
                log.warn("持久化保留消息失败 topic={}: {}", topic, e.getMessage());
            }
        });
    }

    @Transactional
    public void saveRetained(String topic, byte[] payload, int qos) {
        RetainedMessageEntity entity = retainedRepo.findByTopic(topic).orElse(null);
        if (entity == null) {
            entity = new RetainedMessageEntity();
            entity.setTopic(topic);
        }
        entity.setPayload(payload);
        entity.setQos(qos);
        entity.setUpdatedAt(Instant.now());
        retainedRepo.save(entity);
    }

    public void deleteRetainedAsync(String topic) {
        executor.execute(() -> {
            try {
                deleteRetained(topic);
            } catch (Exception e) {
                log.warn("删除保留消息失败 topic={}: {}", topic, e.getMessage());
            }
        });
    }

    @Transactional
    public void deleteRetained(String topic) {
        retainedRepo.deleteByTopic(topic);
    }
}
