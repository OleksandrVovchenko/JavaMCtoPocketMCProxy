package org.smnetworking;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.DatagramPacket;
import io.netty.channel.socket.nio.NioDatagramChannel;

import java.net.InetSocketAddress;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
public class PeClient {
    private final String peServerIp;
    private final int peServerPort;
    private final String playerName;
    private final UUID playerId;
    private final Channel javaClientChannel;
    private EventLoopGroup group;
    private Channel peChannel;
    private InetSocketAddress peServerAddress;
    enum PeConnectionState {
        DISCONNECTED,
        CONNECTING_1_SENT,
        CONNECTING_2_SENT,
        RAKNET_CONNECTED,
        LOGGING_IN,
        CONNECTED
    }
    private PeConnectionState peState = PeConnectionState.DISCONNECTED;
    private static final byte[] RAKNET_MAGIC = {(byte) 0x00, (byte) 0xFF, (byte) 0xFF, (byte) 0x00, (byte) 0xFE, (byte) 0xFE, (byte) 0xFE, (byte) 0xFE, (byte) 0xFD, (byte) 0xFD, (byte) 0xFD, (byte) 0xFD, (byte) 0x12, (byte) 0x34, (byte) 0x56, (byte) 0x78};
    private static final byte RAKNET_PROTOCOL_VERSION = 0x05;
    private static final short RAKNET_MTU = 1492;
    private long clientGuid;
    public PeClient(String peServerIp, int peServerPort, String playerName, UUID playerId, Channel javaClientChannel) {
        this.peServerIp = peServerIp;
        this.peServerPort = peServerPort;
        this.playerName = playerName;
        this.playerId = playerId;
        this.javaClientChannel = javaClientChannel;
        this.peServerAddress = new InetSocketAddress(peServerIp, peServerPort);
        this.clientGuid = ProtocolUtils.generateClientGuid();
        this.serverGuid = 0;
        this.agreedMtu = RAKNET_MTU;
    }
    private long serverGuid;
    private short agreedMtu;
    public void connect() {
        System.out.println("Attempting to connect to PE server at " + peServerAddress);
        group = new NioEventLoopGroup();
        try {
            Bootstrap b = new Bootstrap();
            b.group(group)
                    .channel(NioDatagramChannel.class)
                    .handler(new PeConnectionHandler(this.javaClientChannel, this));
            ChannelFuture f = b.bind(0).sync();
            peChannel = f.channel();
            System.out.println("PE Client bound to local address: " + peChannel.localAddress());
            sendInitialPePacket();
            peState = PeConnectionState.CONNECTING_1_SENT;
        } catch (Exception e) {
            System.err.println("Error connecting to PE server: " + e.getMessage());
            e.printStackTrace();
            if (javaClientChannel.isActive()) {
                ByteBuf disconnectPacket = javaClientChannel.alloc().buffer();
                ProtocolUtils.writeVarInt(0x00, disconnectPacket);
                ProtocolUtils.writeString("{\"text\":\"Failed to connect to target PE server.\"}", disconnectPacket);
                javaClientChannel.writeAndFlush(disconnectPacket);
                javaClientChannel.eventLoop().schedule(() -> {
                    javaClientChannel.close();
                }, 50, TimeUnit.MILLISECONDS);
            }
            shutdown();
        }
    }
    private void sendInitialPePacket() {
        ByteBuf packetBuf = peChannel.alloc().buffer();
        packetBuf.writeByte(0x05);
        packetBuf.writeBytes(RAKNET_MAGIC);
        packetBuf.writeByte(RAKNET_PROTOCOL_VERSION);
        ProtocolUtils.writeLongLE(clientGuid, packetBuf);
        ProtocolUtils.writeShortLE(RAKNET_MTU, packetBuf);
        System.out.println("Sending PE packet Open Connection Request #1 (0x05) to " + peServerAddress + " with readable bytes: " + packetBuf.readableBytes());
        peChannel.writeAndFlush(new DatagramPacket(packetBuf, peServerAddress));
        peState = PeConnectionState.CONNECTING_1_SENT;
    }
    public void sendOpenConnectionRequest2() {
        ByteBuf packetBuf = peChannel.alloc().buffer();
        packetBuf.writeByte(0x07);
        packetBuf.writeBytes(RAKNET_MAGIC);
        ProtocolUtils.writeLongLE(this.serverGuid, packetBuf);
        InetSocketAddress localAddress = (InetSocketAddress) peChannel.localAddress();
        ProtocolUtils.writePEAddress(localAddress, packetBuf);
        ProtocolUtils.writeShortLE(this.agreedMtu, packetBuf);
        ProtocolUtils.writeLongLE(this.clientGuid, packetBuf);
        System.out.println("Sending PE packet Open Connection Request #2 (0x07) to " + peServerAddress + " with readable bytes: " + packetBuf.readableBytes());
        peChannel.writeAndFlush(new DatagramPacket(packetBuf, peServerAddress));
        peState = PeConnectionState.CONNECTING_2_SENT;
    }
    public void setServerGuid(long serverGuid) {
        this.serverGuid = serverGuid;
    }
    public void setAgreedMtu(short agreedMtu) {
        this.agreedMtu = agreedMtu;
        System.out.println("PE Client MTU set to: " + agreedMtu);
    }
    public void setPeState(PeConnectionState newState) {
        this.peState = newState;
        System.out.println("PE Client state changed to: " + newState);
    }
    public PeConnectionState getPeState() {
        return peState;
    }
    public void sendOpenConnectionRequest2(short serverMtu) {
        System.out.println("Sending PE packet Open Connection Request #2 (0x07) to " + peServerAddress);
    }
    public void sendPeLoginPacket() {
        System.out.println("Sending PE Minecraft Login packet to " + peServerAddress);
    }
    public void shutdown() {
        if (group != null) {
            if (peChannel != null) {
                peChannel.close();
            }
            group.shutdownGracefully();
            System.out.println("PE Client EventLoopGroup shut down.");
        }
    }
    public Channel getPeChannel() {
        return peChannel;
    }
    public InetSocketAddress getPeServerAddress() {
        return peServerAddress;
    }
    public long getClientGuid() {
        return clientGuid;
    }
}