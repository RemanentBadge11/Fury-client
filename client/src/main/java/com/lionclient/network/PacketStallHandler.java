package com.lionclient.network;

import com.lionclient.LionClient;
import net.minecraft.client.Minecraft;

import java.util.concurrent.ConcurrentLinkedQueue;

public class PacketStallHandler {
    private static final int MAX_RELEASES_PER_TICK = 50;
    private final ConcurrentLinkedQueue<TimedPacket> packetQueue = new ConcurrentLinkedQueue<>();
    private volatile boolean flushing = false;

    public void queuePacket(net.minecraft.network.Packet<?> packet, int delayMs) {
        if (flushing) {
            dispatch(packet);
            return;
        }

        long now = System.currentTimeMillis();
        long deliverAt = now + delayMs;

        packetQueue.offer(new TimedPacket(packet, deliverAt));
    }

    public void onTick() {
        if (packetQueue.isEmpty() && !flushing) {
            return;
        }

        long now = System.currentTimeMillis();

        flushing = true;
        try {
            while (true) {
                TimedPacket tp = packetQueue.peek();
                if (tp == null || tp.releaseTime > now) {
                    break;
                }

                packetQueue.poll();
                dispatch(tp.packet);
            }
        } finally {
            flushing = false;
        }
    }

    public void flush() {
        flushing = true;
        try {
            while (!packetQueue.isEmpty()) {
                TimedPacket tp = packetQueue.poll();
                if (tp != null) {
                    dispatch(tp.packet);
                }
            }
        } finally {
            flushing = false;
        }
    }

    public void clear() {
        packetQueue.clear();
        flushing = false;
    }

    public boolean isDelayActive() {
        return flushing || !packetQueue.isEmpty();
    }

    public int getQueueSize() {
        return packetQueue.size();
    }

    private void dispatch(net.minecraft.network.Packet<?> packet) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.getNetHandler() == null) return;

        if (isOutboundPacket(packet)) {
            try {
                mc.getNetHandler().addToSendQueue((net.minecraft.network.Packet) packet);
            } catch (Exception ignored) {}
        } else {
            LionClient client = LionClient.getInstance();
            if (client != null) {
                client.getModuleManager().onInboundPacketReleased(packet);
            }

            try {
                ((net.minecraft.network.Packet) packet).processPacket(mc.getNetHandler());
            } catch (Exception ignored) {}
        }
    }

    private static boolean isOutboundPacket(net.minecraft.network.Packet<?> packet) {
        if (packet == null) return false;
        String name = packet.getClass().getName();
        return name.contains(".client.") || packet.getClass().getSimpleName().startsWith("C");
    }
}