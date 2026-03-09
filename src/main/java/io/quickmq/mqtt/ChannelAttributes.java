package io.quickmq.mqtt;

import io.netty.util.AttributeKey;

/**
 * Channel 上绑定的 MQTT 会话属性。
 */
public final class ChannelAttributes {

    private ChannelAttributes() {}

    /** 连接成功后设置的 clientId。 */
    public static final AttributeKey<String> CLIENT_ID = AttributeKey.newInstance("mqtt.clientId");
}
