package io.quickmq.data.entity;

import jakarta.persistence.*;

/**
 * 持久订阅：cleanSession=false 的客户端断开后保留订阅关系。
 */
@Entity
@Table(name = "subscription", indexes = {
        @Index(name = "idx_sub_client", columnList = "clientId"),
        @Index(name = "idx_sub_client_filter", columnList = "clientId, topicFilter", unique = true)
})
public class SubscriptionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 256)
    private String clientId;

    @Column(nullable = false, length = 512)
    private String topicFilter;

    @Column(nullable = false)
    private int qos;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getClientId() { return clientId; }
    public void setClientId(String clientId) { this.clientId = clientId; }

    public String getTopicFilter() { return topicFilter; }
    public void setTopicFilter(String topicFilter) { this.topicFilter = topicFilter; }

    public int getQos() { return qos; }
    public void setQos(int qos) { this.qos = qos; }
}
