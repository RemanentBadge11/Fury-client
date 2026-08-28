package com.lionclient.network;

import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;

import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * FlushConsolidationHandler backported/adapted for Netty 4.0.23 (Minecraft 1.8.9).
 * Batches packet flushes into a single flush to drastically reduce kernel syscalls.
 */
public class FlushConsolidationHandler extends ChannelDuplexHandler {
    private final int explicitFlushAfterFlushes;
    private final boolean consolidateWhenNoReadInProgress;
    private final Runnable flushTask;

    private int flushPendingCount;
    private boolean readInProgress;
    private ChannelHandlerContext ctx;
    private ScheduledFuture<?> nextScheduledFlush;

    public FlushConsolidationHandler(int explicitFlushAfterFlushes, boolean consolidateWhenNoReadInProgress) {
        if (explicitFlushAfterFlushes <= 0) {
            throw new IllegalArgumentException("explicitFlushAfterFlushes: " + explicitFlushAfterFlushes + " (expected: > 0)");
        }
        this.explicitFlushAfterFlushes = explicitFlushAfterFlushes;
        this.consolidateWhenNoReadInProgress = consolidateWhenNoReadInProgress;
        this.flushTask = new Runnable() {
            @Override
            public void run() {
                flushNow(ctx);
            }
        };
    }

    public FlushConsolidationHandler() {
        this(256, false);
    }

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
        this.ctx = ctx;
    }

    @Override
    public void flush(ChannelHandlerContext ctx) throws Exception {
        if (readInProgress) {
            if (++flushPendingCount == explicitFlushAfterFlushes) {
                flushNow(ctx);
            }
        } else if (consolidateWhenNoReadInProgress) {
            if (++flushPendingCount == explicitFlushAfterFlushes) {
                flushNow(ctx);
            } else {
                scheduleFlush(ctx);
            }
        } else {
            flushNow(ctx);
        }
    }

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
        resetReadAndFlushIfNeeded(ctx);
        ctx.fireChannelReadComplete();
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        readInProgress = true;
        ctx.fireChannelRead(msg);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        resetReadAndFlushIfNeeded(ctx);
        ctx.fireExceptionCaught(cause);
    }

    @Override
    public void disconnect(ChannelHandlerContext ctx, ChannelPromise promise) throws Exception {
        resetReadAndFlushIfNeeded(ctx);
        ctx.disconnect(promise);
    }

    @Override
    public void close(ChannelHandlerContext ctx, ChannelPromise promise) throws Exception {
        resetReadAndFlushIfNeeded(ctx);
        ctx.close(promise);
    }

    private void scheduleFlush(final ChannelHandlerContext ctx) {
        if (nextScheduledFlush == null) {
            nextScheduledFlush = ctx.channel().eventLoop().schedule(flushTask, 0, TimeUnit.MILLISECONDS);
        }
    }

    private void cancelScheduledFlush() {
        if (nextScheduledFlush != null) {
            nextScheduledFlush.cancel(false);
            nextScheduledFlush = null;
        }
    }

    private void flushNow(ChannelHandlerContext ctx) {
        cancelScheduledFlush();
        flushPendingCount = 0;
        ctx.flush();
    }

    private void resetReadAndFlushIfNeeded(ChannelHandlerContext ctx) {
        readInProgress = false;
        if (flushPendingCount > 0) {
            flushNow(ctx);
        }
    }
}
