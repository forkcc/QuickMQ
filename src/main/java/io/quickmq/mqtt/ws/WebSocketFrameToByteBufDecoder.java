package io.quickmq.mqtt.ws;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageDecoder;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;

import java.util.List;
import java.util.Optional;

/**
 * 将 WebSocket 帧转为 ByteBuf；策略判断是否可解码，避免多重 instanceof。
 */
public class WebSocketFrameToByteBufDecoder extends MessageToMessageDecoder<WebSocketFrame> {

    private static final List<java.util.function.Predicate<WebSocketFrame>> DECODABLE = List.of(
            BinaryWebSocketFrame.class::isInstance,
            TextWebSocketFrame.class::isInstance
    );

    @Override
    protected void decode(ChannelHandlerContext ctx, WebSocketFrame frame, List<Object> out) {
        Optional.of(frame)
                .filter(f -> DECODABLE.stream().anyMatch(p -> p.test(f)))
                .map(WebSocketFrame::content)
                .map(ByteBuf::retain)
                .ifPresent(out::add);
    }
}
