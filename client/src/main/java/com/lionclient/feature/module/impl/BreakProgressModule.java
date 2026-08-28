package com.lionclient.feature.module.impl;

import com.lionclient.feature.module.Category;
import com.lionclient.feature.module.Module;
import com.lionclient.feature.setting.BooleanSetting;
import com.lionclient.feature.setting.EnumSetting;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.multiplayer.PlayerControllerMP;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.BlockPos;
import net.minecraft.util.MovingObjectPosition;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.ReflectionHelper;
import org.lwjgl.input.Keyboard;

import java.lang.reflect.Field;

public final class BreakProgressModule extends Module {

    public enum DisplayMode {
        PERCENTAGE("Percentage"),
        SECOND("Second"),
        DECIMAL("Decimal");

        private final String name;
        DisplayMode(String name) { this.name = name; }
        @Override public String toString() { return name; }
    }

    private final EnumSetting<DisplayMode> mode = new EnumSetting<>("Mode", DisplayMode.values(), DisplayMode.PERCENTAGE);
    private final BooleanSetting manual = new BooleanSetting("Show Manual", true);
    private final BooleanSetting progressBar = new BooleanSetting("Progress Bar", false);

    private double progress;
    private double animatedProgress;
    private BlockPos block;
    private String progressStr = "";

    private static final Field curBlockDamageMPField = findCurBlockDamageMPField();

    public BreakProgressModule() {
        super("BreakProgress", "Shows block breaking progress in 3D world space.", Category.RENDER, Keyboard.KEY_NONE);
        addSetting(mode);
        addSetting(manual);
        addSetting(progressBar);
    }

    @Override
    protected void onEnable() {
        resetVariables();
    }

    @Override
    protected void onDisable() {
        resetVariables();
    }

    @Override
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START || !isEnabled()) return;
        updateProgress();
        animatedProgress += (progress - animatedProgress) * 0.35D;
    }

    @Override
    public void onRenderWorld(RenderWorldLastEvent event) {
        if (!isEnabled() || progress == 0.0D || block == null) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null || mc.theWorld == null || mc.getRenderManager() == null) return;

        double x = block.getX() + 0.5D - mc.getRenderManager().viewerPosX;
        double y = block.getY() + 0.5D - mc.getRenderManager().viewerPosY;
        double z = block.getZ() + 0.5D - mc.getRenderManager().viewerPosZ;

        GlStateManager.pushMatrix();
        GlStateManager.translate((float) x, (float) y, (float) z);
        GlStateManager.rotate(-mc.getRenderManager().playerViewY, 0.0F, 1.0F, 0.0F);
        GlStateManager.rotate(mc.getRenderManager().playerViewX, 1.0F, 0.0F, 0.0F);
        GlStateManager.scale(-0.02266667F, -0.02266667F, -0.02266667F);
        GlStateManager.depthMask(false);
        GlStateManager.disableDepth();

        int textWidth = mc.fontRendererObj.getStringWidth(progressStr);
        mc.fontRendererObj.drawString(progressStr, -textWidth / 2.0F, -8.0F, -1, true);
        if (progressBar.isEnabled()) {
            drawProgressBar();
        }

        GlStateManager.enableDepth();
        GlStateManager.depthMask(true);
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.popMatrix();
    }

    private void drawProgressBar() {
        int width = 42;
        int height = 4;
        int filled = (int) Math.round(width * Math.max(0.0D, Math.min(1.0D, animatedProgress)));
        Gui.drawRect(-width / 2, 4, width / 2, 4 + height, 0xAA101018);
        Gui.drawRect(-width / 2, 4, -width / 2 + filled, 4 + height, 0xFFD8FB6D);
    }

    private void updateProgress() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null || mc.theWorld == null || mc.playerController == null) {
            resetVariables();
            return;
        }

        if (mc.thePlayer.capabilities.isCreativeMode || !mc.thePlayer.capabilities.allowEdit) {
            resetVariables();
            return;
        }

        if (manual.isEnabled() && (mc.objectMouseOver == null || mc.objectMouseOver.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK)) {
            resetVariables();
            return;
        }

        progress = getCurBlockDamageMP(mc.playerController);
        if (progress == 0.0D) {
            resetVariables();
            return;
        }

        if (mc.objectMouseOver != null && mc.objectMouseOver.getBlockPos() != null) {
            block = mc.objectMouseOver.getBlockPos();
        }

        setProgressText(mc);
    }

    private void setProgressText(Minecraft mc) {
        switch (mode.getValue()) {
            case PERCENTAGE:
                progressStr = (int) (100.0D * progress) + "%";
                break;
            case SECOND:
                progressStr = getTimeLeftText(mc);
                break;
            case DECIMAL:
                progressStr = String.format("%.2f", progress);
                break;
            default:
                progressStr = "";
                break;
        }
    }

    private String getTimeLeftText(Minecraft mc) {
        if (block == null || mc.theWorld == null || mc.thePlayer == null) return "0s";
        Block targetBlock = mc.theWorld.getBlockState(block).getBlock();
        float hardness = targetBlock.getPlayerRelativeBlockHardness(mc.thePlayer, mc.theWorld, block);
        if (hardness <= 0.0F) return "0s";
        double ticksLeft = Math.max(0.0D, 1.0D - progress) / hardness;
        double seconds = Math.round((ticksLeft / 20.0D) * 10.0D) / 10.0D;
        return seconds == 0.0D ? "0s" : seconds + "s";
    }

    private void resetVariables() {
        progress = 0.0D;
        animatedProgress = 0.0D;
        block = null;
        progressStr = "";
    }

    private static float getCurBlockDamageMP(PlayerControllerMP controller) {
        if (controller == null || curBlockDamageMPField == null) return 0.0f;
        try {
            return curBlockDamageMPField.getFloat(controller);
        } catch (Throwable ignored) {
            return 0.0f;
        }
    }

    private static Field findCurBlockDamageMPField() {
        try {
            Field field = ReflectionHelper.findField(PlayerControllerMP.class, "field_78770_f", "curBlockDamageMP");
            field.setAccessible(true);
            return field;
        } catch (Throwable ignored) {
            return null;
        }
    }

    @Override
    public String getHudInfo() {
        return mode.getValue().toString();
    }
}
