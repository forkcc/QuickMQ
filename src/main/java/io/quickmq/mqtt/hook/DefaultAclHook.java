package io.quickmq.mqtt.hook;

/**
 * 默认 ACL 钩子实现，允许所有操作。
 */
public class DefaultAclHook implements MqttAclHook {
    
    @Override
    public AclResult checkAcl(AclCheckContext context) {
        // 默认允许所有操作
        return AclResult.allow();
    }
}