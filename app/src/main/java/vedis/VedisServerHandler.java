package vedis;

import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.redis.*;
import io.netty.util.ReferenceCountUtil;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CountDownLatch;

public class VedisServerHandler extends ChannelInboundHandlerAdapter {

    private final ConcurrentMap<String, String> map;
    private final CountDownLatch shutdownLatch;

    VedisServerHandler(ConcurrentMap<String, String> map, CountDownLatch shutdownLatch) {
        this.map = map;
        this.shutdownLatch = shutdownLatch;
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        try {
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
                            if (!bulkStr.isNull()) {
                                return bulkStr.content().toString(StandardCharsets.UTF_8);
                            } else {
                                return null;
                            }
                        })
                        .toList();

            final String command = strArgs.get(0);
            System.err.println(ctx.channel() + " RCVD: " + strArgs);

            switch (command) {
                case "COMMAND" ->  // dummy response
                        ctx.writeAndFlush(ArrayRedisMessage.EMPTY_INSTANCE);
                case "GET" -> handleGet(ctx, strArgs);
                case "SET" -> handleSet(ctx, strArgs);
                case "DEL" -> handleDel(ctx, strArgs);
                case "SHUTDOWN" -> ctx.writeAndFlush(new SimpleStringRedisMessage("OK"))
                                      .addListener((ChannelFutureListener) f -> shutdownLatch.countDown());
                default -> reject(ctx, "ERR Unsupported command");
            }
        } finally {
            ReferenceCountUtil.release(msg);
        }
    }

    private void handleGet(final ChannelHandlerContext ctx, final List<String> strArgs) {
        if (strArgs.size() < 2) {
            reject(ctx, "ERR GET command requires a key argument");
            return;
        }

        final String key = strArgs.get(1);
        if (key == null) {
            rejectNilKey(ctx);
            return;
        }

        final String value = map.get(key);
        final FullBulkStringRedisMessage reply;
        if (value != null) {
            reply = newBulkStringMessage(value);
        } else {
            reply = FullBulkStringRedisMessage.NULL_INSTANCE;
        }
        ctx.writeAndFlush(reply);
    }

    private void handleSet(final ChannelHandlerContext ctx, final List<String> strArgs) {
        if (strArgs.size() < 3) {
            reject(ctx, "ERR SET command requires a key, value argument");
            return;
        }
        final String key = strArgs.get(1);
        if (key == null) {
            rejectNilKey(ctx);
            return;
        }
        final String value = strArgs.get(2);
        if (value == null) {
            rejectNilValue(ctx);
            return;
        }

        // naive version
        final boolean shouldReplyOldValue = strArgs.size() > 3 && "GET".equals(strArgs.get(3));
        final String oldValue = map.put(key, value);
        final RedisMessage reply;
        if (shouldReplyOldValue) {
            if (oldValue != null) {
                reply = newBulkStringMessage(oldValue);
            } else {
                reply = FullBulkStringRedisMessage.NULL_INSTANCE;
            }
        } else {
            reply = new SimpleStringRedisMessage("OK");
        }

        ctx.writeAndFlush(reply);
    }

    private void handleDel(final ChannelHandlerContext ctx, final List<String> strArgs) {
        if (strArgs.size() < 2) {
            reject(ctx, "ERR DEL command requires at least one key argument");
            return;
        }

        int removedEntries = 0;
        for (int i = 1; i < strArgs.size(); i++) {
            final String key = strArgs.get(i);
            if (key == null) {
                continue;
            }
            if (map.remove(key) != null) {
                removedEntries++;
            }
        }

        ctx.writeAndFlush(new IntegerRedisMessage(removedEntries));
    }

    private static FullBulkStringRedisMessage newBulkStringMessage(final String value) {
        return new FullBulkStringRedisMessage(
                Unpooled.copiedBuffer(value, StandardCharsets.UTF_8));
    }

    private static void rejectMalformedRequest(final ChannelHandlerContext ctx) {
        reject(ctx, "ERR Client request must be an array of bulk strings");
    }

    private static void rejectNilKey(final ChannelHandlerContext ctx) {
        reject(ctx, "ERR A nil key is not allowed");
    }

    private static void rejectNilValue(final ChannelHandlerContext ctx) {
        reject(ctx, "ERR A nil value is not allowed");
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
