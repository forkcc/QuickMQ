package io.quickmq.mqtt.codec;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.haproxy.HAProxyMessage;
import io.quickmq.mqtt.ChannelAttributes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;

/**
 * 解析 HAProxy PROXY protocol 消息，提取真实客户端地址并存入 Channel 属性，
 * 然后移除自身（每连接只处理一次 PROXY header）。
 */
public class ProxyProtocolHandler extends ChannelInboundHandlerAdapter {

    private static final Logger log = LoggerFactory.getLogger(ProxyProtocolHandler.class);

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (msg instanceof HAProxyMessage proxy) {
            try {
                String srcAddr = proxy.sourceAddress();
                int srcPort = proxy.sourcePort();
                if (srcAddr != null) {
                    InetSocketAddress real = new InetSocketAddress(srcAddr, srcPort);
                    ctx.channel().attr(ChannelAttributes.REAL_REMOTE_ADDRESS).set(real);
                    log.debug("PROXY protocol: real client {}:{}", srcAddr, srcPort);
                }
            } finally {
                proxy.release();
            }
            ctx.pipeline().remove(this);
            return;
        }
        super.channelRead(ctx, msg);
    }
}
