package com.lionclient.network;

import net.minecraft.network.Packet;

public class TimedPacket {
    public final Packet<?> packet;
    public final long releaseTime;

    public TimedPacket(Packet<?> packet, long releaseTime) {
        this.packet = packet;
        this.releaseTime = releaseTime;
    }

    public boolean elapsed(long delay) {
        return System.currentTimeMillis() >= releaseTime;
    }
}