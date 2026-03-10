package io.quickmq.mqtt;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.haproxy.HAProxyMessageDecoder;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import io.netty.handler.codec.mqtt.MqttDecoder;
import io.netty.handler.codec.mqtt.MqttEncoder;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslHandler;
import io.netty.handler.timeout.IdleStateHandler;
import io.quickmq.config.MqttProperties;
import io.quickmq.mqtt.hook.HookManager;
import io.quickmq.mqtt.codec.ProxyProtocolHandler;
import io.quickmq.mqtt.codec.ByteBufToWebSocketFrameEncoder;
import io.quickmq.mqtt.codec.WebSocketFrameToByteBufDecoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import jakarta.annotation.PreDestroy;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Netty MQTT 服务：面向百万长连接优化。
 * <p>
 * 关键调优点：
 * <ul>
 *   <li>SO_RCVBUF / SO_SNDBUF 压至 4KB，100万连接节省约 ~8GB（对比 64KB 默认）</li>
 *   <li>SO_KEEPALIVE 让 OS 层检测死连接，减少空闲资源占用</li>
 *   <li>WriteBufferWaterMark 防止慢消费者 OOM</li>
 *   <li>Epoll 自动检测，减少 fd 集拷贝与唤醒</li>
 * </ul>
 */
@Component
public class MqttServer {

    private static final Logger log = LoggerFactory.getLogger(MqttServer.class);

    public static final String IDLE_HANDLER_NAME = "idleStateHandler";

    private static final int BOSS_THREADS = 1;
    private static final int SO_BACKLOG = 4096;

    /**
     * 每连接收发缓冲区（字节）。MQTT 控制报文通常很小，4KB 足够。
     * 100 万连接 × 2 × 4KB = ~8GB，若用默认 64KB 则需 ~128GB。
     */
    private static final int SO_RCVBUF = 4096;
    private static final int SO_SNDBUF = 4096;

    /** Netty 写缓冲水位：低水位 16KB，高水位 64KB。超过高水位 channel.isWritable()=false。 */
    private static final WriteBufferWaterMark WRITE_WATER_MARK = new WriteBufferWaterMark(16 * 1024, 64 * 1024);

    private final MqttBrokerHandler brokerHandler = new MqttBrokerHandler();
    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;
    private final List<Channel> serverChannels = new ArrayList<>();
    private boolean epollUsed;
    private SslContextFactory sslContextFactory;

    public void start(MqttProperties props, HookManager hookManager, io.quickmq.data.PersistenceService persistence) throws InterruptedException {
        brokerHandler.setProperties(props);
        brokerHandler.setPersistence(persistence);
        brokerHandler.setHookManager(hookManager);
        
        sslContextFactory = new SslContextFactory(props);

        int workers = resolveWorkerThreads();
        if (EpollHelper.isAvailable()) {
            bossGroup = EpollHelper.newBossGroup(BOSS_THREADS);
            workerGroup = EpollHelper.newWorkerGroup(workers);
            epollUsed = true;
            log.info("使用 Epoll 传输, workerThreads={}", workers);
        } else {
            bossGroup = new NioEventLoopGroup(BOSS_THREADS);
            workerGroup = new NioEventLoopGroup(workers);
            epollUsed = false;
            log.info("使用 NIO 传输, workerThreads={}", workers);
        }

        int maxMsg = props.getMaxMessageSize();
        int connectTimeout = props.getConnectTimeoutSeconds();
        boolean proxyProtocol = props.isProxyProtocol();

        List<Integer> tcpPorts = props.resolveTcpPorts();
        if (!tcpPorts.isEmpty()) {
            ServerBootstrap tcpBootstrap = newBootstrap()
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) {
                            var pipeline = ch.pipeline();
                            if (proxyProtocol) {
                                pipeline.addLast(new HAProxyMessageDecoder());
                                pipeline.addLast(new ProxyProtocolHandler());
                            }
                            if (connectTimeout > 0) {
                                pipeline.addLast(IDLE_HANDLER_NAME,
                                        new IdleStateHandler(connectTimeout, 0, 0, TimeUnit.SECONDS));
                            }
                            pipeline.addLast(MqttEncoder.INSTANCE)
                                    .addLast(new MqttDecoder(maxMsg))
                                    .addLast(brokerHandler);
                        }
                    });
            for (int port : tcpPorts) {
                ChannelFuture future = tcpBootstrap.bind(port).sync();
                serverChannels.add(future.channel());
                log.info("MQTT TCP 已监听端口: {}{}", port, proxyProtocol ? " [PROXY protocol]" : "");
            }
        }

        List<Integer> wsPorts = props.resolveWsPorts();
        if (!wsPorts.isEmpty()) {
            String wsPath = props.getWsPath();
            int wsMaxHttpBody = props.getWsMaxHttpBodySize();
            ServerBootstrap wsBootstrap = newBootstrap()
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) {
                            var pipeline = ch.pipeline();
                            if (proxyProtocol) {
                                pipeline.addLast(new HAProxyMessageDecoder());
                                pipeline.addLast(new ProxyProtocolHandler());
                            }
                            pipeline.addLast(new HttpServerCodec())
                                    .addLast(new HttpObjectAggregator(wsMaxHttpBody))
                                    .addLast(new WebSocketServerProtocolHandler(wsPath))
                                    .addLast(new WebSocketFrameToByteBufDecoder())
                                    .addLast(new ByteBufToWebSocketFrameEncoder());
                            if (connectTimeout > 0) {
                                pipeline.addLast(IDLE_HANDLER_NAME,
                                        new IdleStateHandler(connectTimeout, 0, 0, TimeUnit.SECONDS));
                            }
                            pipeline.addLast(new MqttDecoder(maxMsg))
                                    .addLast(MqttEncoder.INSTANCE)
                                    .addLast(brokerHandler);
                        }
                    });
            for (int port : wsPorts) {
                ChannelFuture future = wsBootstrap.bind(port).sync();
                serverChannels.add(future.channel());
                log.info("MQTT WebSocket 已监听端口: {} 路径: {}{}", port, wsPath,
                        proxyProtocol ? " [PROXY protocol]" : "");
            }
        }

        // ---------- MQTT over TLS/SSL ----------
        List<Integer> sslPorts = props.resolveSslPorts();
        if (!sslPorts.isEmpty() && sslContextFactory.isSslEnabled()) {
            SslContext sslContext = sslContextFactory.getSslContext();
            ServerBootstrap sslBootstrap = newBootstrap()
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) {
                            var pipeline = ch.pipeline();
                            if (proxyProtocol) {
                                pipeline.addLast(new HAProxyMessageDecoder());
                                pipeline.addLast(new ProxyProtocolHandler());
                            }
                            pipeline.addLast(new SslHandler(sslContext.newEngine(ch.alloc())));
                            if (connectTimeout > 0) {
                                pipeline.addLast(IDLE_HANDLER_NAME,
                                        new IdleStateHandler(connectTimeout, 0, 0, TimeUnit.SECONDS));
                            }
                            pipeline.addLast(MqttEncoder.INSTANCE)
                                    .addLast(new MqttDecoder(maxMsg))
                                    .addLast(brokerHandler);
                        }
                    });
            for (int port : sslPorts) {
                ChannelFuture future = sslBootstrap.bind(port).sync();
                serverChannels.add(future.channel());
                log.info("MQTT over TLS 已监听端口: {}{}", port, proxyProtocol ? " [PROXY protocol]" : "");
            }
        }

        // ---------- WebSocket over TLS/SSL ----------
        List<Integer> wssPorts = props.resolveWssPorts();
        if (!wssPorts.isEmpty() && sslContextFactory.isSslEnabled()) {
            SslContext sslContext = sslContextFactory.getSslContext();
            String wsPath = props.getWsPath();
            int wsMaxHttpBody = props.getWsMaxHttpBodySize();
            ServerBootstrap wssBootstrap = newBootstrap()
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) {
                            var pipeline = ch.pipeline();
                            if (proxyProtocol) {
                                pipeline.addLast(new HAProxyMessageDecoder());
                                pipeline.addLast(new ProxyProtocolHandler());
                            }
                            pipeline.addLast(new SslHandler(sslContext.newEngine(ch.alloc())));
                            pipeline.addLast(new HttpServerCodec())
                                    .addLast(new HttpObjectAggregator(wsMaxHttpBody))
                                    .addLast(new WebSocketServerProtocolHandler(wsPath))
                                    .addLast(new WebSocketFrameToByteBufDecoder())
                                    .addLast(new ByteBufToWebSocketFrameEncoder());
                            if (connectTimeout > 0) {
                                pipeline.addLast(IDLE_HANDLER_NAME,
                                        new IdleStateHandler(connectTimeout, 0, 0, TimeUnit.SECONDS));
                            }
                            pipeline.addLast(new MqttDecoder(maxMsg))
                                    .addLast(MqttEncoder.INSTANCE)
                                    .addLast(brokerHandler);
                        }
                    });
            for (int port : wssPorts) {
                ChannelFuture future = wssBootstrap.bind(port).sync();
                serverChannels.add(future.channel());
                log.info("MQTT WebSocket over TLS 已监听端口: {} 路径: {}{}", port, wsPath,
                        proxyProtocol ? " [PROXY protocol]" : "");
            }
        }

        log.info("Keepalive: default={}s, max={}s, connectTimeout={}s, proxyProtocol={}, SSL={}",
                props.getDefaultKeepaliveSeconds(), props.getMaxKeepaliveSeconds(),
                connectTimeout, proxyProtocol, sslContextFactory.isSslEnabled());
    }

    private ServerBootstrap newBootstrap() {
        return new ServerBootstrap()
                .group(bossGroup, workerGroup)
                .channel(EpollHelper.serverChannelClass(epollUsed))
                .option(ChannelOption.SO_BACKLOG, SO_BACKLOG)
                .option(ChannelOption.SO_REUSEADDR, true)
                .childOption(ChannelOption.TCP_NODELAY, true)
                .childOption(ChannelOption.SO_REUSEADDR, true)
                .childOption(ChannelOption.SO_KEEPALIVE, true)
                .childOption(ChannelOption.SO_RCVBUF, SO_RCVBUF)
                .childOption(ChannelOption.SO_SNDBUF, SO_SNDBUF)
                .childOption(ChannelOption.WRITE_BUFFER_WATER_MARK, WRITE_WATER_MARK)
                .childOption(ChannelOption.ALLOCATOR, PooledByteBufAllocator.DEFAULT);
    }

    private static int resolveWorkerThreads() {
        return Math.max(1, Runtime.getRuntime().availableProcessors() * 2);
    }

    @PreDestroy
    public void shutdown() {
        for (Channel ch : serverChannels) {
            if (ch != null) ch.close().syncUninterruptibly();
        }
        serverChannels.clear();
        if (bossGroup != null) bossGroup.shutdownGracefully();
        if (workerGroup != null) workerGroup.shutdownGracefully();
    }
}
