package io.quickmq.mqtt.hook;

/**
 * ACL 检查结果。
 */
public record AclResult(boolean allowed, String reason) {
    
    public static AclResult allow() {
        return new AclResult(true, null);
    }
    
    public static AclResult deny(String reason) {
        return new AclResult(false, reason);
    }
}