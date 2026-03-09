package io.quickmq.mqtt.hook;

import io.netty.handler.codec.mqtt.MqttQoS;

import java.net.InetSocketAddress;
import java.util.List;

/**
 * MQTT 审计事件钩子（可选）。
 * <p>
 * 所有方法有默认空实现，用户按需覆盖。可注册多个实现为 Spring Bean，全部会被调用。
 * 钩子调用不影响业务流程（fire-and-forget），异常会被捕获并记录日志。
 * <p>
 * 示例：
 * <pre>{@code
 * @Component
 * public class AuditHook implements MqttEventHook {
 *     public void onClientConnected(String clientId, InetSocketAddress remote) {
 *         log.info("客户端连入: {} from {}", clientId, remote);
 *     }
 *     public void onClientDisconnected(String clientId, DisconnectReason reason) {
 *         log.info("客户端断开: {} reason={}", clientId, reason);
 *     }
 * }
 * }</pre>
 */
public interface MqttEventHook {

    /** 客户端成功连接后 */
    default void onClientConnected(String clientId, InetSocketAddress remoteAddress) {}

    /** 客户端断开（含正常 DISCONNECT 和异常断线） */
    default void onClientDisconnected(String clientId, InetSocketAddress remoteAddress, DisconnectReason reason) {}

    /** 客户端发布消息 */
    default void onMessagePublish(String clientId, String topic, MqttQoS qos, boolean retain, int payloadSize) {}

    /** 客户端已投递到订阅者 */
    default void onMessageDelivered(String clientId, String topic, int subscriberCount) {}

    /** 客户端订阅 */
    default void onClientSubscribe(String clientId, List<String> topicFilters) {}

    /** 客户端取消订阅 */
    default void onClientUnsubscribe(String clientId, List<String> topicFilters) {}

    /** 连接被拒绝（认证失败、协议版本不支持等） */
    default void onConnectRejected(String clientId, InetSocketAddress remoteAddress, String reason) {}

    /** 同 clientId 旧连接被踢 */
    default void onClientKicked(String clientId, InetSocketAddress remoteAddress) {}

    enum DisconnectReason {
        NORMAL,
        IDLE_TIMEOUT,
        KICKED,
        PROTOCOL_ERROR,
        EXCEPTION
    }
}
