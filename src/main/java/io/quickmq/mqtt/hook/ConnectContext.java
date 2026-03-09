package io.quickmq.mqtt.hook;

import java.net.InetSocketAddress;

/**
 * 认证钩子收到的连接上下文。
 */
public record ConnectContext(
        String clientId,
        String username,
        byte[] password,
        InetSocketAddress remoteAddress,
        int protocolVersion,
        boolean cleanSession
) {}
