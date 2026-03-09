package io.quickmq.mqtt;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.mqtt.MqttDecoder;
import io.netty.handler.codec.mqtt.MqttEncoder;
import io.netty.handler.timeout.IdleStateHandler;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import io.quickmq.config.MqttProperties;
import io.quickmq.mqtt.ws.ByteBufToWebSocketFrameEncoder;
import io.quickmq.mqtt.ws.WebSocketFrameToByteBufDecoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import jakarta.annotation.PreDestroy;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Netty MQTT 服务：支持多 TCP 端口、多 WebSocket 端口。
 */
@Component
public class MqttServer {

    private static final Logger log = LoggerFactory.getLogger(MqttServer.class);
    private static final int MAX_MQTT_MESSAGE_SIZE = 256 * 1024;
    private static final int READ_IDLE_SECONDS = 120;
    private static final int MAX_HTTP_BODY = 65536;

    private final MqttBrokerHandler brokerHandler = new MqttBrokerHandler();
    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;
    private final List<Channel> serverChannels = new ArrayList<>();

    public void start(MqttProperties props) throws InterruptedException {
        bossGroup = new NioEventLoopGroup(1);
        workerGroup = new NioEventLoopGroup();

        List<Integer> tcpPorts = props.resolveTcpPorts();
        List<Integer> wsPorts = props.resolveWsPorts();
        String wsPath = props.getWsPath();

        // TCP 端口：共用一个 Bootstrap，每端口 bind 一次
        if (!tcpPorts.isEmpty()) {
            ServerBootstrap tcpBootstrap = new ServerBootstrap()
                    .group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) {
                            ch.pipeline()
                                    .addLast(new IdleStateHandler(READ_IDLE_SECONDS, 0, 0, TimeUnit.SECONDS))
                                    .addLast(MqttEncoder.INSTANCE)
                                    .addLast(new MqttDecoder(MAX_MQTT_MESSAGE_SIZE))
                                    .addLast(brokerHandler);
                        }
                    });
            for (int port : tcpPorts) {
                ChannelFuture future = tcpBootstrap.bind(port).sync();
                serverChannels.add(future.channel());
                log.info("MQTT TCP 已监听端口: {}", port);
            }
        }

        // WebSocket 端口：每端口一个 Bootstrap（便于不同 path 扩展）
        if (!wsPorts.isEmpty()) {
            ServerBootstrap wsBootstrap = new ServerBootstrap()
                    .group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) {
                            ch.pipeline()
                                    .addLast(new HttpServerCodec())
                                    .addLast(new HttpObjectAggregator(MAX_HTTP_BODY))
                                    .addLast(new WebSocketServerProtocolHandler(wsPath))
                                    .addLast(new WebSocketFrameToByteBufDecoder())
                                    .addLast(new IdleStateHandler(READ_IDLE_SECONDS, 0, 0, TimeUnit.SECONDS))
                                    .addLast(new MqttDecoder(MAX_MQTT_MESSAGE_SIZE))
                                    .addLast(MqttEncoder.INSTANCE)
                                    .addLast(new ByteBufToWebSocketFrameEncoder())
                                    .addLast(brokerHandler);
                        }
                    });
            for (int port : wsPorts) {
                ChannelFuture future = wsBootstrap.bind(port).sync();
                serverChannels.add(future.channel());
                log.info("MQTT WebSocket 已监听端口: {} 路径: {}", port, wsPath);
            }
        }
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
