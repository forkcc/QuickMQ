package io.quickmq.mqtt;

import io.netty.channel.EventLoopGroup;
import io.netty.channel.ServerChannel;
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.epoll.EpollServerSocketChannel;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;

/**
 * Epoll 可用时创建 Epoll 传输，否则回退 NIO。
 * Linux 下 Epoll 可减少 fd 集拷贝与唤醒开销。
 */
final class EpollHelper {

    private static final boolean EPOLL;

    static {
        boolean avail;
        try { avail = io.netty.channel.epoll.Epoll.isAvailable(); }
        catch (Throwable t) { avail = false; }
        EPOLL = avail;
    }

    static boolean isAvailable() { return EPOLL; }

    static EventLoopGroup newBossGroup(int nThreads) {
        return EPOLL ? new EpollEventLoopGroup(nThreads) : new NioEventLoopGroup(nThreads);
    }

    static EventLoopGroup newWorkerGroup(int nThreads) {
        return EPOLL ? new EpollEventLoopGroup(nThreads) : new NioEventLoopGroup(nThreads);
    }

    static Class<? extends ServerChannel> serverChannelClass(boolean useEpoll) {
        return useEpoll && EPOLL ? EpollServerSocketChannel.class : NioServerSocketChannel.class;
    }

    private EpollHelper() {}
}
