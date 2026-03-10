# QuickMQ v0.0.1

首个正式版本。基于 Spring Boot 3 + Netty 的高性能 MQTT Broker，面向单机百万长连接优化。

## 特性概览

- **MQTT 3.1.1**：CONNECT/CONNACK、PUBLISH、SUBSCRIBE/UNSUBSCRIBE、DISCONNECT、QoS 0/1/2、保留消息、遗嘱
- **多端口**：TCP 与 WebSocket 可同时监听，配置化端口与路径
- **服务端 Keepalive**：按客户端 keepalive 动态设置空闲超时（1.5 倍）
- **HAProxy PROXY Protocol**：可选开启，透传真实客户端 IP
- **认证与审计 Hook**：HTTP Webhook 或 Spring Bean，认证可放行所有，事件 8 类审计
- **百万连接优化**：Epoll、小缓冲、Recycler 对象池、Trie 订阅、write/flush 分离与背压

## 环境要求

- JDK 21+
- Maven 3.6+

## 快速开始

```bash
mvn clean package -DskipTests
java -jar target/quickmq-0.0.1.jar
```

生产环境建议使用 `deploy/start.sh`。详见 README。

## 附件说明

- `quickmq-0.0.1.jar`：可执行 JAR。
