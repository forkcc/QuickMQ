package io.quickmq.data.entity;

import jakarta.persistence.*;

import java.time.Instant;

/**
 * QoS 2 消息状态实体，用于持久化 QoS 2 消息的中间状态。
 */
@Entity
@Table(name = "qos2_message")
public class Qos2MessageEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "client_id", nullable = false, length = 64)
    private String clientId;

    @Column(name = "message_id", nullable = false)
    private Integer messageId;

    @Column(name = "topic", nullable = false, length = 1024)
    private String topic;

    @Lob
    @Column(name = "payload")
    private byte[] payload;

    @Column(name = "qos", nullable = false)
    private Integer qos;

    @Column(name = "state", nullable = false, length = 16)
    @Enumerated(EnumType.STRING)
    private State state;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public enum State {
        PUBREC_RECEIVED,    // 收到 PUBLISH，已发送 PUBREC
        PUBREL_RECEIVED     // 收到 PUBREL，已发送 PUBCOMP
    }

    public Qos2MessageEntity() {
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public Qos2MessageEntity(String clientId, Integer messageId, String topic, byte[] payload, Integer qos, State state) {
        this();
        this.clientId = clientId;
        this.messageId = messageId;
        this.topic = topic;
        this.payload = payload;
        this.qos = qos;
        this.state = state;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public Integer getMessageId() {
        return messageId;
    }

    public void setMessageId(Integer messageId) {
        this.messageId = messageId;
    }

    public String getTopic() {
        return topic;
    }

    public void setTopic(String topic) {
        this.topic = topic;
    }

    public byte[] getPayload() {
        return payload;
    }

    public void setPayload(byte[] payload) {
        this.payload = payload;
    }

    public Integer getQos() {
        return qos;
    }

    public void setQos(Integer qos) {
        this.qos = qos;
    }

    public State getState() {
        return state;
    }

    public void setState(State state) {
        this.state = state;
        this.updatedAt = Instant.now();
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = Instant.now();
    }
}