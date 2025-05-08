package org.smnetworking;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.socket.DatagramPacket;

import java.net.InetSocketAddress;
import java.util.concurrent.TimeUnit;
import java.util.Arrays;
public class PeConnectionHandler extends SimpleChannelInboundHandler<DatagramPacket> {
    private final Channel javaClientChannel;
    private final PeClient peClient;
    private enum PeConnectionState {
        DISCONNECTED,
        CONNECTING_1_SENT,
        CONNECTING_2_SENT,
        RAKNET_CONNECTED,
        LOGGING_IN,
        CONNECTED
    }
    private static final byte[] RAKNET_MAGIC = {(byte) 0x00, (byte) 0xFF, (byte) 0xFF, (byte) 0x00, (byte) 0xFE, (byte) 0xFE, (byte) 0xFE, (byte) 0xFE, (byte) 0xFD, (byte) 0xFD, (byte) 0xFD, (byte) 0xFD, (byte) 0x12, (byte) 0x34, (byte) 0x56, (byte) 0x78};
    public PeConnectionHandler(Channel javaClientChannel, PeClient peClient) {
        this.javaClientChannel = javaClientChannel;
        this.peClient = peClient;
    }
    @Override
    protected void channelRead0(ChannelHandlerContext ctx, DatagramPacket msg) throws Exception {
        PeClient.PeConnectionState currentPeState = peClient.getPeState();
        ByteBuf pePacketData = msg.content();
        InetSocketAddress sender = msg.sender();
        if (pePacketData.readableBytes() <= 0) {
            System.out.println("Received empty PE packet.");
            return;
        }
        byte packetId = pePacketData.readByte();
        System.out.println("Received PE Packet ID: 0x" + Integer.toHexString(packetId & 0xFF) + " (State: TODO)");
        switch (packetId) {
            case 0x06:
                if (currentPeState == PeClient.PeConnectionState.CONNECTING_1_SENT) {
                    System.out.println("Received PE packet: Open Connection Reply #1 (0x06)");
                    handleOpenConnectionReply1(ctx, pePacketData);
                } else {
                    System.out.println("Received unexpected Open Connection Reply #1 in state: " + currentPeState);
                }
                break;
            case 0x08:
                if (currentPeState == PeClient.PeConnectionState.CONNECTING_2_SENT) {
                    System.out.println("Received PE packet: Open Connection Reply #2 (0x08)");
                    handleOpenConnectionReply2(ctx, pePacketData);
                } else {
                    System.out.println("Received unexpected Open Connection Reply #2 in state: " + currentPeState);
                }
                break;
            case 0x1a:
                System.err.println("Received PE packet: Incompatible Protocol Version (0x1a)");
                if (javaClientChannel.isActive()) {
                    ByteBuf disconnectPacket = javaClientChannel.alloc().buffer();
                    ProtocolUtils.writeVarInt(0x00, disconnectPacket);
                    ProtocolUtils.writeString("{\"text\":\"Target PE server has incompatible RakNet protocol version.\"}", disconnectPacket);
                    javaClientChannel.writeAndFlush(disconnectPacket);
                    javaClientChannel.eventLoop().schedule(() -> {
                        javaClientChannel.close();
                    }, 50, TimeUnit.MILLISECONDS);
                }
                ctx.close();
                peClient.shutdown();
                break;
            default:
                System.out.println("Received unknown PE Packet ID: 0x" + Integer.toHexString(packetId & 0xFF) + " in state: " + currentPeState);
                int bytesToDump = Math.min(pePacketData.readableBytes(), 32);
                byte[] dump = new byte[bytesToDump];
                pePacketData.getBytes(pePacketData.readerIndex(), dump);
                System.out.print("--- Raw unknown PE bytes (" + bytesToDump + "): ");
                for (byte b : dump) {
                    System.out.printf("%02X ", b);
                }
                System.out.println(" ---");
                break;
        }
    }
    private void handleOpenConnectionReply1(ChannelHandlerContext ctx, ByteBuf packetData) {
        try {
            byte[] magic = new byte[16];
            packetData.readBytes(magic);
            if (!Arrays.equals(magic, RAKNET_MAGIC)) {
                System.err.println("Received Open Connection Reply #1 with invalid magic bytes.");
                ctx.close();
                return;
            }
            long serverGuid = ProtocolUtils.readLongLE(packetData);
            byte security = packetData.readByte();
            short clientMtu = ProtocolUtils.readShortLE(packetData);
            System.out.println("  Server GUID: " + serverGuid);
            System.out.println("  Server Security: " + security);
            System.out.println("  Client MTU confirmed by server: " + clientMtu);
        } catch (Exception e) {
            System.err.println("Error parsing Open Connection Reply #1: " + e.getMessage());
            e.printStackTrace();
            ctx.close();
        }
    }
    private void handleOpenConnectionReply2(ChannelHandlerContext ctx, ByteBuf packetData) {
        System.out.println("Processing PE packet: Open Connection Reply #2 (0x08)");
        try {
            byte[] magic = new byte[16];
            packetData.readBytes(magic);
            if (!Arrays.equals(magic, RAKNET_MAGIC)) {
                System.err.println("Received Open Connection Reply #2 with invalid magic bytes.");
                ctx.close();
                peClient.shutdown();
                return;
            }
            long serverGuid = ProtocolUtils.readLongLE(packetData);
            InetSocketAddress clientAddressOnServer = ProtocolUtils.readPEAddress(packetData);
            short mtuSize = ProtocolUtils.readShortLE(packetData);
            byte security = packetData.readByte();
            System.out.println("  Server GUID: " + serverGuid);
            System.out.println("  Client Address on Server: " + clientAddressOnServer);
            System.out.println("  MTU confirmed again: " + mtuSize);
            System.out.println("  Server Security: " + security);
            peClient.setPeState(PeClient.PeConnectionState.RAKNET_CONNECTED);
            System.out.println("RakNet connection established successfully!");
            peClient.sendPeLoginPacket(/* передать нужные данные: ник, UUID Java клиента, etc */);
            peClient.setPeState(PeClient.PeConnectionState.LOGGING_IN);
        } catch (Exception e) {
            System.err.println("Error parsing Open Connection Reply #2: " + e.getMessage());
            e.printStackTrace();
            ctx.close();
            peClient.shutdown();
        }
    }
    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        System.err.println("PE Client connection error:");
        cause.printStackTrace();
        ctx.close();
    }
    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        System.out.println("PE Client channel inactive.");
        super.channelInactive(ctx);
    }
}