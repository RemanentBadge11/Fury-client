package com.lionclient.feature.module.impl;

import com.lionclient.feature.module.Category;
import com.lionclient.feature.module.Module;
import com.lionclient.feature.setting.BooleanSetting;
import com.lionclient.feature.setting.NumberSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import java.util.ArrayList;
import java.util.List;

public class CPSModule extends Module {

    private final BooleanSetting leftClick = new BooleanSetting("Left Click", true);
    private final BooleanSetting rightClick = new BooleanSetting("Right Click", true);
    private final NumberSetting posX = new NumberSetting("X", 0, 2000, 1, 6);
    private final NumberSetting posY = new NumberSetting("Y", 0, 2000, 1, 30);
    private final BooleanSetting background = new BooleanSetting("Background", true);
    private final NumberSetting bgAlpha = new NumberSetting("BG Alpha", 0, 255, 5, 60);

    private static final List<Long> leftClicks = new ArrayList<>();
    private static final List<Long> rightClicks = new ArrayList<>();

    public static void addLeftClick() {
        leftClicks.add(System.currentTimeMillis());
    }

    public static void addRightClick() {
        rightClicks.add(System.currentTimeMillis());
    }

    private boolean wasLeftDown = false;
    private boolean wasRightDown = false;

    public CPSModule() {
        super("CPS", "Displays clicks per second.", Category.CLIENT, Keyboard.KEY_NONE);
        addSetting(leftClick);
        addSetting(rightClick);
        addSetting(posX);
        addSetting(posY);
        addSetting(background);
        addSetting(bgAlpha);
    }

    @Override
    public boolean isVisible() {
        return false;
    }

    @Override
    public void onRenderOverlay(RenderGameOverlayEvent.Text event) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.gameSettings.showDebugInfo) return;

        boolean isLeftDown = Mouse.isButtonDown(0);
        boolean isRightDown = Mouse.isButtonDown(1);

        if (isLeftDown && !wasLeftDown) {
            addLeftClick();
        }
        if (isRightDown && !wasRightDown) {
            addRightClick();
        }

        wasLeftDown = isLeftDown;
        wasRightDown = isRightDown;

        long now = System.currentTimeMillis();
        leftClicks.removeIf(t -> now - t > 1000);
        rightClicks.removeIf(t -> now - t > 1000);

        int lCps = leftClicks.size();
        int rCps = rightClicks.size();

        ScaledResolution resolution = event.resolution;
        int x = (int) posX.getValue();
        int y = (int) posY.getValue();

        StringBuilder sb = new StringBuilder();
        if (leftClick.isEnabled()) {
            sb.append("L: ").append(lCps);
        }
        if (leftClick.isEnabled() && rightClick.isEnabled()) {
            sb.append("  ");
        }
        if (rightClick.isEnabled()) {
            sb.append("R: ").append(rCps);
        }

        String text = sb.toString();
        if (text.isEmpty()) return;

        int textWidth = mc.fontRendererObj.getStringWidth(text);

        if (background.isEnabled()) {
            int bgColor = (bgAlpha.getValue() << 24) | (0x141414);
            drawRect(x - 4, y - 12, x + textWidth + 8, y + 2, bgColor);
        }

        mc.fontRendererObj.drawStringWithShadow(text, x, y - 10, 0xFFFFFF);
    }

    private void drawRect(int x, int y, int x2, int y2, int color) {
        Minecraft mc = Minecraft.getMinecraft();
        int i = (color >> 16) & 0xFF;
        int j = (color >> 8) & 0xFF;
        int k = (color >> 0) & 0xFF;
        int alpha = (color >> 24) & 0xFF;
        net.minecraft.client.gui.Gui.drawRect(x, y, x2, y2, (alpha << 24) | (i << 16) | (j << 8) | k);
    }

    @Override
    public String getHudInfo() {
        int lCps = leftClicks.size();
        int rCps = rightClicks.size();
        return String.format("L: %d  R: %d", lCps, rCps);
    }
}