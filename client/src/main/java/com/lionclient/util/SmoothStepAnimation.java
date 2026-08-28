package com.lionclient.util;

public final class SmoothStepAnimation {
    private final long duration;
    private long startTime;
    private float startValue;
    private float targetValue;
    
    public SmoothStepAnimation(long duration) {
        this.duration = duration;
        this.startTime = System.currentTimeMillis();
        this.startValue = 0.0F;
        this.targetValue = 0.0F;
    }
    
    public void interpolate(float target) {
        if (target != targetValue) {
            this.startValue = getValue();
            this.targetValue = target;
            this.startTime = System.currentTimeMillis();
        }
    }
    
    public float getValue() {
        long elapsed = System.currentTimeMillis() - startTime;
        if (elapsed >= duration) {
            return targetValue;
        }
        float x = (float) elapsed / duration;
        float step = x * x * (3.0F - 2.0F * x);
        return startValue + (targetValue - startValue) * step;
    }
}
