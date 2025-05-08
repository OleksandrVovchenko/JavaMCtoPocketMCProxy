package org.smnetworking;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
public class Main {
    private static final int JAVA_PROXY_PORT = 25565;
    private static final String PE_SERVER_IP = "nostalgiape.online";
    private static final int PE_SERVER_PORT = 19132;
    public static void main(String[] args) {
        System.out.println("Starting Minecraft Java-to-PE Proxy...");
        System.out.println("Listening for Java 1.8.9 clients on port " + JAVA_PROXY_PORT);
        System.out.println("Targeting PE 0.8.1 server at " + PE_SERVER_IP + ":" + PE_SERVER_PORT);
        EventLoopGroup bossGroup = new NioEventLoopGroup();
        EventLoopGroup workerGroup = new NioEventLoopGroup();
        try {
            ServerBootstrap b = new ServerBootstrap();
            b.group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        public void initChannel(SocketChannel ch) throws Exception {
                            ch.pipeline().addLast("frameDecoder", new JavaPacketDecoder());
                            ch.pipeline().addLast("frameEncoder", new JavaPacketEncoder());
                            ch.pipeline().addLast("handler", new JavaConnectionHandler(PE_SERVER_IP, PE_SERVER_PORT));
                        }
                    })
                    .option(ChannelOption.SO_BACKLOG, 128)
                    .childOption(ChannelOption.SO_KEEPALIVE, true);
            ChannelFuture f = b.bind(JAVA_PROXY_PORT).sync();
            System.out.println("Proxy server started successfully on port " + JAVA_PROXY_PORT);
            f.channel().closeFuture().sync();
        } catch (InterruptedException e) {
            System.err.println("Proxy server interrupted: " + e.getMessage());
        } finally {
            workerGroup.shutdownGracefully();
            bossGroup.shutdownGracefully();
            System.out.println("Proxy server shut down.");
        }
    }
}