package io.quickmq.mqtt.event;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 读空闲时关闭连接。
 */
public class IdleReaderIdleHandler implements UserEventHandler {

    private static final Logger log = LoggerFactory.getLogger(IdleReaderIdleHandler.class);

    @Override
    public boolean handle(ChannelHandlerContext ctx, Object evt) {
        if (!(evt instanceof IdleStateEvent e) || e.state() != IdleState.READER_IDLE) {
            return false;
        }
        log.debug("读空闲超时，关闭连接");
        ctx.close();
        return true;
    }
}
