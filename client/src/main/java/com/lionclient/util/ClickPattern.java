package com.lionclient.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Random;

/**
 * Tick-based click pattern engine, modeled on Sakura Client's ExtraPattern
 * and ExtraPlusPattern, with Vape-style randomization tiers.
 *
 * The game runs at 20 ticks/sec, so each 20-slot cycle maps directly to
 * one second. A CPS of 15 means 15 attack ticks out of 20, shuffled for
 * realistic spacing. ExtraPlus adds fatigue pauses and burst windows.
 */
public final class ClickPattern {

    private static final Random RANDOM = new Random();
    private static final int SLOTS = 20;

    private final ArrayList<Boolean> attackList = new ArrayList<>(SLOTS);
    private int index;
    private double baseCps;
    private int extraPlusBurstWindow;
    private int extraPlusFatigueCount;
    private int extraPlusFatigueCounter;
    private boolean extraPlusFatigueActive;

    private ClickPattern() {}

    public static ClickPattern create() {
        return new ClickPattern();
    }

    public void reconfigure(int minCps, int maxCps, int randStrength, PatternTechnique technique) {
        baseCps = pickCps(minCps, maxCps, randStrength, technique);
        int aps = (int) Math.round(baseCps);
        aps = Math.max(1, Math.min(SLOTS, aps));

        index = 0;
        attackList.clear();

        for (int i = 0; i < SLOTS; i++) {
            attackList.add(i < aps);
        }

        if (technique == PatternTechnique.EXTRA_PLUS) {
            applyExtraPlus();
        } else {
            Collections.shuffle(attackList);
        }
    }

    public boolean check() {
        return attackList.isEmpty() || index >= attackList.size();
    }

    public boolean nextAttack() {
        boolean attack = attackList.get(index);
        index++;
        return attack;
    }

    public int remaining() {
        return Math.max(0, attackList.size() - index);
    }

    private double pickCps(int minCps, int maxCps, int randStrength, PatternTechnique technique) {
        double lo = Math.max(1, minCps);
        double hi = Math.max(lo, maxCps);

        if (technique == PatternTechnique.OFF) {
            return lo + RANDOM.nextDouble() * (hi - lo);
        }

        double factor = Math.max(0.0, Math.min(1.0, randStrength / 100.0));

        if (technique == PatternTechnique.NORMAL) {
            return lo + RANDOM.nextDouble() * (hi - lo);
        }

        double center = (lo + hi) / 2.0;
        double spread = (hi - lo) * 0.35 * (0.5 + factor);
        double cps = center + nextGaussian() * spread;

        double drift = Math.sin(System.nanoTime() / 55000000.0) * 0.3 * factor;
        cps += drift;
        cps = Math.max(lo, Math.min(hi, cps));
        return cps;
    }

    private void applyExtraPlus() {
        boolean applyFatigue = RANDOM.nextDouble() < 0.30;

        if (applyFatigue) {
            extraPlusFatigueCounter = 0;
            extraPlusFatigueCount = 2 + RANDOM.nextInt(4);
            extraPlusFatigueActive = true;
        } else {
            extraPlusFatigueActive = false;
        }

        Collections.shuffle(attackList);

        if (!extraPlusFatigueActive) {
            extraPlusBurstWindow = 2 + RANDOM.nextInt(4);
            int startRem = RANDOM.nextInt(attackList.size() - extraPlusBurstWindow);
            for (int i = 0; i < extraPlusBurstWindow; i++) {
                attackList.set(startRem + i, false);
            }
        } else {
            int startRem = RANDOM.nextInt(attackList.size() - extraPlusFatigueCount);
            for (int i = 0; i < 2; i++) {
                attackList.set(startRem + i, false);
            }
        }
    }

    public boolean extraPlusShouldSlow() {
        if (!extraPlusFatigueActive) return false;
        extraPlusFatigueCounter++;
        return extraPlusFatigueCounter < extraPlusFatigueCount;
    }

    private static double nextGaussian() {
        double u1 = Math.max(Double.MIN_VALUE, RANDOM.nextDouble());
        double u2 = RANDOM.nextDouble();
        return Math.sqrt(-2.0 * Math.log(u1)) * Math.cos(2.0 * Math.PI * u2);
    }

    public enum PatternTechnique {
        OFF,
        NORMAL,
        EXTRA,
        EXTRA_PLUS
    }
}