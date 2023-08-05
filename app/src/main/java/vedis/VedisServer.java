package vedis;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.redis.RedisArrayAggregator;
import io.netty.handler.codec.redis.RedisBulkStringAggregator;
import io.netty.handler.codec.redis.RedisDecoder;
import io.netty.handler.codec.redis.RedisEncoder;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CountDownLatch;

public class VedisServer {

    public static final int PORT = Integer.parseInt(System.getProperty("port", "6379"));

    public static void main(String[] args) throws Exception {
        final EventLoopGroup bossGroup = new NioEventLoopGroup(1);
        final EventLoopGroup workerGroup = new NioEventLoopGroup();

        // it becomes zero when received `shutdown` command
        final CountDownLatch shutdownLatch = new CountDownLatch(1);

        final ConcurrentMap<String, String> map = new ConcurrentHashMap<>();

        try {
            final ServerBootstrap b = new ServerBootstrap();
            b.channel(NioServerSocketChannel.class);
            b.group(bossGroup, workerGroup);
            b.childHandler(new ChannelInitializer<SocketChannel>() {
                @Override
                protected void initChannel(SocketChannel ch) throws Exception {
                    final ChannelPipeline p = ch.pipeline();
                    p.addLast(new RedisDecoder());
                    p.addLast(new RedisBulkStringAggregator());
                    p.addLast(new RedisArrayAggregator());
                    p.addLast(new RedisEncoder());
                    p.addLast(new VedisServerHandler(map, shutdownLatch));
                }
            });
            final Channel ch = b.bind(PORT).sync().channel();
            System.err.println("Redis server listening at " + ch.localAddress());

            // wait until the latch becomes zero
            shutdownLatch.await();
            System.err.println("Received a SHUTDOWN command; shtting down..");
        } finally {
            bossGroup.shutdownGracefully();
            workerGroup.shutdownGracefully();
        }
    }

    private VedisServer() {

    }
}
