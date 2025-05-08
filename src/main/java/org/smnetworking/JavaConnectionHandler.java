package org.smnetworking;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;

import java.util.UUID;
public class JavaConnectionHandler extends ChannelInboundHandlerAdapter {
    private final String peServerIp;
    private final int peServerPort;
    private enum ConnectionState {
        HANDSHAKING,
        STATUS,
        LOGIN,
        PLAY
    }
    private ConnectionState currentState = ConnectionState.HANDSHAKING;
    public JavaConnectionHandler(String peServerIp, int peServerPort) {
        this.peServerIp = peServerIp;
        this.peServerPort = peServerPort;
        System.out.println("JavaConnectionHandler created for target " + peServerIp + ":" + peServerPort);
    }
    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        System.out.println("Java Client connected: " + ctx.channel().remoteAddress());
        super.channelActive(ctx);
    }
    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        ByteBuf packetBuf = (ByteBuf) msg;
        try {
            int packetId = ProtocolUtils.readVarInt(packetBuf);
            System.out.println("Received Java Packet ID: 0x" + Integer.toHexString(packetId) + " in state: " + currentState + " (Payload readable bytes: " + packetBuf.readableBytes() + ")");
            switch (currentState) {
                case HANDSHAKING:
                    handleHandshakePacket(ctx, packetId, packetBuf);
                    break;
                case STATUS:
                    handleStatusPacket(ctx, packetId, packetBuf);
                    break;
                case LOGIN:
                    handleLoginPacket(ctx, packetId, packetBuf);
                    break;
                case PLAY:
                    handlePlayPacket(ctx, packetId, packetBuf);
                    break;
            }
        } finally {
            packetBuf.release();
        }
    }
    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        cause.printStackTrace();
        ctx.close();
        System.err.println("Java Client connection error, closed.");
    }
    private void handleHandshakePacket(ChannelHandlerContext ctx, int packetId, ByteBuf packetBuf) {
        if (packetId == 0x00) {
            try {
                int protocolVersion = ProtocolUtils.readVarInt(packetBuf);
                String serverAddress = ProtocolUtils.readString(packetBuf);
                int serverPort = packetBuf.readUnsignedShort();
                int nextState = ProtocolUtils.readVarInt(packetBuf);
                System.out.println("Handshake received: Protocol=" + protocolVersion + ", Address=" + serverAddress + ", Port=" + serverPort + ", NextState=" + nextState);
                if (protocolVersion != 47) {
                    System.err.println("Unsupported protocol version: " + protocolVersion + ". Expected 47.");
                    ByteBuf disconnectPacket = ctx.alloc().buffer();
                    ProtocolUtils.writeVarInt(0x00, disconnectPacket);
                    ProtocolUtils.writeString("{\"text\":\"Unsupported protocol version! Expected 1.8.9 (47). Received " + protocolVersion + "\"}", disconnectPacket);
                    ctx.writeAndFlush(disconnectPacket);
                    ctx.channel().eventLoop().schedule(() -> {
                        ctx.close();
                    }, 50, java.util.concurrent.TimeUnit.MILLISECONDS);
                    return;
                }
                if (nextState == 1) {
                    currentState = ConnectionState.STATUS;
                    System.out.println("Switched state to STATUS");
                } else if (nextState == 2) {
                    currentState = ConnectionState.LOGIN;
                    System.out.println("Switched state to LOGIN");
                } else {
                    System.err.println("Invalid next state in Handshake: " + nextState);
                    ctx.close();
                }
            } catch (Exception e) {
                System.err.println("Error handling Handshake packet: " + e.getMessage());
                ctx.close();
            }
        } else {
            System.err.println("Logic error: handleHandshakePacket called with unexpected packet ID 0x" + Integer.toHexString(packetId));
            ctx.close();
        }
    }
    private void handleStatusPacket(ChannelHandlerContext ctx, int packetId, ByteBuf packetBuf) {
        if (packetId == 0x00) {
            System.out.println("Status Request received.");
        } else if (packetId == 0x01) {
            System.out.println("Ping received.");
            if (packetBuf.readableBytes() >= 8) {
                long payload = packetBuf.readLong();
                System.out.println("Ping payload: " + payload);
            } else {
                System.err.println("Ping packet too short.");
                ctx.close();
            }
        } else {
            System.err.println("Unexpected packet ID 0x" + Integer.toHexString(packetId) + " in STATUS state.");
            ctx.close();
        }
    }
    private void handleLoginPacket(ChannelHandlerContext ctx, int packetId, ByteBuf packetBuf) {
        if (packetId == 0x00) {
            try {
                String rawPlayerName = ProtocolUtils.readString(packetBuf);
                System.out.println("Login Start received: Raw Player Name=" + rawPlayerName);
                String cleanedPlayerName = rawPlayerName.replace("_", "");
                if (cleanedPlayerName.isEmpty()) {
                    cleanedPlayerName = "Player";
                    System.out.println("Player name was empty after cleanup, using default: " + cleanedPlayerName);
                }
                String playerName = cleanedPlayerName;
                System.out.println("Using cleaned Player Name=" + playerName);
                UUID playerId = UUID.randomUUID();
                PeClient peClient = new PeClient(peServerIp, peServerPort, playerName, playerId, ctx.channel());
                peClient.connect();
                ByteBuf successPacket = ctx.alloc().buffer();
                ProtocolUtils.writeVarInt(0x02, successPacket);
                ProtocolUtils.writeString(playerId.toString(), successPacket);
                ProtocolUtils.writeString(playerName, successPacket);
                ctx.writeAndFlush(successPacket);
                currentState = ConnectionState.PLAY;
                System.out.println("Java Client logged in as " + playerName + ". Switched state to PLAY.");
            } catch (Exception e) {
                System.err.println("Error handling Login Start packet: " + e.getMessage());
                ctx.close();
            }
        } else {
            System.err.println("Unexpected packet ID 0x" + Integer.toHexString(packetId) + " in LOGIN state.");
            ctx.close();
        }
    }
    private void handlePlayPacket(ChannelHandlerContext ctx, int packetId, ByteBuf packetBuf) {
        System.out.println("Received Play Packet ID: 0x" + Integer.toHexString(packetId) + " (needs translation)");
    }
}
