package com.lionclient.feature.module.impl;

import com.lionclient.feature.module.Category;
import com.lionclient.feature.module.Module;
import com.lionclient.feature.setting.BooleanSetting;
import com.lionclient.feature.setting.DecimalSetting;
import com.lionclient.feature.setting.NumberSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityEnderPearl;
import net.minecraft.entity.projectile.EntityArrow;
import net.minecraft.entity.projectile.EntityEgg;
import net.minecraft.entity.projectile.EntityFireball;
import net.minecraft.entity.projectile.EntitySnowball;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;

import java.awt.Color;

public final class IndicatorsModule extends Module {
    private static final ItemStack FIREBALL_STACK = new ItemStack(Items.fire_charge);
    private static final ItemStack PEARL_STACK = new ItemStack(Items.ender_pearl);
    private static final ItemStack ARROW_STACK = new ItemStack(Items.arrow);
    private static final ItemStack EGG_STACK = new ItemStack(Items.egg);
    private static final ItemStack SNOWBALL_STACK = new ItemStack(Items.snowball);

    private static final Color FIREBALL_COLOR = new Color(255, 100, 0);
    private static final Color PEARL_COLOR = new Color(37, 137, 186);
    private static final Color ARROW_COLOR = new Color(180, 180, 180);
    private static final Color EGG_COLOR = new Color(240, 230, 140);
    private static final Color SNOWBALL_COLOR = Color.WHITE;

    private final DecimalSetting scale = new DecimalSetting("Scale", 0.5D, 1.5D, 0.1D, 1.0D);
    private final NumberSetting offset = new NumberSetting("Offset", 0, 255, 5, 50);
    private final BooleanSetting directionCheck = new BooleanSetting("Direction Check", true);
    private final BooleanSetting fireballs = new BooleanSetting("Fireballs", true);
    private final BooleanSetting pearls = new BooleanSetting("Pearls", true);
    private final BooleanSetting arrows = new BooleanSetting("Arrows", true);
    private final BooleanSetting egg = new BooleanSetting("Egg", true);
    private final BooleanSetting snowball = new BooleanSetting("Snowball", true);

    public IndicatorsModule() {
        super("Indicators", "Displays 2D directional pointers for incoming projectiles.", Category.RENDER, Keyboard.KEY_NONE);
        addSetting(scale);
        addSetting(offset);
        addSetting(directionCheck);
        addSetting(fireballs);
        addSetting(pearls);
        addSetting(arrows);
        addSetting(egg);
        addSetting(snowball);
    }

    @SubscribeEvent
    public void onRenderOverlay(RenderGameOverlayEvent.Post event) {
        if (event.type != RenderGameOverlayEvent.ElementType.ALL || !isEnabled()) return;

        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null || mc.theWorld == null) return;

        ScaledResolution sr = new ScaledResolution(mc);
        float pTicks = event.partialTicks;
        float indicatorScale = (float) scale.getValue();
        float baseOffset = 10.0f + (float) offset.getValue();

        double playerX = lerp(mc.thePlayer.prevPosX, mc.thePlayer.posX, pTicks);
        double playerZ = lerp(mc.thePlayer.prevPosZ, mc.thePlayer.posZ, pTicks);

        for (Object obj : mc.theWorld.loadedEntityList) {
            if (!(obj instanceof Entity)) {
                continue;
            }
            Entity entity = (Entity) obj;
            if (!shouldRender(mc, entity)) {
                continue;
            }
            double entityX = lerp(entity.prevPosX, entity.posX, pTicks);
            double entityZ = lerp(entity.prevPosZ, entity.posZ, pTicks);

            float yawBetween = getYawBetween(playerX, playerZ, entityX, entityZ);
            if (mc.gameSettings.thirdPersonView == 2) {
                yawBetween += 180.0f;
            }

            float x = (float) Math.sin(Math.toRadians(yawBetween));
            float z = (float) Math.cos(Math.toRadians(yawBetween)) * -1.0f;

            GlStateManager.pushMatrix();
            GlStateManager.disableDepth();
            GlStateManager.enableBlend();
            GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, 1, 0);

            GlStateManager.scale(indicatorScale, indicatorScale, 0.0f);
            GlStateManager.translate((sr.getScaledWidth() / 2.0f) / indicatorScale, (sr.getScaledHeight() / 2.0f) / indicatorScale, 0.0f);

            // 1. Render Item GUI Icon
            GlStateManager.pushMatrix();
            GlStateManager.translate(baseOffset * x - 8.0f, baseOffset * z - 8.0f, -300.0f);
            mc.getRenderItem().renderItemAndEffectIntoGUI(getIndicatorStack(entity), 0, 0);
            GlStateManager.popMatrix();

            // 2. Render Distance Meter Text
            String distStr = String.format("%dm", (int) mc.thePlayer.getDistanceToEntity(entity));
            GlStateManager.pushMatrix();
            GlStateManager.translate(baseOffset * x - (mc.fontRendererObj.getStringWidth(distStr) / 2.0f) + 1.0f, baseOffset * z + 1.0f, -100.0f);
            mc.fontRendererObj.drawStringWithShadow(distStr, 0.0f, 0.0f, 0xFFE0E0E0);
            GlStateManager.popMatrix();

            // 3. Render Directional Arrow Triangle
            GlStateManager.pushMatrix();
            GlStateManager.translate((baseOffset + 15.0f) * x, (baseOffset + 15.0f) * z, -100.0f);
            drawArrow((float) (Math.atan2(z, x) + Math.PI), 7.5f, 3.5f, getIndicatorColor(entity));
            GlStateManager.popMatrix();

            GlStateManager.enableDepth();
            GlStateManager.disableBlend();
            GlStateManager.popMatrix();
        }
    }

    private boolean shouldRender(Minecraft mc, Entity entity) {
        if (entity == null || entity == mc.thePlayer) return false;

        double d = (entity.posX - entity.lastTickPosX) * (mc.thePlayer.posX - entity.posX)
                 + (entity.posY - entity.lastTickPosY) * (mc.thePlayer.posY + mc.thePlayer.getEyeHeight() - entity.posY - entity.height / 2.0)
                 + (entity.posZ - entity.lastTickPosZ) * (mc.thePlayer.posZ - entity.posZ);

        if (d < 0.0 && directionCheck.isEnabled()) {
            return false;
        }

        if (fireballs.isEnabled() && entity instanceof EntityFireball) return true;
        if (pearls.isEnabled() && entity instanceof EntityEnderPearl) return true;
        if (arrows.isEnabled() && entity instanceof EntityArrow) return true;
        if (egg.isEnabled() && entity instanceof EntityEgg) return true;
        if (snowball.isEnabled() && entity instanceof EntitySnowball) return true;

        return false;
    }

    private ItemStack getIndicatorStack(Entity entity) {
        if (entity instanceof EntityFireball) return FIREBALL_STACK;
        if (entity instanceof EntityEnderPearl) return PEARL_STACK;
        if (entity instanceof EntityArrow) return ARROW_STACK;
        if (entity instanceof EntityEgg) return EGG_STACK;
        if (entity instanceof EntitySnowball) return SNOWBALL_STACK;
        return ARROW_STACK;
    }

    private Color getIndicatorColor(Entity entity) {
        if (entity instanceof EntityFireball) return FIREBALL_COLOR;
        if (entity instanceof EntityEnderPearl) return PEARL_COLOR;
        if (entity instanceof EntityArrow) return ARROW_COLOR;
        if (entity instanceof EntityEgg) return EGG_COLOR;
        if (entity instanceof EntitySnowball) return SNOWBALL_COLOR;
        return Color.WHITE;
    }

    private float getYawBetween(double fromX, double fromZ, double toX, double toZ) {
        double diffX = toX - fromX;
        double diffZ = toZ - fromZ;
        float yaw = (float) (Math.atan2(diffZ, diffX) * 180.0 / Math.PI) - 90.0f;
        Minecraft mc = Minecraft.getMinecraft();
        return yaw - (mc.thePlayer != null ? mc.thePlayer.rotationYaw : 0.0f);
    }

    private void drawArrow(float angle, float length, float width, Color color) {
        GlStateManager.pushMatrix();
        GlStateManager.rotate((float) Math.toDegrees(angle), 0.0f, 0.0f, 1.0f);
        GlStateManager.disableTexture2D();
        GlStateManager.color(color.getRed() / 255.0f, color.getGreen() / 255.0f, color.getBlue() / 255.0f, 0.85f);

        net.minecraft.client.renderer.Tessellator tessellator = net.minecraft.client.renderer.Tessellator.getInstance();
        net.minecraft.client.renderer.WorldRenderer worldrenderer = tessellator.getWorldRenderer();
        worldrenderer.begin(GL11.GL_TRIANGLES, net.minecraft.client.renderer.vertex.DefaultVertexFormats.POSITION);
        worldrenderer.pos(-width, -length, 0.0D).endVertex();
        worldrenderer.pos(width, -length, 0.0D).endVertex();
        worldrenderer.pos(0.0f, length, 0.0D).endVertex();
        tessellator.draw();

        GlStateManager.enableTexture2D();
        GlStateManager.popMatrix();
    }

    private double lerp(double prev, double current, float partialTicks) {
        return prev + (current - prev) * partialTicks;
    }
}
