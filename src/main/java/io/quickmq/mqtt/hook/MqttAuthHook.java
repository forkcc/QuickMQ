package io.quickmq.mqtt.hook;

/**
 * MQTT 认证钩子（强制调用）。
 * <p>
 * 每次 CONNECT 都会调用此钩子。用户可实现此接口并注册为 Spring Bean 来自定义认证逻辑。
 * 若未注册任何实现，使用默认的 AllowAll 策略（放行所有连接）。
 * <p>
 * 示例：
 * <pre>{@code
 * @Component
 * public class MyAuthHook implements MqttAuthHook {
 *     public AuthResult authenticate(ConnectContext ctx) {
 *         if ("admin".equals(ctx.username())) return AuthResult.accept();
 *         return AuthResult.badCredentials("invalid credentials");
 *     }
 * }
 * }</pre>
 */
@FunctionalInterface
public interface MqttAuthHook {

    /**
     * 对 CONNECT 请求进行认证。
     *
     * @param ctx 连接上下文（clientId、username、password、远程地址等）
     * @return 认证结果；accept 放行，reject/badCredentials 拒绝并断开
     */
    AuthResult authenticate(ConnectContext ctx);
}
