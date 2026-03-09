package io.quickmq.mqtt.event;

import io.netty.channel.ChannelHandlerContext;

/**
 * 将用户事件继续向 pipeline 传递。
 */
public class FireUserEventTriggeredHandler implements UserEventHandler {

    @Override
    public boolean handle(ChannelHandlerContext ctx, Object evt) {
        ctx.fireUserEventTriggered(evt);
        return true;
    }
}
