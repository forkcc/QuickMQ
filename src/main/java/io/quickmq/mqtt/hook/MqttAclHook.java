package io.quickmq.mqtt.hook;

/**
 * ACL 钩子接口，用于检查客户端发布/订阅权限。
 */
public interface MqttAclHook {
    
    /**
     * 检查客户端是否有权限执行操作。
     */
    AclResult checkAcl(AclCheckContext context);
}