package io.quickmq.mqtt.hook;

import java.net.InetSocketAddress;

/**
 * ACL 检查上下文。
 */
public record AclCheckContext(
        String clientId,
        String username,
        InetSocketAddress remoteAddress,
        String topic,
        AclAction action
) {
    
    public enum AclAction {
        PUBLISH,
        SUBSCRIBE
    }
    
    public static AclCheckContext forPublish(String clientId, String username, 
                                           InetSocketAddress remoteAddress, String topic) {
        return new AclCheckContext(clientId, username, remoteAddress, topic, AclAction.PUBLISH);
    }
    
    public static AclCheckContext forSubscribe(String clientId, String username,
                                             InetSocketAddress remoteAddress, String topic) {
        return new AclCheckContext(clientId, username, remoteAddress, topic, AclAction.SUBSCRIBE);
    }
}