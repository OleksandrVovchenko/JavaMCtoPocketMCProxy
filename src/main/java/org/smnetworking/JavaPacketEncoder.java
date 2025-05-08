package org.smnetworking;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;
public class JavaPacketEncoder extends MessageToByteEncoder<ByteBuf> {
    @Override
    protected void encode(ChannelHandlerContext ctx, ByteBuf msg, ByteBuf out) throws Exception {
        int bodyLength = msg.readableBytes();
        int headerLength = ProtocolUtils.getVarIntSize(bodyLength);
        out.ensureWritable(headerLength + bodyLength);
        ProtocolUtils.writeVarInt(bodyLength, out);
        out.writeBytes(msg);
    }
}