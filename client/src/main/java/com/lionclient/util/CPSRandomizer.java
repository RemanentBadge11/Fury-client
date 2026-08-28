package com.lionclient.util;

import java.util.concurrent.ThreadLocalRandom;

public class CPSRandomizer {
    private double currentCPSDrift = 0.0;
    private double driftTarget = 0.0;
    private long driftTimer = 0L;
    private long driftInterval = 30000L;

    public double getCPS(int minCPS, int maxCPS, boolean cpsDrift) {
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        double base = rnd.nextDouble(minCPS, maxCPS + 0.99);

        if (!cpsDrift) {
            return base;
        }

        long now = System.currentTimeMillis();
        if (now - driftTimer > driftInterval) {
            driftTimer = now;
            driftInterval = rnd.nextLong(20000L, 45000L);
            driftTarget = rnd.nextDouble(-1.5, 1.5);
        }

        currentCPSDrift = currentCPSDrift * 0.95 + driftTarget * 0.05;
        double finalCPS = base + currentCPSDrift;
        return Math.max(minCPS, Math.min(maxCPS + 2.0, finalCPS));
    }
}
