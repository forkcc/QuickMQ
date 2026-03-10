package io.quickmq.data.repository;

import io.quickmq.data.entity.RetainedMessageEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RetainedMessageRepository extends JpaRepository<RetainedMessageEntity, Long> {
    Optional<RetainedMessageEntity> findByTopic(String topic);
    void deleteByTopic(String topic);
}
