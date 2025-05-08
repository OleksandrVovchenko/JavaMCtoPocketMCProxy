package org.smnetworking;
import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.DecoderException;
import io.netty.handler.codec.EncoderException;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
public class ProtocolUtils {
    private static final int MAX_VARINT_SIZE = 5;
    private static final int MAX_STRING_LENGTH = 32767;
    public static int readVarInt(ByteBuf buf) {
        int value = 0;
        int size = 0;
        byte b;
        do {
            b = buf.readByte();
            value |= (b & 0x7F) << (size++ * 7);
            if (size > MAX_VARINT_SIZE) {
                throw new DecoderException("VarInt too long");
            }
        } while ((b & 0x80) == 0x80);
        return value;
    }
    public static void writeVarInt(int value, ByteBuf buf) {
        do {
            byte temp = (byte) (value & 0x7F);
            if ((value >>> 7) != 0) {
                temp |= 0x80;
            }
            buf.writeByte(temp);
            value >>>= 7;
        } while (value != 0);
    }
    public static int getVarIntSize(int value) {
        int size = 0;
        do {
            size++;
            value >>>= 7;
        } while (value != 0);
        return size;
    }
    public static String readString(ByteBuf buf) {
        int length = readVarInt(buf);
        if (length < 0 || length > MAX_STRING_LENGTH) {
            throw new DecoderException("String length out of bounds: " + length);
        }
        if (buf.readableBytes() < length) {
            throw new DecoderException("Buffer too short for string of length " + length);
        }
        byte[] bytes = new byte[length];
        buf.readBytes(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }
    public static void writeString(String value, ByteBuf buf) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_STRING_LENGTH) {
            throw new DecoderException("String too long: " + bytes.length + " bytes");
        }
        writeVarInt(bytes.length, buf);
        buf.writeBytes(bytes);
    }
    public static long readLong(ByteBuf buf) {
        if (buf.readableBytes() < 8) {
            throw new DecoderException("Buffer too short for Long");
        }
        return buf.readLong();
    }
    public static void writeLong(long value, ByteBuf buf) {
        buf.writeLong(value);
    }
    public static short readShortLE(ByteBuf buf) {
        if (buf.readableBytes() < 2) {
            throw new DecoderException("Buffer too short for Short (LE)");
        }
        return buf.readShortLE();
    }
    public static void writeShortLE(int value, ByteBuf buf) {
        buf.writeShortLE(value);
    }
    public static int readIntLE(ByteBuf buf) {
        if (buf.readableBytes() < 4) {
            throw new DecoderException("Buffer too short for Int (LE)");
        }
        return buf.readIntLE();
    }
    public static void writeIntLE(int value, ByteBuf buf) {
        buf.writeIntLE(value);
    }
    public static long readLongLE(ByteBuf buf) {
        if (buf.readableBytes() < 8) {
            throw new DecoderException("Buffer too short for Long (LE)");
        }
        return buf.readLongLE();
    }
    public static void writeLongLE(long value, ByteBuf buf) {
        buf.writeLongLE(value);
    }
    public static String readPEString(ByteBuf buf) {
        short length = readShortLE(buf);
        if (length < 0) {
            throw new DecoderException("PE String length out of bounds: " + length);
        }
        if (buf.readableBytes() < length) {
            throw new DecoderException("Buffer too short for PE string of length " + length);
        }
        byte[] bytes = new byte[length];
        buf.readBytes(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }
    public static void writePEString(String value, ByteBuf buf) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        writeShortLE(bytes.length, buf);
        buf.writeBytes(bytes);
    }
    public static long generateClientGuid() {
        long guid = System.nanoTime();
        return guid;
    }
    public static InetSocketAddress readPEAddress(ByteBuf buf) {
        if (buf.readableBytes() < 7) {
            throw new DecoderException("Buffer too short for PE Address");
        }
        byte version = buf.readByte();
        if (version != 4) {
            throw new DecoderException("Unsupported PE Address version: " + version);
        }
        byte[] ipBytes = new byte[4];
        buf.readBytes(ipBytes);
        int port = buf.readUnsignedShort();
        try {
            InetAddress address = InetAddress.getByAddress(ipBytes);
            return new InetSocketAddress(address, port);
        } catch (UnknownHostException e) {
            throw new DecoderException("Failed to decode PE Address IP bytes", e);
        }
    }
    public static void writePEAddress(InetSocketAddress address, ByteBuf buf) {
        InetAddress inetAddress = address.getAddress();
        byte[] ipBytes = inetAddress.getAddress();
        if (ipBytes.length != 4) {
            throw new EncoderException("Only IPv4 addresses are supported for PE Address encoding");
        }
        buf.writeByte(4);
        buf.writeBytes(ipBytes);
        buf.writeShort(address.getPort());
    }
}