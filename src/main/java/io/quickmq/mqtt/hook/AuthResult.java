package io.quickmq.mqtt.hook;

import io.netty.handler.codec.mqtt.MqttConnectReturnCode;

/**
 * 认证钩子返回结果。
 */
public record AuthResult(boolean accepted, MqttConnectReturnCode returnCode, String reason) {

    public static AuthResult accept() {
        return new AuthResult(true, MqttConnectReturnCode.CONNECTION_ACCEPTED, null);
    }

    public static AuthResult reject(String reason) {
        return new AuthResult(false, MqttConnectReturnCode.CONNECTION_REFUSED_NOT_AUTHORIZED, reason);
    }

    public static AuthResult badCredentials(String reason) {
        return new AuthResult(false, MqttConnectReturnCode.CONNECTION_REFUSED_BAD_USER_NAME_OR_PASSWORD, reason);
    }

    public static AuthResult serverUnavailable(String reason) {
        return new AuthResult(false, MqttConnectReturnCode.CONNECTION_REFUSED_SERVER_UNAVAILABLE, reason);
    }

    public static AuthResult identifierRejected(String reason) {
        return new AuthResult(false, MqttConnectReturnCode.CONNECTION_REFUSED_IDENTIFIER_REJECTED, reason);
    }

    public static AuthResult unacceptableProtocolVersion(String reason) {
        return new AuthResult(false, MqttConnectReturnCode.CONNECTION_REFUSED_UNACCEPTABLE_PROTOCOL_VERSION, reason);
    }

    public static AuthResult reject(MqttConnectReturnCode code, String reason) {
        return new AuthResult(false, code, reason);
    }
}
