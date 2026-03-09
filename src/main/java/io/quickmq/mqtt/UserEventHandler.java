package io.quickmq.mqtt;

import io.netty.channel.ChannelHandlerContext;

/**
 * 用户事件处理策略：返回 true 表示已处理，不再传递。
 */
@FunctionalInterface
public interface UserEventHandler {

    boolean handle(ChannelHandlerContext ctx, Object evt);
}
