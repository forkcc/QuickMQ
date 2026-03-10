package io.quickmq.data.repository;

import io.quickmq.data.entity.SubscriptionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SubscriptionRepository extends JpaRepository<SubscriptionEntity, Long> {
    List<SubscriptionEntity> findByClientId(String clientId);
    void deleteByClientId(String clientId);
    void deleteByClientIdAndTopicFilter(String clientId, String topicFilter);
}
