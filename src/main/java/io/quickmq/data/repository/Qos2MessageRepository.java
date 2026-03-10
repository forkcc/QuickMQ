package io.quickmq.data.repository;

import io.quickmq.data.entity.Qos2MessageEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface Qos2MessageRepository extends JpaRepository<Qos2MessageEntity, Long> {

    Optional<Qos2MessageEntity> findByClientIdAndMessageId(String clientId, Integer messageId);

    List<Qos2MessageEntity> findByClientId(String clientId);

    List<Qos2MessageEntity> findByClientIdAndState(String clientId, Qos2MessageEntity.State state);

    @Modifying
    @Query("DELETE FROM Qos2MessageEntity q WHERE q.clientId = :clientId AND q.messageId = :messageId")
    void deleteByClientIdAndMessageId(@Param("clientId") String clientId, @Param("messageId") Integer messageId);

    @Modifying
    @Query("DELETE FROM Qos2MessageEntity q WHERE q.clientId = :clientId")
    void deleteByClientId(@Param("clientId") String clientId);

    @Modifying
    @Query("DELETE FROM Qos2MessageEntity q WHERE q.updatedAt < :expiryTime")
    void deleteExpired(@Param("expiryTime") Instant expiryTime);
}