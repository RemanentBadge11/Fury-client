package com.lionclient.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.concurrent.ThreadLocalRandom;

public class ExtraPlusPattern {
    private static final int SLOTS = 20;
    private final ArrayList<Boolean> attackList = new ArrayList<>(SLOTS);
    private int index = 0;

    public void setup(int aps) {
        index = 0;
        attackList.clear();

        int targetAttacks = Math.max(1, Math.min(SLOTS - 1, aps));

        for (int i = 0; i < SLOTS; i++) {
            attackList.add(i < targetAttacks);
        }

        Collections.shuffle(attackList);

        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        if (rnd.nextDouble() < 0.35) {
            int burstLen = rnd.nextInt(2, 5);
            int startIdx = rnd.nextInt(0, Math.max(1, SLOTS - burstLen));
            for (int i = 0; i < burstLen; i++) {
                attackList.set(startIdx + i, true);
            }
        }
    }

    public boolean check() {
        return attackList.isEmpty() || index >= attackList.size();
    }

    public boolean nextAttack() {
        if (attackList.isEmpty() || index >= attackList.size()) {
            return false;
        }
        boolean val = attackList.get(index);
        index++;
        return val;
    }
}
