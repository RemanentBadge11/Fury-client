package com.lionclient.feature.module.impl;

import com.lionclient.feature.module.Category;
import com.lionclient.feature.module.Module;
import com.lionclient.feature.setting.BooleanSetting;
import com.lionclient.feature.setting.DecimalSetting;
import com.lionclient.feature.setting.EnumSetting;
import com.lionclient.feature.setting.NumberSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.network.play.client.C02PacketUseEntity;
import net.minecraft.network.play.client.C0APacketAnimation;
import net.minecraft.network.play.client.C0BPacketEntityAction;
import net.minecraft.network.play.server.S12PacketEntityVelocity;
import net.minecraft.network.play.server.S27PacketExplosion;
import net.minecraft.util.MathHelper;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.input.Keyboard;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Random;

/**
 * Advanced Velocity Module — Combines Vape's KnockbackDelay & JumpReset modes,
 * LiquidBounce's GrimFull bypass, and zero-allocation performance math.
 */
public final class VelocityModule extends Module {

    public enum Mode {
        Simple("Simple"),
        KnockbackDelay("KnockbackDelay"),
        JumpReset("JumpReset"),
        GrimFull("GrimFull"),
        Reduce("Reduce"),
        Ignore("Ignore"),
        Matrix("Matrix"),
        Vulcan("Vulcan"),
        Hypixel("Hypixel");

        private final String name;
        Mode(String name) { this.name = name; }
        @Override public String toString() { return name; }
    }

    public enum KiteMode {
        OFF("Off"),
        HIT_BEHIND("Hit Behind"),
        ALWAYS("Always");

        private final String name;
        KiteMode(String name) { this.name = name; }
        @Override public String toString() { return name; }
    }

    // ======================== SETTINGS ========================
    private final EnumSetting<Mode> mode = new EnumSetting<>("Mode", Mode.values(), Mode.Simple);
    private final DecimalSetting horizontal = new DecimalSetting("Horizontal", 0.0D, 1.0D, 0.05D, 0.0D);
    private final DecimalSetting vertical = new DecimalSetting("Vertical", 0.0D, 1.0D, 0.05D, 0.0D);
    private final NumberSetting chance = new NumberSetting("Chance", 0, 100, 5, 100);
    
    // KnockbackDelay (Vape Packet Mode) Settings
    private final NumberSetting airDelay = new NumberSetting("Air Delay", 0, 500, 25, 100);
    private final NumberSetting groundDelay = new NumberSetting("Ground Delay", 0, 500, 25, 250);
    
    // JumpReset (Vape Receive Mode) Settings
    private final NumberSetting jumpAccuracy = new NumberSetting("Jump Accuracy", 0, 100, 5, 100);
    
    // Vape Kite Mode Settings
    private final EnumSetting<KiteMode> kiteMode = new EnumSetting<>("Kite Mode", KiteMode.values(), KiteMode.OFF);
    private final DecimalSetting kiteHorizontal = new DecimalSetting("Kite Horizontal", 1.0D, 2.0D, 0.05D, 1.3D);
    private final DecimalSetting kiteVertical = new DecimalSetting("Kite Vertical", 1.0D, 2.0D, 0.05D, 1.0D);

    // Filter Settings
    private final NumberSetting fovAngle = new NumberSetting("FOV Angle", 0, 360, 15, 360);
    private final BooleanSetting waterCheck = new BooleanSetting("Water Check", true);
    private final BooleanSetting onlyMoving = new BooleanSetting("Only Moving", false);
    private final BooleanSetting onlyTargeting = new BooleanSetting("Only Targeting", false);

    // ======================== INTERNAL STATE ========================
    private final Random random = new Random();
    private final Deque<DelayedVelocity> heldPackets = new ArrayDeque<>(16);

    private long releaseTime = 0L;
    private int consecutiveHits = 0;
    private boolean waitingForJumpReset = false;
    private boolean jumpingState = false;
    private double expectedMotionX = 0.0;
    private double expectedMotionY = 0.0;
    private double expectedMotionZ = 0.0;

    private static final class DelayedVelocity {
        final double motionX;
        final double motionY;
        final double motionZ;
        final long scheduledReleaseTime;

        DelayedVelocity(double x, double y, double z, long release) {
            this.motionX = x;
            this.motionY = y;
            this.motionZ = z;
            this.scheduledReleaseTime = release;
        }
    }

    public VelocityModule() {
        super("Velocity", "Reduces or modifies knockback taken when hit.", Category.COMBAT, Keyboard.KEY_NONE);
        addSetting(mode);
        addSetting(horizontal);
        addSetting(vertical);
        addSetting(chance);
        addSetting(airDelay);
        addSetting(groundDelay);
        addSetting(jumpAccuracy);
        addSetting(kiteMode);
        addSetting(kiteHorizontal);
        addSetting(kiteVertical);
        addSetting(fovAngle);
        addSetting(waterCheck);
        addSetting(onlyMoving);
        addSetting(onlyTargeting);
    }

    @Override
    protected void onEnable() {
        resetState();
    }

    @Override
    protected void onDisable() {
        resetState();
    }

    public String getSuffix() {
        if (!heldPackets.isEmpty()) {
            return "Holding";
        }
        return mode.getValue().toString();
    }

    @Override
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START || !isEnabled()) return;

        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null || mc.theWorld == null) {
            resetState();
            return;
        }

        // 1. Water / Liquid / Cobweb Check
        if (waterCheck.isEnabled() && isInLiquidOrWeb(mc)) {
            flushHeldPackets(mc, true);
            return;
        }

        // 2. Vape KnockbackDelay (Packet Queue Flush Check)
        if (!heldPackets.isEmpty()) {
            long now = System.currentTimeMillis();
            if (now >= releaseTime) {
                flushHeldPackets(mc, false);
            }
        }

        // 3. Vape JumpReset Mode Mechanics
        if (mode.getValue() == Mode.JumpReset) {
            handleJumpResetTick(mc);
        }

        // Track consecutive hits for delay calculation
        if (mc.thePlayer.hurtTime == 9) {
            consecutiveHits++;
        } else if (mc.thePlayer.hurtTime == 0) {
            consecutiveHits = 0;
        }
    }

    /**
     * Called when an S12PacketEntityVelocity is received.
     * Returns true to cancel vanilla velocity application.
     */
    public boolean onVelocityPacket(S12PacketEntityVelocity packet) {
        if (!isEnabled()) return false;

        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null || packet.getEntityID() != mc.thePlayer.getEntityId()) return false;

        if (waterCheck.isEnabled() && isInLiquidOrWeb(mc)) return false;

        double rawX = packet.getMotionX() / 8000.0D;
        double rawY = packet.getMotionY() / 8000.0D;
        double rawZ = packet.getMotionZ() / 8000.0D;

        // Skip zero or downward velocity
        if (rawX == 0.0D && rawZ == 0.0D && rawY <= 0.0D) return false;

        if (!shouldActivateConditions(mc, rawX, rawZ)) return false;

        // --- Kite Mode Check (Boosts knockback when hit from behind) ---
        boolean isHitFromBehind = isHitFromBehind(mc, rawX, rawZ);
        if (kiteMode.getValue() == KiteMode.ALWAYS || (kiteMode.getValue() == KiteMode.HIT_BEHIND && isHitFromBehind)) {
            mc.thePlayer.motionX = rawX * kiteHorizontal.getValue();
            mc.thePlayer.motionY = rawY * kiteVertical.getValue();
            mc.thePlayer.motionZ = rawZ * kiteHorizontal.getValue();
            return true;
        }

        // --- Mode Execution Pipeline ---
        switch (mode.getValue()) {
            case Simple:
            case Ignore:
                double horizScale = horizontal.getValue();
                double vertScale = vertical.getValue();
                if (horizScale == 0.0D && vertScale == 0.0D) {
                    return true; // Completely cancel knockback
                }
                mc.thePlayer.motionX = rawX * horizScale;
                mc.thePlayer.motionY = rawY * vertScale;
                mc.thePlayer.motionZ = rawZ * horizScale;
                return true;

            case KnockbackDelay: // Vape VelocityPacketMode
                long delay = mc.thePlayer.onGround ? (long) groundDelay.getValue() : (long) airDelay.getValue();
                if (delay > 0) {
                    long targetRelease = System.currentTimeMillis() + delay;
                    this.releaseTime = targetRelease;
                    heldPackets.add(new DelayedVelocity(
                        rawX * horizontal.getValue(),
                        rawY * vertical.getValue(),
                        rawZ * horizontal.getValue(),
                        targetRelease
                    ));
                    return true;
                }
                mc.thePlayer.motionX = rawX * horizontal.getValue();
                mc.thePlayer.motionY = rawY * vertical.getValue();
                mc.thePlayer.motionZ = rawZ * horizontal.getValue();
                return true;

            case JumpReset: // Vape VelocityPacketReceiveMode
                if (rollChance((double) jumpAccuracy.getValue())) {
                    expectedMotionX = rawX;
                    expectedMotionY = rawY;
                    expectedMotionZ = rawZ;
                    waitingForJumpReset = true;
                }
                return false; // Allow vanilla velocity so jump reset resets momentum

            case GrimFull: // LiquidBounce Grim Combat Bypass
                Entity target = getTargetEntity(mc);
                if (target != null) {
                    boolean sprinting = mc.thePlayer.isSprinting();
                    if (!sprinting) {
                        mc.thePlayer.sendQueue.addToSendQueue(new C0BPacketEntityAction(mc.thePlayer, C0BPacketEntityAction.Action.START_SPRINTING));
                    }
                    mc.thePlayer.sendQueue.addToSendQueue(new C02PacketUseEntity(target, C02PacketUseEntity.Action.ATTACK));
                    mc.thePlayer.sendQueue.addToSendQueue(new C0APacketAnimation());
                    if (!sprinting) {
                        mc.thePlayer.sendQueue.addToSendQueue(new C0BPacketEntityAction(mc.thePlayer, C0BPacketEntityAction.Action.STOP_SPRINTING));
                    }
                    return true;
                }
                break;

            case Reduce:
                mc.thePlayer.motionX = rawX * 0.6D;
                mc.thePlayer.motionY = rawY * 0.8D;
                mc.thePlayer.motionZ = rawZ * 0.6D;
                return true;

            case Matrix:
                if (mc.thePlayer.isSprinting()) {
                    mc.thePlayer.motionX *= 0.6D;
                    mc.thePlayer.motionZ *= 0.6D;
                }
                return true;

            case Vulcan:
            case Hypixel:
                mc.thePlayer.motionX = rawX * horizontal.getValue();
                mc.thePlayer.motionY = rawY * vertical.getValue();
                mc.thePlayer.motionZ = rawZ * horizontal.getValue();
                return true;
        }

        return false;
    }

    public boolean onExplosionPacket(S27PacketExplosion packet) {
        if (!isEnabled()) return false;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null) return false;

        if (horizontal.getValue() == 0.0D && vertical.getValue() == 0.0D) {
            return true;
        }
        return false;
    }

    private void handleJumpResetTick(Minecraft mc) {
        if (waitingForJumpReset && mc.thePlayer.onGround) {
            if (rollChance((double) jumpAccuracy.getValue())) {
                mc.thePlayer.jump();
                jumpingState = true;
            }
            waitingForJumpReset = false;
        } else if (jumpingState && !mc.thePlayer.onGround) {
            jumpingState = false;
        }
    }

    private void flushHeldPackets(Minecraft mc, boolean discard) {
        if (heldPackets.isEmpty()) return;

        if (!discard && mc.thePlayer != null) {
            while (!heldPackets.isEmpty()) {
                DelayedVelocity dv = heldPackets.poll();
                mc.thePlayer.motionX = dv.motionX;
                mc.thePlayer.motionY = dv.motionY;
                mc.thePlayer.motionZ = dv.motionZ;
            }
        } else {
            heldPackets.clear();
        }
        releaseTime = 0L;
    }

    private boolean shouldActivateConditions(Minecraft mc, double motionX, double motionZ) {
        if (!rollChance((double) chance.getValue())) {
            return false;
        }

        if (onlyMoving.isEnabled() && mc.thePlayer.movementInput != null) {
            if (mc.thePlayer.movementInput.moveForward == 0.0F && mc.thePlayer.movementInput.moveStrafe == 0.0F) {
                return false;
            }
        }

        if (fovAngle.getValue() < 360 && (motionX != 0 || motionZ != 0)) {
            float packetYaw = (float) (Math.toDegrees(Math.atan2(-motionZ, -motionX)) - 90.0D);
            float diff = Math.abs(MathHelper.wrapAngleTo180_float(packetYaw - mc.thePlayer.rotationYaw));
            if (diff > fovAngle.getValue() / 2.0F) {
                return false;
            }
        }

        if (onlyTargeting.isEnabled()) {
            Entity target = getTargetEntity(mc);
            if (target == null) return false;
        }

        return true;
    }

    private boolean isHitFromBehind(Minecraft mc, double motionX, double motionZ) {
        if (motionX == 0 && motionZ == 0) return false;
        float packetYaw = (float) (Math.toDegrees(Math.atan2(-motionZ, -motionX)) - 90.0D);
        float diff = Math.abs(MathHelper.wrapAngleTo180_float(packetYaw - mc.thePlayer.rotationYaw));
        return diff < 45.0F;
    }

    private Entity getTargetEntity(Minecraft mc) {
        if (mc.objectMouseOver != null && mc.objectMouseOver.entityHit instanceof EntityLivingBase) {
            return mc.objectMouseOver.entityHit;
        }
        try {
            com.lionclient.LionClient client = com.lionclient.LionClient.getInstance();
            if (client != null && client.getModuleManager() != null) {
                KillAuraModule aura = client.getModuleManager().getModule(KillAuraModule.class);
                if (aura != null && aura.isEnabled()) return aura.getTarget();

                SilentAuraModule silentAura = client.getModuleManager().getModule(SilentAuraModule.class);
                if (silentAura != null && silentAura.isEnabled()) return silentAura.getTarget();
            }
        } catch (Throwable ignored) {}
        return null;
    }

    private boolean rollChance(double targetChance) {
        if (targetChance >= 100.0D) return true;
        if (targetChance <= 0.0D) return false;
        return random.nextDouble() * 100.0D <= targetChance;
    }

    private boolean isInLiquidOrWeb(Minecraft mc) {
        return mc.thePlayer.isInWater() || mc.thePlayer.isInLava();
    }

    private void resetState() {
        heldPackets.clear();
        releaseTime = 0L;
        consecutiveHits = 0;
        waitingForJumpReset = false;
        jumpingState = false;
        expectedMotionX = 0.0;
        expectedMotionY = 0.0;
        expectedMotionZ = 0.0;
    }
}
