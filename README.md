# QuickMQ

基于 Spring Boot 3 + Netty 的高性能 MQTT Broker，面向单机百万长连接优化，支持 TCP 与 WebSocket、可插拔认证与审计 Hook。

---

## 特性

- **MQTT 3.1.1**：CONNECT/CONNACK、PUBLISH、SUBSCRIBE/UNSUBSCRIBE、DISCONNECT、QoS 0/1/2、保留消息、遗嘱
- **多端口**：TCP 与 WebSocket 可同时监听，配置化端口与路径
- **服务端 Keepalive**：按客户端 CONNECT 声明的 keepalive 动态设置空闲超时（1.5 倍），可配置默认值与上限
- **HAProxy PROXY Protocol**：可选开启，透传真实客户端 IP
- **认证与审计 Hook**：认证支持 HTTP Webhook 或 Spring Bean，留空则放行所有；事件 Hook 支持 8 类审计事件（连入/断开/发布/投递/订阅等），可 HTTP 或 Bean，留空零开销
- **百万连接优化**：Socket 缓冲压至 4KB、Recycler 对象池、Trie 订阅索引、write/flush 分离与背压、RetainedStore Trie 索引

---

## 整体架构

Spring Boot 启动后由 `MqttServerRunner` 拉起 `MqttServer`，Netty 监听 TCP 与 WebSocket 端口；业务由共享的 `MqttBrokerHandler` 处理，内部维护订阅表（Trie）、保留消息、遗嘱及 clientId 映射，认证与事件由 `HookManager` 统一调度。

---

## 连接与认证流程

客户端发起 TCP/WebSocket 连接后，若开启 PROXY Protocol 则先解析真实 IP；随后由 `IdleStateHandler` 在约定时间内等待 CONNECT 报文。收到 CONNECT 后进入 `ConnectMessageHandler`，调用 `HookManager.authenticate`：若配置了 `auth-url` 则走 HTTP 认证，否则若存在 `MqttAuthHook` Bean 则走 Bean，否则使用默认实现**放行所有**。认证通过则回复 CONNACK 并将空闲检测替换为基于 keepalive×1.5 的读超时；拒绝则 CONNACK 失败并关闭连接。

---

## 发布与订阅流程

**订阅**：客户端发送 SUBSCRIBE，服务端在 `SubscriptionStore` 中按 topicFilter 写入 Trie（每个节点维护 Channel→QoS），并调用 `RetainedStore.deliverMatching` 向该连接下发已存在的保留消息。

**发布**：客户端发送 PUBLISH，服务端若为 retain 则更新 `RetainedStore`，再根据 topic 在 Trie 中收集所有匹配的订阅者（支持 `+`、`#` 通配），对每个订阅者先 `write`，再统一 `flush`；若某 Channel 不可写（`isWritable()==false`）则跳过该订阅者，避免慢消费者导致堆积。

多个客户端订阅同一主题时，Trie 中对应节点会保存多个 Channel，因此每条消息会向所有匹配的订阅者各投递一份。

---

## Hook 系统

- **认证（必选）**：优先级为 `auth-url` → Spring Bean `MqttAuthHook` → `DefaultAuthHook`（放行所有）。`auth-url` 留空时即使用默认放行，无需认证即可接入。
- **事件（可选）**：若配置 `event-url` 则启用 HTTP 事件上报；与所有 `MqttEventHook` Bean 合并，连入、断开、发布、投递、订阅、取消订阅、拒绝、踢出等 8 类事件均会触发。事件采用异步 fire-and-forget，不阻塞 Broker 主路径；`event-url` 留空则不上报，零开销。

---

## 快速开始

### 环境要求

- JDK 21+
- Maven 3.6+

### 编译与运行

```bash
mvn clean package -DskipTests
java -jar target/quickmq-0.0.1.jar
```

默认会读取 `src/main/resources/application.yml`；可指定 `--spring.config.location` 使用外部配置。

### 配置说明

主要配置在 `application.yml` 的 `mqtt` 下：

| 配置项 | 说明 | 示例 |
|--------|------|------|
| `tcp-ports` | TCP 监听端口列表，空则默认 1883 | `[1883]` |
| `ws-ports` | WebSocket 端口列表，空则不启 | `[8083]` |
| `ws-path` | WebSocket 路径 | `/mqtt` |
| `max-message-size` | 单条报文最大字节数 | `262144` |
| `default-keepalive-seconds` | 客户端 keepalive=0 时的默认值（秒） | `60` |
| `max-keepalive-seconds` | 服务端允许的最大 keepalive（秒），0 不限制 | `0` |
| `connect-timeout-seconds` | 连接建立后等待 CONNECT 的超时（秒） | `10` |
| `hooks.auth-url` | 认证 Webhook URL，留空则放行所有 | `""` |
| `hooks.event-url` | 事件 Webhook URL，留空则不上报 | `""` |
| `hooks.http-timeout-ms` | HTTP 超时（毫秒） | `5000` |
| `proxy-protocol` | 是否启用 HAProxy PROXY protocol | `false` |

---

## 百万连接部署

项目提供 `deploy/` 目录供生产环境参考：

- **deploy/sysctl-tuning.conf**：Linux 内核参数（如 `fs.file-max`、`tcp_rmem`/`tcp_wmem`、`somaxconn` 等），需按机器规格调整后加载。
- **deploy/limits.conf**：进程 `nofile` 等 ulimit 配置，放入 `/etc/security/limits.d/` 后重新登录生效。
- **deploy/start.sh**：示例 JVM 启动脚本（ZGC、堆与 DirectMemory、Netty 相关系统属性），可按内存与 CPU 修改后使用。

---

## 许可证

本项目采用 MIT 许可证。
