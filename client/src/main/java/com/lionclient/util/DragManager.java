package com.lionclient.util;

public final class DragManager {
    private int dragX;
    private int dragY;
    private boolean dragging;

    public void startDrag(int mouseX, int mouseY, int currentX, int currentY) {
        this.dragX = mouseX - currentX;
        this.dragY = mouseY - currentY;
        this.dragging = true;
    }

    public void updateDrag(int mouseX, int mouseY, int screenWidth, int screenHeight, int width, int height, java.util.function.BiConsumer<Integer, Integer> updatePos) {
        if (dragging) {
            int newX = mouseX - dragX;
            int newY = mouseY - dragY;
            newX = Math.max(0, Math.min(newX, screenWidth - width));
            newY = Math.max(0, Math.min(newY, screenHeight - height));
            updatePos.accept(newX, newY);
        }
    }

    public void stopDrag() {
        this.dragging = false;
    }

    public boolean isDragging() {
        return dragging;
    }
}
