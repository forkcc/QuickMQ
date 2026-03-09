package io.quickmq.mqtt;

import io.netty.util.AttributeKey;

import java.net.InetSocketAddress;

/**
 * Channel 上绑定的 MQTT 会话属性。
 */
public final class ChannelAttributes {

    private ChannelAttributes() {}

    /** 连接成功后设置的 clientId。 */
    public static final AttributeKey<String> CLIENT_ID = AttributeKey.newInstance("mqtt.clientId");

    /** 是否已收到 CONNECT（首包必须是 CONNECT，否则断开）。 */
    public static final AttributeKey<Boolean> CONNECTED = AttributeKey.newInstance("mqtt.connected");

    /** 真实客户端地址（经 Proxy Protocol 解析后），未启用时为 null。 */
    public static final AttributeKey<InetSocketAddress> REAL_REMOTE_ADDRESS = AttributeKey.newInstance("mqtt.realRemoteAddress");

    /** 获取客户端真实地址：优先 PROXY protocol 解析结果，否则用 channel.remoteAddress()。 */
    public static InetSocketAddress remoteAddress(io.netty.channel.Channel channel) {
        InetSocketAddress real = channel.attr(REAL_REMOTE_ADDRESS).get();
        if (real != null) return real;
        return (InetSocketAddress) channel.remoteAddress();
    }
}
