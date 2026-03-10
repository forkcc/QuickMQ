package io.quickmq.mqtt;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * MQTT 主题名和主题过滤器验证器（MQTT 3.1.1 §4.7）。
 */
public class TopicValidator {
    
    private static final Logger log = LoggerFactory.getLogger(TopicValidator.class);
    
    private static final char WILDCARD_PLUS = '+';
    private static final char WILDCARD_HASH = '#';
    private static final char SEPARATOR = '/';
    
    /**
     * 验证主题名是否符合规范。
     * 
     * @param topic 主题名
     * @return 是否有效
     */
    public static boolean isValidTopicName(String topic) {
        if (topic == null || topic.isEmpty()) {
            log.debug("主题名为空");
            return false;
        }
        
        if (topic.contains("\u0000")) {
            log.debug("主题名包含空字符");
            return false;
        }
        
        if (topic.contains(String.valueOf(WILDCARD_PLUS)) || 
            topic.contains(String.valueOf(WILDCARD_HASH))) {
            log.debug("主题名包含通配符");
            return false;
        }
        
        if (topic.length() > 65535) {
            log.debug("主题名过长: {}", topic.length());
            return false;
        }
        
        return true;
    }
    
    /**
     * 验证主题过滤器是否符合规范。
     * 
     * @param filter 主题过滤器
     * @return 是否有效
     */
    public static boolean isValidTopicFilter(String filter) {
        if (filter == null || filter.isEmpty()) {
            log.debug("主题过滤器为空");
            return false;
        }
        
        if (filter.contains("\u0000")) {
            log.debug("主题过滤器包含空字符");
            return false;
        }
        
        if (filter.length() > 65535) {
            log.debug("主题过滤器过长: {}", filter.length());
            return false;
        }
        
        if (!validateWildcards(filter)) {
            log.debug("主题过滤器通配符无效: {}", filter);
            return false;
        }
        
        return true;
    }
    
    private static boolean validateWildcards(String filter) {
        int length = filter.length();
        
        for (int i = 0; i < length; i++) {
            char c = filter.charAt(i);
            
            if (c == WILDCARD_HASH) {
                // # 必须是最后一个字符
                if (i != length - 1) {
                    return false;
                }
                // # 前面必须是 / 或者 # 是唯一字符
                if (i > 0 && filter.charAt(i - 1) != SEPARATOR) {
                    return false;
                }
                // 检查 # 后面没有字符（已经是最后一个）
                continue;
            }
            
            if (c == WILDCARD_PLUS) {
                // + 必须占据整个层级
                if (i > 0 && filter.charAt(i - 1) != SEPARATOR) {
                    return false;
                }
                if (i < length - 1 && filter.charAt(i + 1) != SEPARATOR) {
                    return false;
                }
                continue;
            }
        }
        
        return true;
    }
    
    /**
     * 检查是否为系统主题（以 $SYS/ 开头）。
     * 
     * @param topic 主题名或过滤器
     * @return 是否是系统主题
     */
    public static boolean isSystemTopic(String topic) {
        return topic != null && topic.startsWith("$SYS/");
    }
    
    /**
     * 检查主题名是否可用于发布。
     * 
     * @param topic 主题名
     * @return 是否可用于发布
     */
    public static boolean isPublishableTopic(String topic) {
        return isValidTopicName(topic) && !isSystemTopic(topic);
    }
    
    /**
     * 检查主题过滤器是否可用于订阅。
     * 
     * @param filter 主题过滤器
     * @return 是否可用于订阅
     */
    public static boolean isSubscribableFilter(String filter) {
        return isValidTopicFilter(filter);
    }
}