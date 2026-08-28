package com.lionclient.feature.module.impl;

import com.lionclient.feature.module.Category;
import com.lionclient.feature.module.Module;
import com.lionclient.feature.setting.BooleanSetting;
import com.lionclient.feature.setting.DecimalSetting;
import com.lionclient.feature.setting.EnumSetting;
import com.lionclient.feature.setting.NumberSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.C02PacketUseEntity;
import net.minecraft.network.play.client.C03PacketPlayer;
import net.minecraft.network.play.client.C0FPacketConfirmTransaction;
import net.minecraft.network.play.client.C00PacketKeepAlive;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.Vec3;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Advanced LagRange module engineered to bypass modern anti-cheats (Matrix, GrimAC, Polar).
 * 
 * Uses micro-pulse choking & transaction synchronization to prevent Timer, Speed,
 * BadPackets, and Post/Prediction flags.
 */
public final class LagRangeModule extends Module {

    public enum BypassMode {
        GRIM("Grim"),
        POLAR("Polar"),
        MATRIX("Matrix"),
        GENERIC("Generic");

        private final String name;
        BypassMode(String name) { this.name = name; }
        @Override public String toString() { return name; }
    }

    private final EnumSetting<BypassMode> mode = new EnumSetting<>("Mode", BypassMode.values(), BypassMode.GRIM);
    private final DecimalSetting range = new DecimalSetting("Range", 3.0, 7.0, 0.1, 4.5);
    private final NumberSetting maxDelay = new NumberSetting("Max Delay", 50, 500, 10, 150);
    private final BooleanSetting pulseMode = new BooleanSetting("Pulse Mode", true);
    private final NumberSetting pulseInterval = new NumberSetting("Pulse Interval", 50, 400, 10, 120);

    private final BooleanSetting sprintReset = new BooleanSetting("Sprint Reset", true);
    private final BooleanSetting useSplashPotion = new BooleanSetting("Use Splash Potion", true);
    private final BooleanSetting attackFlush = new BooleanSetting("Flush On Attack", true);
    private final BooleanSetting holdingWeapon = new BooleanSetting("Holding Weapon", true);
    private final BooleanSetting indicator = new BooleanSetting("Ghost Indicator", true);

    private EntityPlayer target;
    private boolean isLagging;
    private boolean isSprinting;
    private long lastSprintResetTime;
    private long lagStartTimeMs;
    private int chokedTicks;
    private Vec3 laggedPosition;

    public LagRangeModule() {
        super("LagRange", "Keeps you out of opponent's hit range using synchronized lag.", Category.COMBAT, Keyboard.KEY_NONE);
        addSetting(mode);
        addSetting(range);
        addSetting(maxDelay);
        addSetting(pulseMode);
        addSetting(pulseInterval);
        addSetting(sprintReset);
        addSetting(useSplashPotion);
        addSetting(attackFlush);
        addSetting(holdingWeapon);
        addSetting(indicator);
    }

    @Override
    protected void onEnable() {
        target = null;
        isLagging = false;
        lastSprintResetTime = 0;
        lagStartTimeMs = 0;
        chokedTicks = 0;
        laggedPosition = null;
    }

    @Override
    protected void onDisable() {
        flushLag();
    }

    @Override
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;

        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null || mc.theWorld == null || mc.thePlayer.isDead) {
            flushLag();
            return;
        }

        if (mc.currentScreen != null) {
            flushLag();
            return;
        }

        if (shouldFlush()) {
            flushLag();
            return;
        }

        if (mc.thePlayer.isSprinting() && !isSprinting) {
            lastSprintResetTime = System.currentTimeMillis();
        }
        isSprinting = mc.thePlayer.isSprinting();

        EntityPlayer newTarget = findTarget(mc);
        if (newTarget != null && newTarget != target) {
            target = newTarget;
        }

        if (target != null) {
            double dist = mc.thePlayer.getDistanceToEntity(target);
            if (dist > range.getValue()) {
                flushLag();
                return;
            }
        } else {
            flushLag();
            return;
        }

        if (!shouldLag(mc)) {
            flushLag();
            return;
        }

        long now = System.currentTimeMillis();

        // Check pulse timeout / anti-cheat delay caps
        int effectiveMaxDelay = getEffectiveMaxDelay();
        if (isLagging && (now - lagStartTimeMs >= effectiveMaxDelay)) {
            flushLag();
            // Start next pulse on next tick
            return;
        }

        if (!isLagging) {
            startLag();
        } else {
            chokedTicks++;
        }
    }

    private int getEffectiveMaxDelay() {
        switch (mode.getValue()) {
            case POLAR:
                return Math.min(120, pulseMode.isEnabled() ? pulseInterval.getValue() : maxDelay.getValue());
            case GRIM:
                return Math.min(160, pulseMode.isEnabled() ? pulseInterval.getValue() : maxDelay.getValue());
            case MATRIX:
                return Math.min(200, pulseMode.isEnabled() ? pulseInterval.getValue() : maxDelay.getValue());
            case GENERIC:
            default:
                return pulseMode.isEnabled() ? Math.min(maxDelay.getValue(), pulseInterval.getValue()) : maxDelay.getValue();
        }
    }

    @Override
    public int getOutboundPacketDelay(Packet<?> packet) {
        if (!isEnabled() || !isLagging || target == null) return 0;

        // Never delay critical sync/chat packets unless Grim/Polar mode requires packet order syncing
        if (isExemptSyncPacket(packet)) {
            return 0;
        }

        if (packet instanceof C02PacketUseEntity) {
            C02PacketUseEntity use = (C02PacketUseEntity) packet;
            if (use.getAction() == C02PacketUseEntity.Action.ATTACK) {
                if (attackFlush.isEnabled()) {
                    flushLag();
                    return 0;
                }
            }
        }

        int delayVal = getEffectiveMaxDelay();

        // Apply delay primarily to movement packets C03/C04/C05/C06
        if (packet instanceof C03PacketPlayer) {
            return delayVal;
        }

        // For Grim and Polar, also group transactions with movement to maintain strict sequence
        if (mode.getValue() == BypassMode.GRIM || mode.getValue() == BypassMode.POLAR) {
            if (packet instanceof C0FPacketConfirmTransaction || packet instanceof C00PacketKeepAlive) {
                return delayVal;
            }
        }

        return 0;
    }

    private boolean isExemptSyncPacket(Packet<?> packet) {
        if (packet == null) return false;
        String name = packet.getClass().getSimpleName();
        return name.contains("ChatMessage") || name.contains("C01PacketChatMessage") || name.contains("C14PacketTabComplete");
    }

    @Override
    public boolean isPacketDelayActive() {
        return isEnabled() && isLagging;
    }

    @Override
    public void onOutboundPacketQueued(Packet<?> packet) {
        if (!isEnabled() || !isLagging) return;

        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null) return;

        // Record the position at which server last saw us
        if (laggedPosition == null) {
            laggedPosition = new Vec3(mc.thePlayer.posX, mc.thePlayer.posY, mc.thePlayer.posZ);
        }
    }

    @Override
    public void onRenderWorld(RenderWorldLastEvent event) {
        if (!isEnabled() || !indicator.isEnabled()) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.theWorld == null || mc.thePlayer == null) return;
        if (mc.gameSettings.thirdPersonView == 0 && !isLagging) return;

        Vec3 laggedPos = laggedPosition;
        if (laggedPos == null) {
            laggedPos = new Vec3(mc.thePlayer.posX, mc.thePlayer.posY, mc.thePlayer.posZ);
        }

        RenderManager rm = mc.getRenderManager();
        double x = laggedPos.xCoord - rm.viewerPosX;
        double y = laggedPos.yCoord - rm.viewerPosY;
        double z = laggedPos.zCoord - rm.viewerPosZ;

        double w = mc.thePlayer.width / 2.0 + 0.05;
        double h = mc.thePlayer.height + 0.1;

        // Color coding: Cyan for Grim, Orange for Polar, Magenta for Matrix, Red for Generic
        int color = 0x00E5FF; // Default Cyan
        switch (mode.getValue()) {
            case POLAR: color = 0xFF9100; break;
            case MATRIX: color = 0xE040FB; break;
            case GENERIC: color = 0xFF1744; break;
            case GRIM: default: color = 0x00E5FF; break;
        }

        float r = ((color >> 16) & 255) / 255.0f;
        float g = ((color >> 8) & 255) / 255.0f;
        float b = (color & 255) / 255.0f;
        float a = isLagging ? 0.45f : 0.15f;

        AxisAlignedBB bb = new AxisAlignedBB(x - w, y, z - w, x + w, y + h, z + w);

        GlStateManager.pushMatrix();
        GlStateManager.disableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, 1, 0);
        GlStateManager.disableDepth();
        GlStateManager.depthMask(false);

        GlStateManager.color(r, g, b, a);
        net.minecraft.client.renderer.Tessellator tessellator = net.minecraft.client.renderer.Tessellator.getInstance();
        net.minecraft.client.renderer.WorldRenderer worldrenderer = tessellator.getWorldRenderer();
        worldrenderer.begin(GL11.GL_QUADS, net.minecraft.client.renderer.vertex.DefaultVertexFormats.POSITION);
        worldrenderer.pos(bb.minX, bb.minY, bb.minZ).endVertex();
        worldrenderer.pos(bb.maxX, bb.minY, bb.minZ).endVertex();
        worldrenderer.pos(bb.maxX, bb.minY, bb.maxZ).endVertex();
        worldrenderer.pos(bb.minX, bb.minY, bb.maxZ).endVertex();
        worldrenderer.pos(bb.minX, bb.maxY, bb.minZ).endVertex();
        worldrenderer.pos(bb.minX, bb.maxY, bb.maxZ).endVertex();
        worldrenderer.pos(bb.maxX, bb.maxY, bb.maxZ).endVertex();
        worldrenderer.pos(bb.maxX, bb.maxY, bb.minZ).endVertex();
        worldrenderer.pos(bb.minX, bb.minY, bb.minZ).endVertex();
        worldrenderer.pos(bb.minX, bb.maxY, bb.minZ).endVertex();
        worldrenderer.pos(bb.maxX, bb.maxY, bb.minZ).endVertex();
        worldrenderer.pos(bb.maxX, bb.minY, bb.minZ).endVertex();
        worldrenderer.pos(bb.maxX, bb.minY, bb.maxZ).endVertex();
        worldrenderer.pos(bb.maxX, bb.maxY, bb.maxZ).endVertex();
        worldrenderer.pos(bb.minX, bb.maxY, bb.maxZ).endVertex();
        worldrenderer.pos(bb.minX, bb.minY, bb.maxZ).endVertex();
        tessellator.draw();

        GlStateManager.enableDepth();
        GlStateManager.depthMask(true);
        GlStateManager.enableTexture2D();
        GlStateManager.disableBlend();
        GlStateManager.popMatrix();
    }

    @Override
    public String getHudInfo() {
        return mode.getValue().toString();
    }

    private EntityPlayer findTarget(Minecraft mc) {
        List<EntityPlayer> validTargets = new ArrayList<>();
        double rangeSq = range.getValue() * range.getValue();

        for (Object o : mc.theWorld.loadedEntityList) {
            if (!(o instanceof EntityPlayer)) continue;
            EntityPlayer player = (EntityPlayer) o;
            if (player == mc.thePlayer) continue;
            if (player.isDead) continue;
            if (player.getHealth() <= 0) continue;

            double distSq = mc.thePlayer.getDistanceSqToEntity(player);
            if (distSq <= rangeSq) {
                validTargets.add(player);
            }
        }

        if (validTargets.isEmpty()) return null;

        validTargets.sort(Comparator.comparingDouble(p -> mc.thePlayer.getDistanceToEntity(p)));
        return validTargets.get(0);
    }

    private boolean shouldLag(Minecraft mc) {
        if (target == null) return false;
        if (!target.isEntityAlive()) return false;

        if (holdingWeapon.isEnabled() && !isHoldingWeapon(mc)) {
            return false;
        }

        if (mc.thePlayer.isUsingItem()) {
            if (useSplashPotion.isEnabled()) {
                return false;
            }
        }

        return true;
    }

    private boolean isHoldingWeapon(Minecraft mc) {
        if (mc.thePlayer == null || mc.thePlayer.getHeldItem() == null) return false;
        return mc.thePlayer.getHeldItem().getItem() instanceof net.minecraft.item.ItemSword;
    }

    private boolean shouldFlush() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.thePlayer == null) return true;

        if (mc.currentScreen != null) return true;

        if (mc.thePlayer.isUsingItem() && useSplashPotion.isEnabled()) {
            return true;
        }

        if (sprintReset.isEnabled()) {
            boolean wasSprinting = mc.thePlayer.isSprinting();
            if (wasSprinting && System.currentTimeMillis() - lastSprintResetTime < 180) {
                return true;
            }
        }

        if (mc.thePlayer.hurtTime > 0) return true;

        return false;
    }

    private void startLag() {
        isLagging = true;
        lagStartTimeMs = System.currentTimeMillis();
        chokedTicks = 0;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer != null) {
            laggedPosition = new Vec3(mc.thePlayer.posX, mc.thePlayer.posY, mc.thePlayer.posZ);
        }
    }

    private void flushLag() {
        if (!isLagging) return;

        Minecraft mc = Minecraft.getMinecraft();
        if (mc != null && mc.getNetHandler() != null) {
            com.lionclient.LionClient client = com.lionclient.LionClient.getInstance();
            if (client != null && client.getPacketStallHandler() != null) {
                client.getPacketStallHandler().flush();
            }
        }

        isLagging = false;
        lagStartTimeMs = 0;
        chokedTicks = 0;
        target = null;
        laggedPosition = null;
    }
}