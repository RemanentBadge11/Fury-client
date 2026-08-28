package com.lionclient.feature.module.impl;

import com.lionclient.feature.module.Category;
import com.lionclient.feature.module.Module;
import com.lionclient.feature.setting.BooleanSetting;
import com.lionclient.feature.setting.NumberSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.item.ItemStack;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;

import java.awt.Color;
import java.util.HashMap;
import java.util.Map;

public class ArmorHudModule extends Module {

    private final NumberSetting posX = new NumberSetting("X", 0, 2000, 1, 2);
    private final NumberSetting posY = new NumberSetting("Y", 0, 2000, 1, 100);
    private final BooleanSetting background = new BooleanSetting("Background", true);
    private final NumberSetting bgAlpha = new NumberSetting("BG Alpha", 0, 255, 5, 60);

    private final Map<Integer, Float> barAnimations = new HashMap<>();

    private static final int ROW_HEIGHT = 26;
    private static final int ICON_SIZE = 16;
    private static final int PADDING = 6;
    private static final int DIVIDER_X = PADDING + ICON_SIZE + 6;
    private static final int BAR_WIDTH = 50;
    private static final float BAR_H = 3f;
    private static final int PANEL_WIDTH = DIVIDER_X + 1 + 8 + BAR_WIDTH + PADDING;
    private static final int PANEL_HEIGHT = PADDING + ROW_HEIGHT * 4 + PADDING;

    public ArmorHudModule() {
        super("ArmorHUD", "Displays your armor status.", Category.RENDER, Keyboard.KEY_NONE);
        addSetting(posX);
        addSetting(posY);
        addSetting(background);
        addSetting(bgAlpha);
    }

    @Override
    public void onRenderOverlay(RenderGameOverlayEvent.Text event) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.thePlayer == null) return;

        int x = (int) posX.getValue();
        int y = (int) posY.getValue();
        int w = PANEL_WIDTH;
        int h = PANEL_HEIGHT;

        if (background.isEnabled()) {
            int bgColor = (bgAlpha.getValue() << 24) | (0x141414);
            drawRect(x, y, x + w, y + h, bgColor);
        }

        int divX = x + DIVIDER_X;

        for (int slot = 3; slot >= 0; slot--) {
            int row = 3 - slot;
            int rowY = y + PADDING + row * ROW_HEIGHT;
            ItemStack stack = mc.thePlayer.inventory.armorInventory[slot];
            if (stack == null) continue;

            float durability = stack.getMaxDamage() == 0 ? 1.0f
                    : (float) (stack.getMaxDamage() - stack.getItemDamage()) / stack.getMaxDamage();
            float anim = barAnimations.getOrDefault(slot, durability);
            anim = Math.min(1.0f, anim + 0.1f * (durability - anim));
            barAnimations.put(slot, anim);

            int barX = divX;
            int barY = rowY + 16;

            Gui.drawRect(barX, barY, barX + BAR_WIDTH, barY + (int) BAR_H, 0xFF1E2832);
            int fillW = (int) (BAR_WIDTH * Math.max(0, Math.min(1, anim)));
            if (fillW > 0) {
                Gui.drawRect(barX, barY, barX + fillW, barY + (int) BAR_H, 
                    new Color(80, 200, 100, 255).getRGB());
            }
        }

        GlStateManager.enableTexture2D();
        GlStateManager.enableAlpha();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GlStateManager.color(1f, 1f, 1f, 1f);

        for (int slot = 3; slot >= 0; slot--) {
            int row = 3 - slot;
            int rowY = y + PADDING + row * ROW_HEIGHT;
            ItemStack stack = mc.thePlayer.inventory.armorInventory[slot];
            if (stack == null) continue;

            renderItemStack(stack, x + PADDING, rowY + (ROW_HEIGHT - ICON_SIZE) / 2);

            float durability = stack.getMaxDamage() == 0 ? 1.0f
                    : (float) (stack.getMaxDamage() - stack.getItemDamage()) / stack.getMaxDamage();
            String pctText = (int) (durability * 100) + "%";
            int textX = divX + 8;
            int textY = rowY + 3;

            mc.fontRendererObj.drawStringWithShadow(pctText, textX, textY, 0xFFFFFF);
        }

        GlStateManager.enableTexture2D();
        GlStateManager.enableAlpha();
        GlStateManager.color(1f, 1f, 1f, 1f);
        GlStateManager.bindTexture(0);
    }

    private void renderItemStack(ItemStack stack, int x, int y) {
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GlStateManager.enableTexture2D();
        GlStateManager.enableAlpha();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GlStateManager.enableDepth();
        GlStateManager.depthFunc(515);
        GlStateManager.color(1f, 1f, 1f, 1f);
        Minecraft.getMinecraft().getRenderItem().renderItemAndEffectIntoGUI(stack, x, y);
        GL11.glPopAttrib();
    }

    private void drawRect(int x, int y, int x2, int y2, int color) {
        int i = (color >> 16) & 0xFF;
        int j = (color >> 8) & 0xFF;
        int k = (color >> 0) & 0xFF;
        int alpha = (color >> 24) & 0xFF;
        Gui.drawRect(x, y, x2, y2, (alpha << 24) | (i << 16) | (j << 8) | k);
    }
}