package org.smnetworking;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import io.netty.handler.codec.CorruptedFrameException;

import java.util.List;
public class JavaPacketDecoder extends ByteToMessageDecoder {
    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        if (in.readableBytes() > 0) {
            int bytesToDump = Math.min(in.readableBytes(), 16);
            byte[] dump = new byte[bytesToDump];
            in.getBytes(in.readerIndex(), dump);
            System.out.print("--- Raw incoming bytes (" + bytesToDump + "): ");
            for (byte b : dump) {
                System.out.printf("%02X ", b);
            }
            System.out.println(" ---");
        }
        in.markReaderIndex();
        int packetLength;
        try {
            packetLength = ProtocolUtils.readVarInt(in);
        } catch (Exception e) {
            in.resetReaderIndex();
            return;
        }
        if (in.readableBytes() < packetLength) {
            in.resetReaderIndex();
            return;
        }
        int initialReaderIndexForPacket = in.readerIndex();
        int packetId = ProtocolUtils.readVarInt(in);
        int bytesReadForId = in.readerIndex() - initialReaderIndexForPacket;
        int dataLength = packetLength - bytesReadForId;
        ByteBuf packetData = in.readBytes(dataLength);
        in.readerIndex(initialReaderIndexForPacket);
        ByteBuf fullPacketWithId = in.readBytes(packetLength);
        out.add(fullPacketWithId);
    }
    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        if (cause instanceof CorruptedFrameException) {
            System.err.println("Corrupted frame from Java client: " + cause.getMessage());
            ctx.close();
        } else {
            super.exceptionCaught(ctx, cause);
        }
    }
}