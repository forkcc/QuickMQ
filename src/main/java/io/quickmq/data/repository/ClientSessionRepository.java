package io.quickmq.data.repository;

import io.quickmq.data.entity.ClientSessionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ClientSessionRepository extends JpaRepository<ClientSessionEntity, Long> {
    Optional<ClientSessionEntity> findByClientId(String clientId);
    void deleteByClientId(String clientId);
}
