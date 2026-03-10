package io.quickmq.data.repository;

import io.quickmq.data.entity.ClientSessionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface ClientSessionRepository extends JpaRepository<ClientSessionEntity, Long> {
    Optional<ClientSessionEntity> findByClientId(String clientId);
    void deleteByClientId(String clientId);
    
    @Modifying
    @Query("DELETE FROM ClientSessionEntity s WHERE s.lastSeenAt < :expiryTime")
    void deleteExpired(@Param("expiryTime") Instant expiryTime);
}
