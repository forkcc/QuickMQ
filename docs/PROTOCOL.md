# MQTT 协议支持说明

QuickMQ 基于 MQTT 3.1.1 规范实现。

## 支持的报文类型

| 类型 | 方向 | 说明 |
|------|------|------|
| CONNECT | 客户端→服务端 | 连接建立，支持 cleanSession、will、keepalive |
| CONNACK | 服务端→客户端 | 连接确认 |
| PUBLISH | 双向 | QoS 0/1/2，保留消息 |
| PUBACK | 双向 | QoS 1 确认 |
| PUBREC | 双向 | QoS 2 第一步 |
| PUBREL | 双向 | QoS 2 第二步 |
| PUBCOMP | 双向 | QoS 2 第三步 |
| SUBSCRIBE | 客户端→服务端 | 支持 +/# 通配符 |
| SUBACK | 服务端→客户端 | 订阅确认 |
| UNSUBSCRIBE | 客户端→服务端 | 取消订阅 |
| UNSUBACK | 服务端→客户端 | 取消确认 |
| PINGREQ | 客户端→服务端 | 心跳请求 |
| PINGRESP | 服务端→客户端 | 心跳响应 |
| DISCONNECT | 客户端→服务端 | 正常断开 |

## 主题过滤

- `+`：单层通配符，匹配一个主题层级
- `#`：多层通配符，匹配零个或多个层级（必须位于末尾）

## 参考规范

- [MQTT 3.1.1 规范](https://docs.oasis-open.org/mqtt/mqtt/v3.1.1/os/mqtt-v3.1.1-os.html)
