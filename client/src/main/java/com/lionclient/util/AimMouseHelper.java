package com.lionclient.util;

import net.minecraft.util.MouseHelper;

/**
 * Custom MouseHelper that wraps the original and allows injecting
 * additional mouse pixel deltas. Injected deltas go through the
 * vanilla sensitivity → GCD pipeline in Entity.setAngles(), making
 * them indistinguishable from real mouse input to any anticheat.
 */
public class AimMouseHelper extends MouseHelper {

    private final MouseHelper original;
    private int injectDX;
    private int injectDY;

    // Exposes the real mouse delta (before injection) for blending logic
    public int realDeltaX;
    public int realDeltaY;

    public AimMouseHelper(MouseHelper original) {
        this.original = original;
    }

    @Override
    public void mouseXYChange() {
        // Read real mouse input from LWJGL buffer
        original.mouseXYChange();

        // Save the real delta before we add our injection
        realDeltaX = original.deltaX;
        realDeltaY = original.deltaY;

        // Combine real mouse + our injected correction
        this.deltaX = original.deltaX + injectDX;
        this.deltaY = original.deltaY + injectDY;

        // Consume injection
        injectDX = 0;
        injectDY = 0;
    }

    /**
     * Queue pixel deltas for injection on the next mouseXYChange() call.
     * Multiple inject() calls between frames accumulate additively.
     */
    public void inject(int dx, int dy) {
        injectDX += dx;
        injectDY += dy;
    }

    @Override
    public void grabMouseCursor() {
        original.grabMouseCursor();
    }

    @Override
    public void ungrabMouseCursor() {
        original.ungrabMouseCursor();
    }

    /** Get the original MouseHelper for restoration on disable. */
    public MouseHelper getOriginal() {
        return original;
    }
}
