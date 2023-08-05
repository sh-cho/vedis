package vedis;

import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.redis.*;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;

public class VedisServerHandler extends ChannelInboundHandlerAdapter {

    private final CountDownLatch shutdownLatch;

    VedisServerHandler(CountDownLatch shutdownLatch) {
        this.shutdownLatch = shutdownLatch;
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (!(msg instanceof final ArrayRedisMessage req)) {
            rejectMalformedRequest(ctx);
            return;
        }

        final List<RedisMessage> args = req.children();
        if (args.stream().anyMatch((arg) -> !(arg instanceof FullBulkStringRedisMessage))) {
            rejectMalformedRequest(ctx);
            return;
        }

        // For simplicity, convert all arguments into strings
        // In production, handle them with byte[]
        final List<String> strArgs =
                args.stream()
                    .map(FullBulkStringRedisMessage.class::cast)
                    .map(bulkStr -> {
                        if (bulkStr.isNull()) {
                            return null;
                        } else {
                            return bulkStr.content().toString(StandardCharsets.UTF_8);
                        }
                    })
                    .toList();

        final String command = strArgs.get(0);
        System.err.println(ctx.channel() + " RCVD: " + strArgs);

        switch (command) {
            case "COMMAND":  // dummy response
                ctx.writeAndFlush(ArrayRedisMessage.EMPTY_INSTANCE);
                break;
            case "GET":
                ctx.writeAndFlush(new FullBulkStringRedisMessage(Unpooled.copiedBuffer("hi!", StandardCharsets.UTF_8)));
                break;
            case "SET":
                ctx.writeAndFlush(new FullBulkStringRedisMessage(Unpooled.copiedBuffer("wow", StandardCharsets.UTF_8)));
                break;
            case "SHUTDOWN":
                ctx.writeAndFlush(new SimpleStringRedisMessage("OK"))
                   .addListener((ChannelFutureListener) f -> {
                       f.channel().close();
                       shutdownLatch.countDown();
                   });
            default:
                reject(ctx, "ERR Unsupported command");
        }
    }

    private void rejectMalformedRequest(final ChannelHandlerContext ctx) {
        reject(ctx, "ERR Client request must be an array of bulk strings");
    }

    private static void reject(ChannelHandlerContext ctx, String error) {
        ctx.writeAndFlush(new ErrorRedisMessage(error));
    }

    @Override
    public void exceptionCaught(final ChannelHandlerContext ctx, final Throwable cause) throws Exception {
        System.err.println("Unexpected exception handling " + ctx.channel());
        cause.printStackTrace(System.err);
        ctx.close();
    }
}
