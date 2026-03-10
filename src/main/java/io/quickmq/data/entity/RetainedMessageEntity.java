package io.quickmq.data.entity;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * 保留消息持久化：Broker 重启后仍可向新订阅者下发。
 */
@Entity
@Table(name = "retained_message", indexes = {
        @Index(name = "idx_retained_topic", columnList = "topic", unique = true)
})
public class RetainedMessageEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 512)
    private String topic;

    @Lob
    @Column(nullable = false)
    private byte[] payload;

    @Column(nullable = false)
    private int qos;

    @Column(nullable = false)
    private Instant updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getTopic() { return topic; }
    public void setTopic(String topic) { this.topic = topic; }

    public byte[] getPayload() { return payload; }
    public void setPayload(byte[] payload) { this.payload = payload; }

    public int getQos() { return qos; }
    public void setQos(int qos) { this.qos = qos; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
