package io.quickmq.mqtt.hook;

/**
 * 默认认证钩子：放行所有连接（AllowAll）。
 * 当用户未注册自定义 {@link MqttAuthHook} 时使用此实现。
 */
public final class DefaultAuthHook implements MqttAuthHook {

    @Override
    public AuthResult authenticate(ConnectContext ctx) {
        return AuthResult.accept();
    }
}
