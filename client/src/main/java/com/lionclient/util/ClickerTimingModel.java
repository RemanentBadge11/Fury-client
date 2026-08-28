package com.lionclient.util;

import java.util.Random;

/**
 * Advanced click-timing engine.
 *
 * Implements realistic, human-like click spacing based on the randomization
 * models used by modern premium clients:
 *
 *  - OFF        : legacy uniform random CPS (no behavioural shaping)
 *  - NORMAL     : Vape "Normal" — static randomization that picks a CPS value
 *                 inside the configured range each cycle (no gaussian shaping)
 *  - EXTRA      : Vape "Extra" — single Gaussian (normal) distribution around a
 *                 per-cycle CPS target, producing organic micro-variation
 *  - EXTRA_PLUS : LiquidBounce "NormalDistribution" — dual-band Gaussian model
 *                 with primary tapping rhythm + secondary micro-jitter, plus
 *                 occasional finger/hand-reset pauses and drag/butterfly style
 *                 bursts. Resists both statistical and model-based detection.
 *
 * The {@code strength} parameter (0..100) scales the standard deviation of the
 * distributions and the probability of the special behavioural events, letting
 * the user tune how "wild" vs "stable" the pattern looks.
 */
public final class ClickerTimingModel {

    private static final Random RANDOM = new Random();
    private static final double TWO_PI = 2.0 * Math.PI;

    private ClickerTimingModel() {}

    /** Standard normal sample via the Box-Muller transform. */
    public static double nextGaussian() {
        double u1 = Math.max(Double.MIN_VALUE, RANDOM.nextDouble());
        double u2 = RANDOM.nextDouble();
        return Math.sqrt(-2.0 * Math.log(u1)) * Math.cos(TWO_PI * u2);
    }

    public enum Technique {
        OFF,
        NORMAL,
        EXTRA,
        EXTRA_PLUS
    }

    /**
     * Computes the next inter-click delay in milliseconds.
     *
     * @param minCps    configured minimum CPS
     * @param maxCps    configured maximum CPS
     * @param technique randomization technique
     * @param strength  0..100 randomization strength
     */
    public static long computeDelayMillis(int minCps, int maxCps, Technique technique, int strength) {
        int lo = Math.max(1, minCps);
        int hi = Math.max(lo, maxCps);
        double factor = Math.max(0.0, Math.min(1.0, strength / 100.0));

        if (technique == Technique.OFF) {
            double cps = lo + RANDOM.nextDouble() * (hi - lo + 1);
            return Math.max(1L, Math.round(1000.0 / cps));
        }

        // Pick a per-cycle CPS target.
        double targetCps;
        if (technique == Technique.NORMAL) {
            targetCps = lo + RANDOM.nextDouble() * (hi - lo + 1);
        } else {
            // Gaussian centre between the bounds for a more organic average.
            double center = (lo + hi) / 2.0;
            double spread = (hi - lo) / 2.0 + 0.5;
            targetCps = center + nextGaussian() * spread * 0.6;
            targetCps = Math.max(lo * 0.85, Math.min(hi * 1.15, targetCps));
        }

        double baseMean = 1000.0 / targetCps; // mean inter-click interval (ms)
        double delay;

        if (technique == Technique.NORMAL) {
            // Flat uniform within the chosen target window, tiny jitter only.
            delay = baseMean + nextGaussian() * baseMean * 0.10 * (0.3 + factor);
        } else if (technique == Technique.EXTRA) {
            // Single Gaussian band; std dev scales with strength.
            double std = baseMean * (0.12 + 0.45 * factor);
            delay = baseMean + nextGaussian() * std;
        } else { // EXTRA_PLUS — dual-band Gaussian behavioural emulation
            double std1 = baseMean * (0.10 + 0.35 * factor); // primary rhythm
            double band2 = baseMean * (0.04 + 0.18 * factor) * nextGaussian(); // micro jitter
            delay = baseMean + nextGaussian() * std1 + band2;
        }

        // Behavioural special events — probability scales with strength.
        double p = 0.04 + 0.10 * factor;
        if (RANDOM.nextDouble() < p) {
            // Finger / hand-reset pause.
            delay *= (1.6 + RANDOM.nextDouble() * 1.8);
        } else if (RANDOM.nextDouble() < p * 0.8) {
            // Burst — short gap that emulates drag / butterfly double taps.
            delay *= (0.45 + RANDOM.nextDouble() * 0.40);
        }

        // Slow sinusoidal CPS drift so the average evolves over time.
        delay += Math.sin(System.nanoTime() / 62000000.0) * baseMean * 0.10 * factor;

        return Math.max(1L, Math.round(delay));
    }

    /** Convenience wrapper preserving the old 2-arg signature (OFF mode). */
    public static long computeDelayMillis(int minCps, int maxCps) {
        return computeDelayMillis(minCps, maxCps, Technique.OFF, 0);
    }
}