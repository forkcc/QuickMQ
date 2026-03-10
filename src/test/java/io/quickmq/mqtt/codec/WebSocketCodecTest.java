package io.quickmq.mqtt.codec;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.PingWebSocketFrame;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class WebSocketCodecTest {

    @Test
    void decoder_binaryFrame_extractsContent() {
        EmbeddedChannel ch = new EmbeddedChannel(new WebSocketFrameToByteBufDecoder());
        ByteBuf data = Unpooled.wrappedBuffer(new byte[]{1, 2, 3});
        ch.writeInbound(new BinaryWebSocketFrame(data));

        ByteBuf out = ch.readInbound();
        assertNotNull(out);
        assertEquals(3, out.readableBytes());
        assertEquals(1, out.readByte());
        out.release();
    }

    @Test
    void decoder_textFrame_extractsContent() {
        EmbeddedChannel ch = new EmbeddedChannel(new WebSocketFrameToByteBufDecoder());
        ByteBuf data = Unpooled.wrappedBuffer("hello".getBytes());
        ch.writeInbound(new TextWebSocketFrame(data));

        ByteBuf out = ch.readInbound();
        assertNotNull(out);
        assertEquals(5, out.readableBytes());
        out.release();
    }

    @Test
    void decoder_pingFrame_ignored() {
        EmbeddedChannel ch = new EmbeddedChannel(new WebSocketFrameToByteBufDecoder());
        ch.writeInbound(new PingWebSocketFrame(Unpooled.wrappedBuffer(new byte[]{1})));
        assertNull(ch.readInbound(), "Ping frames should be ignored");
    }

    @Test
    void encoder_byteBufToBinaryFrame() {
        EmbeddedChannel ch = new EmbeddedChannel(new ByteBufToWebSocketFrameEncoder());
        ByteBuf data = Unpooled.wrappedBuffer(new byte[]{4, 5, 6});
        ch.writeOutbound(data);

        Object out = ch.readOutbound();
        assertNotNull(out);
        assertTrue(out instanceof BinaryWebSocketFrame);
        BinaryWebSocketFrame frame = (BinaryWebSocketFrame) out;
        assertEquals(3, frame.content().readableBytes());
        frame.release();
    }
}
