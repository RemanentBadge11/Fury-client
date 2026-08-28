package com.lionclient.feature.module.impl;

import com.lionclient.feature.module.Category;
import com.lionclient.feature.module.Module;
import com.lionclient.feature.setting.BooleanSetting;
import com.lionclient.feature.setting.DecimalSetting;
import com.lionclient.feature.setting.EnumSetting;
import com.lionclient.feature.setting.NumberSetting;
import com.lionclient.util.TargetFilter;
import lion.client.ClientLogger;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemAxe;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemSword;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MathHelper;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.input.Mouse;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

/**
 * AimAssist — Full port of Vape V4.21 architecture.
 *
 * Two modes:
 *   Simple   — Velocity-buffer force system with drift offsets
 *              (Vape AimAssistRotationSubModule)
 *   Adaptive — PID control loop with lead prediction, snap targeting,
 *              multi-layer noise, pitch drift spring
 *              (Vape AimAssistTargetingSubModule)
 *
 * Mouse-unit injection: All rotations computed as integer mouse cursor
 * steps through the vanilla sensitivity formula (f^3 * 8 * 0.15),
 * producing inherently GCD-compliant deltas.
 */
public class AimAssistModule extends Module {

    // ======================== Enums ========================

    public enum AimMode {
        SIMPLE("Simple"), ADAPTIVE("Adaptive");
        private final String name;
        AimMode(String n) { this.name = n; }
        @Override public String toString() { return name; }
    }

    public enum TargetPriority {
        YAW("Yaw"), DISTANCE("Distance"), HEALTH("Health");
        private final String name;
        TargetPriority(String n) { this.name = n; }
        @Override public String toString() { return name; }
    }

    public enum AreaMode {
        CENTER("Center"), CLOSEST("Closest");
        private final String name;
        AreaMode(String n) { this.name = n; }
        @Override public String toString() { return name; }
    }

    // ======================== Settings ========================

    private final EnumSetting<AimMode> mode =
            new EnumSetting<>("Mode", AimMode.values(), AimMode.ADAPTIVE);
    private final EnumSetting<TargetPriority> targetPriority =
            new EnumSetting<>("Target Priority", TargetPriority.values(), TargetPriority.YAW);
    private final EnumSetting<AreaMode> targetArea =
            new EnumSetting<>("Target Area", AreaMode.values(), AreaMode.CENTER);

    private final DecimalSetting horizontalSpeed =
            new DecimalSetting("Horizontal Speed", 1.0, 10.0, 0.1, 5.0);
    private final DecimalSetting verticalSpeed =
            new DecimalSetting("Vertical Speed", 1.0, 10.0, 0.1, 5.0);
    private final NumberSetting maxAngle =
            new NumberSetting("Max Angle", 1, 360, 1, 180);
    private final DecimalSetting distance =
            new DecimalSetting("Distance", 1.0, 8.0, 0.1, 5.0);

    private final BooleanSetting clickAim =
            new BooleanSetting("On Click", true);
    private final BooleanSetting aimVertically =
            new BooleanSetting("Aim Vertically", false);
    private final BooleanSetting strafeIncrease =
            new BooleanSetting("Strafe Increase", false);
    private final BooleanSetting weaponOnly =
            new BooleanSetting("Weapon Only", false);
    private final BooleanSetting singleplayerOnly =
            new BooleanSetting("Singleplayer Only", false);

    // ======================== Shared State ========================

    public static EntityLivingBase target = null;
    private final Random random = new Random();
    private final Random sharedRandom = new Random();

    // ======================== Simple Mode State ========================

    private float sHorizVel, sHorizVelBuf;
    private float sVertVel, sVertVelBuf;
    private float sHorizMouseAccum, sVertMouseAccum;
    private int sSwapCounter;
    private int sDriftX, sDriftY;
    private int sRandOffsetX, sRandOffsetY;
    private double sDriftTimer;
    private boolean sPrevOnLeft, sPrevAbove;
    private double sLastAngleDiff;
    private boolean multiplayerGuardWarned;
    private int sSampleCounter;
    private float sHorizBoost, sVertBoost;
    private double sPrevTargetX, sPrevTargetZ;
    private boolean sPrevTargetInitialized;
    private EntityLivingBase sTrackedTarget;
    private int sRetargetCounter;

    // ======================== Adaptive Mode State ========================

    private boolean aInit;
    private long aLastFrameNanos;
    private boolean aAimPtInit;
    private double aAimX, aAimY, aAimZ;
    private double aLeadX, aLeadY, aLeadZ;
    private boolean aPredInit;
    private double aPredX, aPredY, aPredZ;
    private float aYawBias, aPitchBias;
    private float aYawVel, aPitchVel;
    private float aYawAccel, aPitchAccel;
    private float aLastYawDiff, aLastPitchDiff;
    private float aLastTargetYaw, aLastTargetPitch;
    private float aLastPlayerYaw, aLastPlayerPitch;
    private float aAimStrength;
    private boolean aYawSnapped, aPitchSnapped;
    private float aOvershoot;
    private float aYawFlickTicks, aPitchFlickTicks;
    private float aLastYawSign, aLastPitchSign;
    private float aAirFactor, aVertVelocity;
    private double aLastEyeY, aLastGroundEyeY;
    private float aDriftPos, aDriftVel, aDriftTarget, aDriftNoise;
    private long aDriftNextNanos, aNoiseStartNanos;
    private int aTargetSwitchTicks;
    private float aPendingYaw, aPendingPitch;
    private float aLastAppliedYawDelta, aLastAppliedPitchDelta;
    private int aPlayerOpposeTicks;
    private float sLastAppliedYaw;
    
    // Matrix Bypass State
    private final float[] aHistYawVel = new float[10];
    private final float[] aHistPitchVel = new float[10];
    private int aHistIdx;
    private float aKineNoiseY, aKineNoiseP;
    private float aKineTargetY, aKineTargetP;
    private long aLastKineChange;

    private static final float SNAP_ENTER = 1.5f;
    private static final float SNAP_EXIT = 7.0f;
    private static final float SNAP_MAX_ANGLE = 20.0f;

    // ======================== Constructor ========================

    public AimAssistModule() {
        super("AimAssist",
              "Vape V4 architecture AimAssist with mouse-unit injection.",
              Category.COMBAT, 0);

        addSetting(mode);
        addSetting(targetPriority);
        addSetting(targetArea);
        addSetting(horizontalSpeed);
        addSetting(verticalSpeed);
        addSetting(maxAngle);
        addSetting(distance);
        addSetting(clickAim);
        addSetting(aimVertically);
        addSetting(strafeIncrease);
        addSetting(weaponOnly);
        addSetting(singleplayerOnly);
    }

    // ======================== Enable / Disable ========================

    @Override
    protected void onEnable() {
        target = null;
        multiplayerGuardWarned = false;
        resetSimple();
        resetAdaptive();
    }

    @Override
    protected void onDisable() {
        target = null;
    }

    // ======================== Client Tick — Computation ========================

    @Override
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null || mc.theWorld == null) return;
        if (mc.currentScreen != null) { resetAll(); return; }

        if (!canAim(mc)) { resetAll(); return; }

        // Validate current target
        if (target != null) {
            float effectiveMaxAngle = (float)(maxAngle.getValue() / 2);
            if (target.isDead || target.getHealth() <= 0
                    || mc.thePlayer.getDistanceToEntity(target) > distance.getValue()
                    || yawAngleTo(mc.thePlayer, target) > effectiveMaxAngle) {
                target = null;
                if (mode.getValue() == AimMode.SIMPLE) resetSimple();
                else resetAdaptive();
            }
        }

        // Find / update target
        updateTarget(mc);
        if (target == null) return;

        // Dispatch to engine.
        // Adaptive computes on the *client tick* (deterministic 1:1 cadence with
        // System.nanoTime deltas) and accumulates pending mouse deltas; the render
        // tick only *applies* them (sub-frame). Computing adaptive on render tick
        // instead broke the dt/aim-strength math and left pending deltas at zero.
        if (mode.getValue() == AimMode.SIMPLE) {
            simpleModeTick(mc);
        } else if (mode.getValue() == AimMode.ADAPTIVE) {
            adaptiveModeTick(mc);
        }
    }

    // ======================== Render Tick — Apply Mouse Deltas ========================

    @Override
    public void onRenderTick(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        if (target == null) return;

        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null || mc.theWorld == null) return;

        // Render-tick only *applies* pending adaptive deltas (sub-frame smoothness).
        // Compute happens on the client tick (1:1 cadence); rendering here must not
        // recompute, or deltas get doubled and the dt/aim-strength math breaks.
        if (mode.getValue() == AimMode.ADAPTIVE) {
            applyAdaptivePending(mc);
        }
        // Simple mode applies directly in tick (no sub-frame accumulation needed)
    }

    // ======================== Conditions ========================

    private boolean canAim(Minecraft mc) {
        EntityPlayerSP p = mc.thePlayer;
        if (p == null) return false;

        if (singleplayerOnly.isEnabled() && !mc.isSingleplayer()) {
            if (!multiplayerGuardWarned) {
                String msg = "[AimAssist] Multiplayer guard blocked rotation assistance. "
                        + "Use single-player worlds for integrity-audit rotation replay.";
                System.out.println(msg);
                ClientLogger.warn(msg);
                multiplayerGuardWarned = true;
            }
            return false;
        }

        if (clickAim.isEnabled() && !Mouse.isButtonDown(0)) return false;
        if (weaponOnly.isEnabled() && !isHoldingWeapon(p)) return false;
        return true;
    }

    private boolean isHoldingWeapon(EntityPlayerSP p) {
        ItemStack held = p.getHeldItem();
        if (held == null) return false;
        return held.getItem() instanceof ItemSword
            || held.getItem() instanceof ItemAxe;
    }

    // ======================== Target Selection ========================

    private void updateTarget(Minecraft mc) {
        EntityLivingBase candidate = findBestTarget(mc);

        if (clickAim.isEnabled()) {
            if (Mouse.isButtonDown(0) && target == null) {
                if (mode.getValue() == AimMode.ADAPTIVE && candidate != null) resetAdaptive();
                target = candidate;
            }
        } else {
            if (mode.getValue() == AimMode.SIMPLE) {
                sRetargetCounter++;
                if (sRetargetCounter > 700 || target == null
                        || !isValidTarget(mc, target)) {
                    target = candidate;
                    sRetargetCounter = 0;
                }
            } else {
                aTargetSwitchTicks++;
                if (aTargetSwitchTicks > 700 || target == null
                        || !isValidTarget(mc, target)) {
                    if (target == null || (candidate != null && !target.equals(candidate)))
                        resetAdaptive();
                    target = candidate;
                    aTargetSwitchTicks = 0;
                }
            }
        }
    }

    private EntityLivingBase findBestTarget(Minecraft mc) {
        List<EntityLivingBase> targets = new ArrayList<EntityLivingBase>();
        for (Entity e : mc.theWorld.loadedEntityList) {
            if (!(e instanceof EntityLivingBase)) continue;
            EntityLivingBase living = (EntityLivingBase) e;
            if (!isValidTarget(mc, living)) continue;
            targets.add(living);
        }
        if (targets.isEmpty()) return null;

        final EntityPlayerSP player = mc.thePlayer;
        switch (targetPriority.getValue()) {
            case YAW:
                Collections.sort(targets, new Comparator<EntityLivingBase>() {
                    @Override public int compare(EntityLivingBase a, EntityLivingBase b) {
                        return Float.compare(yawAngleTo(player, a), yawAngleTo(player, b));
                    }
                });
                break;
            case DISTANCE:
                Collections.sort(targets, new Comparator<EntityLivingBase>() {
                    @Override public int compare(EntityLivingBase a, EntityLivingBase b) {
                        return Float.compare(player.getDistanceToEntity(a),
                                             player.getDistanceToEntity(b));
                    }
                });
                break;
            case HEALTH:
                Collections.sort(targets, new Comparator<EntityLivingBase>() {
                    @Override public int compare(EntityLivingBase a, EntityLivingBase b) {
                        return Float.compare(a.getHealth(), b.getHealth());
                    }
                });
                break;
        }
        return targets.get(0);
    }

    private boolean isValidTarget(Minecraft mc, EntityLivingBase e) {
        if (e == mc.thePlayer) return false;
        if (e.isDead || e.getHealth() <= 0) return false;
        if (mc.thePlayer.getDistanceToEntity(e) > distance.getValue()) return false;
        if (yawAngleTo(mc.thePlayer, e) > maxAngle.getValue() / 2) return false;
        if (e == mc.thePlayer.ridingEntity) return false;
        return TargetFilter.isValidTarget(e);
    }

    // ================================================================
    //  SIMPLE MODE — Velocity-buffer force system with drift
    //  (Vape AimAssistRotationSubModule)
    // ================================================================

    private void simpleModeTick(Minecraft mc) {
        EntityPlayerSP player = mc.thePlayer;

        // Reset prediction state when target changes
        if (target != sTrackedTarget) {
            sTrackedTarget = target;
            sPrevTargetInitialized = false;
        }

        // Measure player's genuine mouse input since last frame
        float playerMouseYaw = Float.isNaN(sLastAppliedYaw) ? 0 
            : wrap180(player.rotationYaw - sLastAppliedYaw);

        // Velocity buffer swap — every 10 ticks
        sSwapCounter++;
        if (sSwapCounter > 10) {
            sVertVelBuf = sVertVel;
            sHorizVel   = sHorizVelBuf;
            sHorizVelBuf = 0;
            sVertVel     = 0;
            sSwapCounter = 0;
        }

        updateSimpleDrift();

        // Resolve target position
        double targetX, targetZ;
        if (targetArea.getValue() == AreaMode.CLOSEST) {
            double eyeX = player.posX;
            double eyeZ = player.posZ;
            AxisAlignedBB bb = target.getEntityBoundingBox();
            targetX = clamp(eyeX, bb.minX, bb.maxX);
            targetZ = clamp(eyeZ, bb.minZ, bb.maxZ);
            // Motion interpolation
            double mx = target.posX - target.prevPosX;
            double mz = target.posZ - target.prevPosZ;
            double prevCX = targetX - mx;
            double prevCZ = targetZ - mz;
            targetX = prevCX + (targetX - prevCX) * 0.5;
            targetZ = prevCZ + (targetZ - prevCZ) * 0.5;
        } else {
            targetX = target.posX;
            targetZ = target.posZ;
        }
        // Aim at body center (65% height) for consistent vertical tracking
        AxisAlignedBB targetBB = target.getEntityBoundingBox();
        double targetY = targetBB.minY + (targetBB.maxY - targetBB.minY) * 0.65;

        // Target motion & prediction (factor 1.7 from Vape)
        // Initialize previous position on first tick after target change
        if (!sPrevTargetInitialized) {
            sPrevTargetX = targetX;
            sPrevTargetZ = targetZ;
            sPrevTargetInitialized = true;
        }
        double motX = targetX - sPrevTargetX;
        double motZ = targetZ - sPrevTargetZ;
        sPrevTargetX = targetX;
        sPrevTargetZ = targetZ;
        double predX = targetX + motX * 1.7;
        double predZ = targetZ + motZ * 1.7;

        // Angle calculations
        float viewYaw = player.rotationYaw;
        double horizAngleDiff = horizAngleDiff(player.posX, player.posZ,
                                               viewYaw, predX, predZ);
        boolean onLeft = isOnLeft(player.posX, player.posZ, viewYaw, predX, predZ);
        int vertAngleDiff = vertAngleDiff(player, targetX, targetY, targetZ);
        boolean above = vertAngleDiff < 0;
        int vertDead = Math.abs(vertAngleDiff) - 3;

        // Forces
        float hForce = 1.0f + (float) randRange(0, 2)
                + (float)(horizAngleDiff / 50.0);
        float vForce = 1.0f + (float) randRange(0, 2)
                + (float) Math.abs(vertDead) / 50.0f;

        if (Math.abs(horizAngleDiff - sLastAngleDiff) > 6.0)
            hForce += (float)(horizAngleDiff / 35.0);

        // Proximity boost
        double proxBoost = Math.max(0, (9.0f - player.getDistanceToEntity(target))
                                       / 2.5f - 2.0f);
        hForce += (float) proxBoost;

        // Strafe increase
        float strafe = player.moveStrafing;
        boolean strafingAway = onLeft ? strafe < 0 : strafe > 0;
        if (strafeIncrease.isEnabled() && strafingAway) hForce *= 1.6f;

        // Close range damping
        if (player.getDistanceToEntity(target) < 0.5f) hForce /= 5.0f;

        // Acceleration
        float hAccel = hForce / 90.0f * (onLeft ? -1.0f : 1.0f);
        // Symmetric vertical acceleration (was asymmetric: upward 90x stronger than downward)
        float vAccel = vForce / 90.0f * (above ? 1.0f : -1.0f);

        // Deadzone
        if (horizAngleDiff < 5.0) {
            hAccel = 0;
            sHorizVel *= 0.7f;
            boolean strafingToward = onLeft ? strafe > 0 : strafe < 0;
            if (strafingToward) sHorizVel *= 0.5f;
        }

        // Direction reversal
        if (onLeft != sPrevOnLeft) {
            sHorizVel = -sHorizVel;
            sHorizVelBuf = -sHorizVelBuf;
            sHorizMouseAccum = 0;
        }
        if (above != sPrevAbove) {
            sVertVelBuf = -sVertVelBuf;
            sVertVel = -sVertVel;
            sVertMouseAccum = 0;
        }
        if (vertDead < 5) { vAccel = 0; sVertVelBuf *= 0.7f; }

        // Accumulate (clamp buffers to prevent explosion across swap boundary)
        sHorizVelBuf += hAccel;
        sHorizVelBuf = clampF(sHorizVelBuf, -8.0f, 8.0f);
        sVertVel += vAccel;
        sVertVel = clampF(sVertVel, -8.0f, 8.0f);

        float smoothH = sHorizVel;
        float smoothV = sVertVelBuf;
        if (Math.abs(smoothH) > 8.0f) {
            sHorizVelBuf = 0; sHorizVel = 0; return;
        }

        // Adjustment
        float hAdj = smoothH * 0.15f;
        if (horizAngleDiff <= 9.0) {
            hAdj = (float)(hAdj / (10.0 - horizAngleDiff));
        }
        if (Float.isNaN(hAdj)) { sHorizVelBuf = 0; sHorizVel = 0; return; }

        // Queue horizontal
        queueSimpleH(hAdj, (float) horizAngleDiff);

        // Queue vertical
        if (aimVertically.isEnabled()) {
            float vAdj = smoothV * 0.15f;
            if (!Float.isNaN(vAdj))
                queueSimpleV(vAdj, Math.abs(vertAngleDiff));
        }

        sPrevAbove = above;
        sPrevOnLeft = onLeft;
        sSampleCounter++;
        if (sSampleCounter > 10) { sLastAngleDiff = horizAngleDiff; sSampleCounter = 0; }

        // Apply drift to accumulators
        sHorizMouseAccum += sDriftX;
        if (aimVertically.isEnabled()) sVertMouseAccum += sDriftY;

        // Respect player mouse: reduce assist when player actively pulls away
        // Applied to accumulator BEFORE integer conversion to preserve GCD alignment
        if (Math.abs(playerMouseYaw) > 0.3f && Math.abs(sHorizMouseAccum) > 0.1f) {
            boolean mouseLeft = playerMouseYaw < 0;
            boolean assistLeft = sHorizMouseAccum < 0;
            if (mouseLeft != assistLeft) {
                float oppFactor = 1.0f - clampF(Math.abs(playerMouseYaw) / 3.0f, 0, 0.75f);
                sHorizMouseAccum *= oppFactor;
                sVertMouseAccum *= oppFactor;
            }
        }

        // Convert to mouse units and apply (GCD-compliant integer steps)
        int hSteps = (int) sHorizMouseAccum;
        int vSteps = (int) sVertMouseAccum;
        sHorizMouseAccum -= hSteps;
        sVertMouseAccum -= vSteps;

        float sens = mc.gameSettings.mouseSensitivity;
        float sBase = sens * 0.6f + 0.2f;
        float sScale = sBase * sBase * sBase * 8.0f;
        float mouseUnit = sScale * 0.15f;

        // Clamp step count based on sensitivity to cap max angle at ~15 degrees
        // Clamping steps (not angles) preserves GCD alignment
        int maxYSteps = mouseUnit > 1e-5f ? Math.max(1, (int)(15.0f / mouseUnit)) : 100;
        int maxPSteps = mouseUnit > 1e-5f ? Math.max(1, (int)(12.0f / mouseUnit)) : 80;
        hSteps = Math.max(-maxYSteps, Math.min(maxYSteps, hSteps));
        vSteps = Math.max(-maxPSteps, Math.min(maxPSteps, vSteps));

        // Pure GCD-aligned delta: integer steps * sensitivity formula
        // NO further scaling after this — any float multiplication breaks GCD
        float sYawDelta = (float) hSteps * sScale * 0.15f;
        float sPitchDelta = (float) vSteps * sScale * 0.15f;

        player.rotationYaw += sYawDelta;
        player.rotationPitch -= sPitchDelta;
        player.rotationPitch = MathHelper.clamp_float(player.rotationPitch, -90, 90);
        sLastAppliedYaw = player.rotationYaw;

        sDriftX = 0;
        sDriftY = 0;
    }

    private void queueSimpleH(float adj, float angleDiff) {
        if (adj == 0) { sHorizMouseAccum = 0; return; }
        adj *= 5.0f;
        float speed = (float) horizontalSpeed.getValue();
        if (angleDiff <= 10.0f) sHorizBoost = speed;
        if (sHorizBoost > 0) {
            speed -= sHorizBoost / 3.0f;
            sHorizBoost -= angleDiff / 200.0f;
        }
        sHorizMouseAccum += speed * adj;
    }

    private void queueSimpleV(float adj, float angleDiff) {
        if (adj == 0) { sVertMouseAccum = 0; return; }
        adj *= 5.0f;
        float speed = (float) verticalSpeed.getValue();
        if (angleDiff <= 10.0f) sVertBoost = speed;
        if (sVertBoost > 0) {
            speed -= sVertBoost / 3.0f;
            sVertBoost -= angleDiff / 200.0f;
        }
        sVertMouseAccum += speed * adj;
    }

    private void updateSimpleDrift() {
        sDriftTimer += 1.0;
        if (sDriftTimer >= 250 + random.nextInt(50)) {
            sDriftTimer = randRange(-100, -50);
            sRandOffsetX = randInt(-1, 2);
            sRandOffsetY = randInt(-1, 2);
        }
        int hStep = sRandOffsetX;
        int vStep = sRandOffsetY;
        if (random.nextInt(10) < 2) hStep = 0;
        if (random.nextInt(10) < 2) vStep = 0;
        if (sDriftTimer < 0) { hStep = 0; vStep = 0; }
        if (random.nextInt(20) == 1) {
            sDriftX += hStep;
            sDriftY += vStep;
        }
        if ((sHorizMouseAccum > 0 && sDriftX < 0) ||
            (sHorizMouseAccum < 0 && sDriftX > 0))
            sDriftX = 0;
    }

    // ================================================================
    //  ADAPTIVE MODE — PID control with lead prediction, snap,
    //  multi-layer noise, pitch drift spring
    //  (Vape AimAssistTargetingSubModule)
    // ================================================================

    private void adaptiveModeTick(Minecraft mc) {
        EntityPlayerSP player = mc.thePlayer;

        long now = System.nanoTime();
        float dt;
        if (!aInit || aLastFrameNanos == 0L) {
            dt = 0.05f; // first tick, assume 50ms
        } else {
            dt = (float)(now - aLastFrameNanos) / 1.0E9f;
            dt = Math.max(0.008333334f, Math.min(0.12f, dt));
        }
        aLastFrameNanos = now;

        float hSpeed = (float) horizontalSpeed.getValue();
        float vSpeed = (float) verticalSpeed.getValue();
        boolean yawSnapEnabled = hSpeed > 2.0f;
        boolean pitchSnapEnabled = aimVertically.isEnabled() && vSpeed > 2.0f;
        boolean snapYaw = yawSnapEnabled && aYawSnapped;
        boolean snapPitch = pitchSnapEnabled && aPitchSnapped;

        // Resolve aim target
        double[] aimPt = resolveAimTarget(player, target);
        double resolvedX = aimPt[0], resolvedY = aimPt[1], resolvedZ = aimPt[2];

        // Target and relative motion
        double tmx = target.motionX, tmy = target.motionY, tmz = target.motionZ;
        double rmx = tmx - player.motionX;
        double rmy = tmy - player.motionY;
        double rmz = tmz - player.motionZ;
        double tlx = tmx + rmx * 0.08;
        double tly = tmy + rmy * 0.1;
        double tlz = tmz + rmz * 0.08;

        if (!aAimPtInit) {
            aAimX = resolvedX; aAimY = resolvedY; aAimZ = resolvedZ;
            aLeadX = tlx; aLeadY = tly; aLeadZ = tlz;
            aAimPtInit = true;
        }

        double tHorizSpd = Math.sqrt(tmx*tmx + tmz*tmz);
        double rHorizSpd = Math.sqrt(rmx*rmx + rmz*rmz);
        double tDist = player.getDistanceToEntity(target);

        // Exponential aim point smoothing
        double baseSmooth = targetArea.getValue() == AreaMode.CLOSEST ? 0.3 : 0.28;
        double aimSmooth = clamp(baseSmooth + Math.min(0.35, rHorizSpd * 0.5), 0.08, 0.75);
        double leadMotScale = tHorizSpd + rHorizSpd * 0.25;
        double leadSmooth = clamp(0.18 + Math.min(0.42, leadMotScale * 0.72), 0.12, 0.68);
        double fScale = clamp(dt * 60.0, 0.45, 2.4);
        double aimBlend = 1.0 - Math.pow(1.0 - aimSmooth, fScale);
        double leadBlend = 1.0 - Math.pow(1.0 - leadSmooth, fScale);

        aAimX += (resolvedX - aAimX) * aimBlend;
        aAimY += (resolvedY - aAimY) * aimBlend;
        aAimZ += (resolvedZ - aAimZ) * aimBlend;
        aLeadX += (tlx - aLeadX) * leadBlend;
        aLeadY += (tly - aLeadY) * leadBlend;
        aLeadZ += (tlz - aLeadZ) * leadBlend;

        // Clamp aim lag
        double lagX = resolvedX - aAimX, lagZ = resolvedZ - aAimZ;
        double hAimLag = Math.sqrt(lagX*lagX + lagZ*lagZ);
        double maxLag = 0.3 + tHorizSpd * 1.3 + rHorizSpd * 0.4;
        if (hAimLag > maxLag) {
            aAimX = resolvedX; aAimY = resolvedY; aAimZ = resolvedZ;
            aLeadX = tlx; aLeadY = tly; aLeadZ = tlz;
        }

        // Lead amount
        double leadAmt = clamp(0.35 + tDist * 0.045 + tHorizSpd * 0.95
                + Math.min(0.18, rHorizSpd * 0.12), 0.1, 1.05);
        double distLeadScale = clamp((tDist - 0.8) / 2.5, 0, 1);
        leadAmt *= 0.3 + 0.7 * distLeadScale;
        leadAmt = Math.max(0.05, leadAmt);

        // Player eye Y and vertical velocity tracking
        double pX = player.posX, pZ = player.posZ;
        double pEyeY = player.posY + player.getEyeHeight();
        float measVVel = 0;
        if (aInit && aLastEyeY != 0) measVVel = (float)((pEyeY - aLastEyeY) / dt);
        aVertVelocity += (measVVel - aVertVelocity) * 0.65f;
        aLastEyeY = pEyeY;

        // Air factor
        if (player.onGround) {
            aLastGroundEyeY = pEyeY;
            aAirFactor *= 0.35f;
        } else {
            if (aLastGroundEyeY == 0) aLastGroundEyeY = pEyeY;
            double airH = Math.max(0, pEyeY - aLastGroundEyeY);
            float velAF = (float)Math.min(0.7, Math.max(0, aVertVelocity) * 0.34);
            float hgtAF = (float)Math.min(0.82, airH * 0.58);
            aAirFactor = Math.max(aAirFactor * 0.92f, Math.max(velAF, hgtAF));
        }

        // Adjusted aim point with lead
        double adjAimX = aAimX, adjAimZ = aAimZ;
        double vAimDelta = aAimY - pEyeY;
        double vOffScale = clamp(Math.abs(vAimDelta) / 0.7, 0, 1);
        double vLeadAmt = Math.min(leadAmt, 0.7 + tDist * 0.04);
        double vLeadScale = 0.28 + 0.52 * vOffScale;
        double vLead = aLeadY * vLeadAmt * vLeadScale;
        double vLeadLimit = 0.16 + tDist * 0.055;
        vLead = clamp(vLead, -vLeadLimit, vLeadLimit);
        double adjAimY = aAimY + vLead;

        if (snapYaw)  { adjAimX = resolvedX; adjAimZ = resolvedZ; }
        if (snapPitch) { adjAimY = resolvedY; }

        // Target yaw/pitch from adjusted aim point
        double dX = adjAimX - pX, dZ = adjAimZ - pZ, dY = adjAimY - pEyeY;
        double hDist = Math.sqrt(dX*dX + dZ*dZ);
        float tgtYaw = (float)(Math.toDegrees(Math.atan2(dZ, dX)) - 90.0);
        float tgtPitch = (float)(-Math.toDegrees(Math.atan2(dY, Math.max(hDist, 1e-4))));

        // Rate limiting on target yaw/pitch changes
        if (aInit) {
            float yTgtChg = Math.abs(wrap180(tgtYaw - aLastTargetYaw));
            float pTgtChg = Math.abs(wrap180(tgtPitch - aLastTargetPitch));
            float abruptThresh = targetArea.getValue() == AreaMode.CLOSEST ? 20.0f : 12.0f;
            float maxChg = Math.max(yTgtChg, pTgtChg);
            float abruptFactor = clampF((maxChg - abruptThresh) / 50.0f, 0, 1);
            aOvershoot = Math.max(aOvershoot, abruptFactor);
            if (abruptFactor > 0.3f) {
                aAimStrength *= 0.3f;
                aAimX = resolvedX; aAimY = resolvedY; aAimZ = resolvedZ;
            }

            float yRateScale = 1.0f + Math.min(2.0f, yTgtChg / 60.0f);
            float pRateScale = 1.0f + Math.min(2.0f, pTgtChg / 60.0f);
            float maxYawRate = (120.0f + (float)(rHorizSpd * 700.0)) * yRateScale;
            float maxPitchRate = (95.0f + (float)(Math.abs(aLeadY) * 550.0)) * pRateScale;
            float viewYawDiff = Math.abs(wrap180(tgtYaw - player.rotationYaw));
            float wideScale = smoothStep(10, 35, viewYawDiff);
            maxYawRate *= 1.0f + wideScale * 1.5f;
            maxYawRate = clampF(maxYawRate, 90, 360);
            maxPitchRate = clampF(maxPitchRate, 70, 240);

            float yStep = wrap180(tgtYaw - aLastTargetYaw);
            float pStep = wrap180(tgtPitch - aLastTargetPitch);
            if (!snapYaw)  yStep = clampF(yStep, -maxYawRate * dt, maxYawRate * dt);
            if (!snapPitch) pStep = clampF(pStep, -maxPitchRate * dt, maxPitchRate * dt);
            tgtYaw = aLastTargetYaw + yStep;
            tgtPitch = aLastTargetPitch + pStep;
        }

        // Overshoot decay
        aOvershoot *= (float)Math.pow(0.02, dt);
        if (aOvershoot < 0.01f) aOvershoot = 0;
        float ovMul = 1.0f + 3.0f * aOvershoot;

        // Error computation
        float pYaw = player.rotationYaw;
        float pPitch = player.rotationPitch;
        float yErr = wrap180(tgtYaw - pYaw);
        float pErr = wrap180(tgtPitch - pPitch);
        if (!aimVertically.isEnabled()) pErr = 0;

        float absYErr = Math.abs(yErr);
        float absPErr = Math.abs(pErr);
        float combErr = (float)Math.sqrt(absYErr*absYErr + absPErr*absPErr);

        // Snap hysteresis
        aYawSnapped = shouldSnap(yawSnapEnabled, aYawSnapped, absYErr);
        aPitchSnapped = shouldSnap(pitchSnapEnabled, aPitchSnapped, absPErr);
        snapYaw = yawSnapEnabled && aYawSnapped;
        snapPitch = pitchSnapEnabled && aPitchSnapped;

        // Aim strength
        float desStr = 1.0f - smoothStep(1.5f, 8.0f, combErr);
        if (aInit) {
            float yClose = -(absYErr - Math.abs(aLastYawDiff)) / dt;
            desStr += clampF(yClose / 20.0f, -0.3f, 0.3f);
            desStr = clampF(desStr, 0, 1);
        }
        float strBlend = desStr > aAimStrength
                ? clampF(dt * 5.0f, 0.02f, 0.35f)
                : clampF(dt * 18.0f, 0.05f, 0.75f);
        aAimStrength += (desStr - aAimStrength) * strBlend;

        // PID gains: Less steep curve so mid-speeds are responsive
        float hSpd = Math.min(hSpeed, 10), vSpd = Math.min(vSpeed, 10);
        float hGainIn = (hSpd - 1) / 9.0f; hGainIn = (float) Math.pow(hGainIn, 1.3);
        float vGainIn = (vSpd - 1) / 9.0f; vGainIn = (float) Math.pow(vGainIn, 1.3);
        float hGainScale = 0.25f + 0.75f * clampF(hGainIn, 0, 1);
        float vGainScale = 0.25f + 0.75f * clampF(vGainIn, 0, 1);

        // Bias integral
        float sq = aAimStrength * aAimStrength;
        float hSpeedFactor = clampF((hSpeed - 10.0f) / 90.0f, 0, 1);
        float vSpeedFactor = clampF((vSpeed - 10.0f) / 90.0f, 0, 1);
        if (Math.signum(yErr) != Math.signum(aLastYawDiff)
                && Math.abs(yErr) > 0.1f && Math.abs(aLastYawDiff) > 0.1f)
            aYawBias *= 0.3f;
        if (Math.signum(pErr) != Math.signum(aLastPitchDiff)
                && Math.abs(pErr) > 0.1f && Math.abs(aLastPitchDiff) > 0.1f)
            aPitchBias *= 0.3f;
        aYawBias += yErr * dt * sq * (1.0f - hSpeedFactor);
        aPitchBias += pErr * dt * sq * (1.0f - vSpeedFactor);
        float biasRet = 1.0f - (1.0f - aAimStrength) * clampF(dt * 5.0f, 0, 0.5f);
        aYawBias *= biasRet;
        aPitchBias *= biasRet;
        aYawBias = clampF(aYawBias, -15 * (1 - hSpeedFactor * 0.9f),
                                      15 * (1 - hSpeedFactor * 0.9f));
        aPitchBias = clampF(aPitchBias, -10 * (1 - vSpeedFactor * 0.9f),
                                         10 * (1 - vSpeedFactor * 0.9f));

        // Velocity derivative
        float yErrVel = aInit ? (yErr - aLastYawDiff) / dt : 0;
        float pErrVel = aInit ? (pErr - aLastPitchDiff) / dt : 0;
        aYawVel = aYawVel * 0.85f + yErrVel * 0.15f;
        aPitchVel = aPitchVel * 0.85f + pErrVel * 0.15f;

        // Simulated Reaction Buffer (SRB) - 100ms human delay on direction changes
        float smTgtYRate = 0, smTgtPRate = 0, plrYRate = 0, plrPRate = 0;
        float trueMouseYRate = 0, totalYRate = 0;
        if (aInit) {
            float tYRate = wrap180(tgtYaw - aLastTargetYaw) / dt;
            float tPRate = wrap180(tgtPitch - aLastTargetPitch) / dt;
            float rawPlrYRate = wrap180(pYaw - aLastPlayerYaw) / dt;
            float rawPlrPRate = wrap180(pPitch - aLastPlayerPitch) / dt;
            totalYRate = rawPlrYRate;
            // Isolate genuine player mouse input by subtracting assist's own output
            trueMouseYRate = rawPlrYRate - (aLastAppliedYawDelta / dt);
            plrYRate = trueMouseYRate;
            plrPRate = rawPlrPRate - (aLastAppliedPitchDelta / dt);
            
            // Push to history buffer
            aHistYawVel[aHistIdx] = tYRate;
            aHistPitchVel[aHistIdx] = tPRate;
            aHistIdx = (aHistIdx + 1) % 10;
            
            // Read from 100ms ago (approx 5 frames at 50fps rendering, or dynamically scaled)
            // To be safe we'll use a blended historical velocity to simulate human reaction lag
            float histYRate = 0, histPRate = 0;
            for (int i = 0; i < 10; i++) {
                histYRate += aHistYawVel[i];
                histPRate += aHistPitchVel[i];
            }
            histYRate /= 10.0f;
            histPRate /= 10.0f;

            // If the target reverses direction suddenly, rely heavily on historical velocity (overshoot). 
            // If continuing in straight line, blend closer to current velocity.
            boolean yReversed = Math.signum(tYRate) != Math.signum(histYRate) && Math.abs(tYRate) > 30;
            float reactBlend = yReversed ? clampF(dt * 3.0f, 0.01f, 0.15f) : clampF(dt * 12.0f, 0.05f, 0.45f);
            
            aYawAccel += (histYRate - aYawAccel) * reactBlend;
            aPitchAccel += (histPRate - aPitchAccel) * reactBlend;
            smTgtYRate = aYawAccel;
            smTgtPRate = aPitchAccel;
        }

        // Flick detection
        float flickThresh = targetArea.getValue() == AreaMode.CLOSEST ? 1.5f : 0.5f;
        float ySgn = Math.signum(yErr);
        aYawFlickTicks = (ySgn != aLastYawSign && Math.abs(yErr) > flickThresh)
                ? Math.min(aYawFlickTicks + 1, 8)
                : Math.max(0, aYawFlickTicks - dt * 3);
        aLastYawSign = ySgn;
        float pSgn = Math.signum(pErr);
        aPitchFlickTicks = (pSgn != aLastPitchSign && Math.abs(pErr) > flickThresh)
                ? Math.min(aPitchFlickTicks + 1, 8)
                : Math.max(0, aPitchFlickTicks - dt * 3);
        aLastPitchSign = pSgn;
        float yFlick = clampF(aYawFlickTicks / 5.0f, 0, 1);
        float pFlick = clampF(aPitchFlickTicks / 5.0f, 0, 1);

        // Dynamic Edge-Weighting (Pull hard on edges, relax in center)
        float edgeWeight = clampF(Math.abs(yErr) / 4.0f, 0.5f, 1.0f);
        
        // Compute PID output per axis (Humanized & Dynamic)
        float yErrGain = lerp(aAimStrength, 5, 2.5f + hSpd * 0.25f) * hGainScale
                * (1 - 0.5f * yFlick) * edgeWeight;
        float pErrGain = lerp(aAimStrength, 4, 2.0f + vSpd * 0.2f) * vGainScale
                * (1 - 0.5f * pFlick);
        float yBiasGain = lerp(aAimStrength, 0.15f, 0.7f + hSpd * 0.05f) * hGainScale;
        float pBiasGain = lerp(aAimStrength, 0.12f, 0.6f + vSpd * 0.04f) * vGainScale;
        float yVelGain = lerp(aAimStrength, 0.1f, 0.35f) * hGainScale
                * (1 + 1.5f * yFlick);
        float pVelGain = lerp(aAimStrength, 0.08f, 0.3f) * vGainScale
                * (1 + 1.5f * pFlick);
        float tgtYRateGain = (1.0f + hSpd * 0.05f) * hGainScale;
        float tgtPRateGain = (0.9f + vSpd * 0.04f) * vGainScale;
        float plrYComp = lerp(aAimStrength, 0.2f, 0.55f);
        float plrPComp = lerp(aAimStrength, 0.15f, 0.45f);

        float yRate = yErrGain * yErr + yBiasGain * aYawBias
                + yVelGain * aYawVel + tgtYRateGain * smTgtYRate
                - plrYComp * plrYRate;
        float pRate = 0;
        if (aimVertically.isEnabled()) {
            pRate = pErrGain * pErr + pBiasGain * aPitchBias
                    + pVelGain * aPitchVel + tgtPRateGain * smTgtPRate
                    - plrPComp * plrPRate;
        } else {
            aPitchBias = 0; aPitchVel = 0;
        }
        if (!snapPitch && aimVertically.isEnabled()) pRate += computePitchDrift(pPitch, dt, now);

        // Strafe multiplier
        float strafeIn = player.moveStrafing;
        if (strafeIncrease.isEnabled() && Math.abs(strafeIn) > 0.01f) {
            boolean toRight = yErr > 0;
            boolean sAway = (toRight && strafeIn < 0) || (!toRight && strafeIn > 0);
            if (sAway) yRate *= 1.15f;
        }

        // Mouse opposition: reduce assist when player actively fights it
        boolean yawOpposing = Math.abs(trueMouseYRate) > 30 
            && Math.signum(trueMouseYRate) != Math.signum(yErr) 
            && Math.abs(yErr) > 1.0f;
        if (yawOpposing) {
            aPlayerOpposeTicks = Math.min(aPlayerOpposeTicks + 1, 20);
        } else {
            aPlayerOpposeTicks = Math.max(aPlayerOpposeTicks - 2, 0);
        }
        float mouseOpposeScale = 1.0f - clampF(aPlayerOpposeTicks / 8.0f, 0, 0.8f);
        yRate *= mouseOpposeScale;
        pRate *= mouseOpposeScale;

        // Player Strafing Desync Fix (Move Bypass) — smooth gradient
        float plrMotion = (float) Math.sqrt(player.motionX * player.motionX + player.motionZ * player.motionZ);
        float moveDesyncLimiter = 1.0f;
        if (plrMotion > 0.05f) {
            float motionBlend = smoothStep(0.05f, 0.3f, plrMotion);
            float rotBlend = smoothStep(50, 300, Math.abs(totalYRate));
            moveDesyncLimiter = 1.0f - 0.4f * motionBlend * rotBlend;
        }

        // Rate limiting (Raised ceilings but clamped by move desync)
        float wideScale = smoothStep(8, 40, absYErr);
        float wideMul = 1 + wideScale * 2.5f;
        float yBaseLimit = (80 + hSpd * 40) * hGainScale * ovMul * wideMul * moveDesyncLimiter;
        float pBaseLimit = (60 + vSpd * 30) * vGainScale * ovMul * moveDesyncLimiter;
        float yMotLim = (Math.abs(smTgtYRate) * 0.85f + 40) * (0.35f + hGainScale * 0.65f) * wideMul;
        float pMotLim = (Math.abs(smTgtPRate) * 0.8f + 30) * (0.35f + vGainScale * 0.65f);
        float maxYOut = Math.min(800 * ovMul * wideMul, Math.max(yBaseLimit, yMotLim));
        float maxPOut = Math.min(600 * ovMul, Math.max(pBaseLimit, pMotLim));
        yRate = clampF(yRate, -maxYOut, maxYOut);
        pRate = clampF(pRate, -maxPOut, maxPOut);

        // Distance scaling
        float distScale = smoothStep(0.5f, 3.0f, (float) tDist);
        float wideBase = lerp(wideScale, 0.15f, 0.65f);
        float yDistMul = lerp(aAimStrength,
                wideBase + (1 - wideBase) * distScale, 0.4f + 0.6f * distScale);
        float pDistMul = lerp(aAimStrength,
                0.2f + 0.8f * distScale, 0.45f + 0.55f * distScale);
        yRate *= yDistMul;
        pRate *= pDistMul;

        // Multi-layer Kinematic Noise (Anti-FFT matrix bypass)
        if (now - aLastKineChange > 150000000L) { // Change target spline every 150ms
            aKineTargetY = (sharedRandom.nextFloat() - 0.5f) * 25.0f;
            aKineTargetP = (sharedRandom.nextFloat() - 0.5f) * 16.0f;
            aLastKineChange = now;
        }
        // Smoothly interpolate towards the target spline (Perlin-esque micro-tremors)
        aKineNoiseY += (aKineTargetY - aKineNoiseY) * clampF(dt * 8.0f, 0.01f, 1.0f);
        aKineNoiseP += (aKineTargetP - aKineNoiseP) * clampF(dt * 8.0f, 0.01f, 1.0f);
        
        float noiseStr = 0.15f + 0.85f * aAimStrength;
        float spdNoiseScale = 0.5f + 0.5f * (1 - hGainIn);
        float yNoise = aKineNoiseY * noiseStr * spdNoiseScale;
        float pNoise = aKineNoiseP * noiseStr * spdNoiseScale;
        if (!snapYaw) yRate += yNoise;
        if (aimVertically.isEnabled() && !snapPitch) pRate += pNoise;

        // Convert to mouse units
        float yFrameDelta = yRate * dt;
        float pFrameDelta = pRate * dt;
        float sens = mc.gameSettings.mouseSensitivity;
        float sBase = sens * 0.6f + 0.2f;
        float sScale = sBase * sBase * sBase * 8.0f;
        float mouseUnit = sScale * 0.15f;

        if (mouseUnit > 1e-5f) {
            float yVelUnits = yFrameDelta / mouseUnit;
            float pVelUnits = pFrameDelta / mouseUnit;
            float pBlendFactor = aimVertically.isEnabled() ? vSpeedFactor : 0;

            if (snapYaw) aPendingYaw = 0;
            if (snapPitch) aPendingPitch = 0;

            if (!snapYaw) {
                if (hSpeedFactor > 0) {
                    float tgtYUnits = yErr / mouseUnit;
                    float yCorr = clampF(tgtYUnits - aPendingYaw,
                            -hSpeed * 2, hSpeed * 2);
                    aPendingYaw += yVelUnits * (1 - hSpeedFactor)
                            + yCorr * hSpeedFactor;
                } else {
                    aPendingYaw += yVelUnits;
                }
            }
            if (!snapPitch) {
                if (pBlendFactor > 0) {
                    float tgtPUnits = aimVertically.isEnabled() ? pErr / mouseUnit : 0;
                    float pCorr = clampF(tgtPUnits - aPendingPitch,
                            -vSpeed * 2, vSpeed * 2);
                    aPendingPitch += pVelUnits * (1 - pBlendFactor)
                            + pCorr * pBlendFactor;
                } else {
                    aPendingPitch += pVelUnits;
                }
            }
        }

        if (!aInit) aInit = true;
        aLastYawDiff = yErr;
        aLastPitchDiff = pErr;
        aLastTargetYaw = wrap180(tgtYaw);
        aLastTargetPitch = wrap180(tgtPitch);
        aLastPlayerYaw = pYaw;
        aLastPlayerPitch = pPitch;
    }

    /** Called on render tick to apply accumulated mouse deltas (sub-frame smoothness). */
    private void applyAdaptivePending(Minecraft mc) {
        EntityPlayerSP player = mc.thePlayer;
        float hSpd = (float) horizontalSpeed.getValue();
        float vSpd = (float) verticalSpeed.getValue();
        boolean yawSnapEnabled = hSpd > 2.0f;
        boolean pitchSnapEnabled = aimVertically.isEnabled() && vSpd > 2.0f;
        boolean snapYaw = yawSnapEnabled && aYawSnapped;
        boolean snapPitch = pitchSnapEnabled && aPitchSnapped;

        float sens = mc.gameSettings.mouseSensitivity;
        float sBase = sens * 0.6f + 0.2f;
        float sScale = sBase * sBase * sBase * 8.0f;
        float mouseUnit = sScale * 0.15f;

        int ySteps = Math.round(aPendingYaw);
        int pSteps = Math.round(aPendingPitch);
        float remY = snapYaw ? 0 : aPendingYaw - ySteps;
        float remP = snapPitch ? 0 : aPendingPitch - pSteps;

        // Snap override: compute target angles and quantize to integer mouse steps (GCD safe)
        if ((snapYaw || snapPitch) && target != null && mouseUnit > 1e-5f) {
            double[] snapPt = resolveSnapPoint(player, target);
            double dX = snapPt[0] - player.posX;
            double dZ = snapPt[2] - player.posZ;
            double dY2 = snapPt[1] - (player.posY + player.getEyeHeight());
            double hD = Math.sqrt(dX*dX + dZ*dZ);
            float tgtYaw = (float)(Math.toDegrees(Math.atan2(dZ, dX)) - 90.0);
            float tgtPitch = (float)(-Math.toDegrees(Math.atan2(dY2, Math.max(hD, 1e-4))));
            if (snapYaw) {
                float snapYawDelta = clampF(wrap180(tgtYaw - player.rotationYaw), -5.0f, 5.0f);
                ySteps = Math.round(snapYawDelta / mouseUnit);
            }
            if (snapPitch) {
                float snapPitchDelta = clampF(wrap180(tgtPitch - player.rotationPitch), -4.5f, 4.5f);
                pSteps = Math.round(snapPitchDelta / mouseUnit);
            }
        }

        // Clamp step count based on sensitivity to cap max angle at ~12 degrees/frame
        // Clamping steps (not angles) preserves GCD alignment
        int maxYSteps = mouseUnit > 1e-5f ? Math.max(1, (int)(12.0f / mouseUnit)) : 80;
        int maxPSteps = mouseUnit > 1e-5f ? Math.max(1, (int)(10.0f / mouseUnit)) : 60;
        ySteps = Math.max(-maxYSteps, Math.min(maxYSteps, ySteps));
        pSteps = Math.max(-maxPSteps, Math.min(maxPSteps, pSteps));

        // Pure GCD-aligned deltas: integer steps * sensitivity formula
        // NO further scaling — any float multiplication after this breaks GCD
        float aYawDelta = (float) ySteps * sScale * 0.15f;
        float aPitchDelta = (float) pSteps * sScale * 0.15f;

        player.rotationYaw += aYawDelta;
        player.rotationPitch += aPitchDelta;
        aLastAppliedYawDelta = aYawDelta;
        aLastAppliedPitchDelta = aPitchDelta;
        player.rotationPitch = MathHelper.clamp_float(player.rotationPitch, -90, 90);

        aPendingYaw = remY;
        aPendingPitch = remP;
    }

    // ======================== Aim Point Resolution ========================

    private double[] resolveAimTarget(EntityPlayerSP player, EntityLivingBase tgt) {
        double pEyeY = player.posY + player.getEyeHeight();
        AxisAlignedBB bb = tgt.getEntityBoundingBox();
        double tMinY = bb.minY, tMaxY = bb.maxY;
        double tHeight = tMaxY - tMinY;
        double tCenterY = tMinY + tHeight * 0.65;
        double airOff = Math.min(0.85, aAirFactor);
        double eyeOff = 0.1 + airOff;
        double eyeAlignY = clamp(pEyeY - eyeOff, tMinY + 0.01, tMaxY - 0.01);
        double centerBlend = clamp(airOff / 0.55, 0, 1);
        // When player is above target, blend toward center mass instead of head top
        double heightAbove = pEyeY - tMaxY;
        double aboveFactor = clamp(heightAbove / 1.5, 0, 1);
        double effectiveBlend = Math.max(centerBlend, aboveFactor);
        double tY = eyeAlignY + (tCenterY - eyeAlignY) * effectiveBlend;
        tY = clamp(tY, tMinY + 0.01, tMaxY - 0.01);

        if (targetArea.getValue() == AreaMode.CLOSEST) {
            double cx = clamp(player.posX, bb.minX, bb.maxX);
            double cz = clamp(player.posZ, bb.minZ, bb.maxZ);
            double cy = clamp(pEyeY - eyeOff, tMinY + 0.01, tMaxY - 0.01);
            cy += (tCenterY - cy) * effectiveBlend;
            cy = clamp(cy, tMinY + 0.01, tMaxY - 0.01);

            if (aPredInit) {
                cx = aPredX + (cx - aPredX) * 0.35;
                cy = aPredY + (cy - aPredY) * 0.35;
                cz = aPredZ + (cz - aPredZ) * 0.35;
            }
            aPredX = cx; aPredY = cy; aPredZ = cz;
            aPredInit = true;
            return new double[]{cx, cy, cz};
        }
        return new double[]{tgt.posX, tY, tgt.posZ};
    }

    private double[] resolveSnapPoint(EntityPlayerSP player, EntityLivingBase tgt) {
        double pEyeY = player.posY + player.getEyeHeight();
        AxisAlignedBB bb = tgt.getEntityBoundingBox();
        double tMinY = bb.minY, tMaxY = bb.maxY;
        double tHeight = tMaxY - tMinY;
        double tCenterY = tMinY + tHeight * 0.65;
        double airOff = Math.min(0.85, aAirFactor);
        double eyeOff = 0.1 + airOff;
        double eyeAlignY = clamp(pEyeY - eyeOff, tMinY + 0.01, tMaxY - 0.01);
        double centerBlend = clamp(airOff / 0.55, 0, 1);
        double heightAbove = pEyeY - tMaxY;
        double aboveFactor = clamp(heightAbove / 1.5, 0, 1);
        double effectiveBlend = Math.max(centerBlend, aboveFactor);
        double tY = eyeAlignY + (tCenterY - eyeAlignY) * effectiveBlend;
        tY = clamp(tY, tMinY + 0.01, tMaxY - 0.01);

        if (targetArea.getValue() == AreaMode.CLOSEST) {
            double cx = clamp(player.posX, bb.minX, bb.maxX);
            double cy = clamp(pEyeY - eyeOff, tMinY + 0.01, tMaxY - 0.01);
            cy += (tCenterY - cy) * effectiveBlend;
            cy = clamp(cy, tMinY + 0.01, tMaxY - 0.01);
            double cz = clamp(player.posZ, bb.minZ, bb.maxZ);
            return new double[]{cx, cy, cz};
        }
        return new double[]{tgt.posX, tY, tgt.posZ};
    }

    // ======================== Pitch Drift Spring ========================

    private float computePitchDrift(float playerPitch, float dt, long now) {
        float strength = 0.65f + 0.35f * aAimStrength;
        if (aDriftNextNanos == 0 || now >= aDriftNextNanos) {
            float amp = lerp(strength, 0.05f, 0.15f);
            float pitchCorr = playerPitch > 22 ? -0.18f : (playerPitch < -22 ? 0.18f : 0);
            aDriftTarget = (random.nextFloat() * 2 - 1) * amp + pitchCorr;
            aDriftTarget = clampF(aDriftTarget, -0.5f, 0.5f);
            aDriftNextNanos = now + (300L + random.nextInt(420)) * 1000000L;
        }
        float sprStr = lerp(strength, 2, 5);
        float damp = (float)Math.pow(0.04f, dt);
        aDriftVel += (aDriftTarget - aDriftPos) * sprStr * dt;
        aDriftVel *= damp;
        aDriftPos += aDriftVel * dt;
        float posLim = lerp(strength, 0.45f, 0.8f);
        if (aDriftPos > posLim)  { aDriftPos = posLim;  aDriftVel = Math.min(0, aDriftVel); }
        if (aDriftPos < -posLim) { aDriftPos = -posLim; aDriftVel = Math.max(0, aDriftVel); }
        aDriftNoise += (random.nextFloat() * 2 - 1 - aDriftNoise)
                * clampF(dt * 5, 0.02f, 0.18f);
        float elapsed = (float)(now - aNoiseStartNanos) / 1.0E9f;
        float noise = (float)(Math.sin(elapsed * 8.7 + 0.4) * 0.35
                + Math.sin(elapsed * 13.1 + 2.2) * 0.22
                + Math.sin(elapsed * 19.6 + 1.1) * 0.12
                + aDriftNoise * 0.3);
        float restore = -aDriftPos * lerp(strength, 1.4f, 3.0f);
        float output = aDriftVel * 0.25f + restore
                + noise * lerp(strength, 0.35f, 0.9f);
        float pitchLimScale = 1 - 0.85f * smoothStep(72, 88, Math.abs(playerPitch));
        float outLim = lerp(strength, 0.25f, 0.55f) * pitchLimScale;
        return clampF(output, -outLim, outLim);
    }

    // ======================== State Reset ========================

    private void resetAll() {
        target = null;
        resetSimple();
        resetAdaptive();
    }

    private void resetSimple() {
        sHorizVel = sHorizVelBuf = sVertVel = sVertVelBuf = 0;
        sHorizMouseAccum = sVertMouseAccum = 0;
        sSwapCounter = 0;
        sDriftX = sDriftY = sRandOffsetX = sRandOffsetY = 0;
        sDriftTimer = 0;
        sPrevOnLeft = sPrevAbove = false;
        sLastAngleDiff = 0;
        sSampleCounter = 0;
        sHorizBoost = sVertBoost = 0;
        sPrevTargetX = sPrevTargetZ = 0;
        sPrevTargetInitialized = false;
        sTrackedTarget = null;
        sRetargetCounter = 0;
        sLastAppliedYaw = Float.NaN;
    }

    private void resetAdaptive() {
        aPendingYaw = aPendingPitch = 0;
        aYawSnapped = aPitchSnapped = false;
        aYawBias = aPitchBias = 0;
        aYawVel = aPitchVel = 0;
        aLastYawDiff = aLastPitchDiff = 0;
        aLastTargetYaw = aLastTargetPitch = 0;
        aYawAccel = aPitchAccel = 0;
        aLastPlayerYaw = aLastPlayerPitch = 0;
        aInit = false;
        aLastFrameNanos = 0;
        aAimPtInit = false;
        aAimX = aAimY = aAimZ = 0;
        aLeadX = aLeadY = aLeadZ = 0;
        aAimStrength = 0;
        aYawFlickTicks = aPitchFlickTicks = 0;
        aLastYawSign = aLastPitchSign = 0;
        aOvershoot = 0;
        aLastEyeY = aLastGroundEyeY = 0;
        aVertVelocity = aAirFactor = 0;
        aPredInit = false;
        aPredX = aPredY = aPredZ = 0;
        aNoiseStartNanos = System.nanoTime();
        aDriftPos = aDriftVel = aDriftTarget = aDriftNoise = 0;
        aDriftNextNanos = 0;
        aTargetSwitchTicks = 0;
        aLastAppliedYawDelta = aLastAppliedPitchDelta = 0;
        aPlayerOpposeTicks = 0;
    }

    // ======================== Math Utilities ========================

    private static boolean shouldSnap(boolean enabled, boolean snapped, float angle) {
        if (!enabled) return false;
        return snapped ? angle <= SNAP_EXIT : angle <= SNAP_ENTER;
    }

    private static float smoothStep(float e0, float e1, float x) {
        float t = clampF((x - e0) / (e1 - e0), 0, 1);
        return t * t * (3 - 2 * t);
    }

    private static float lerp(float t, float a, float b) {
        return a + t * (b - a);
    }

    private static float wrap180(float angle) {
        // Robust wrapping: handles all edge cases including -180 boundary and large values
        angle = ((angle % 360.0f) + 540.0f) % 360.0f - 180.0f;
        return angle;
    }

    private static float clampF(float v, float min, float max) {
        return v < min ? min : (v > max ? max : v);
    }

    private static double clamp(double v, double min, double max) {
        return v < min ? min : (v > max ? max : v);
    }

    private float yawAngleTo(EntityPlayerSP player, EntityLivingBase entity) {
        double dx = entity.posX - player.posX;
        double dz = entity.posZ - player.posZ;
        float targetYaw = (float)(Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
        return Math.abs(wrap180(targetYaw - player.rotationYaw));
    }

    private double horizAngleDiff(double px, double pz, float yaw,
                                  double tx, double tz) {
        double dx = tx - px, dz = tz - pz;
        float tgtYaw = (float)(Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
        return Math.abs(wrap180(tgtYaw - yaw));
    }

    private boolean isOnLeft(double px, double pz, float yaw,
                             double tx, double tz) {
        double dx = tx - px, dz = tz - pz;
        float tgtYaw = (float)(Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
        return wrap180(tgtYaw - yaw) < 0;
    }

    private int vertAngleDiff(EntityPlayerSP player, double tx, double ty, double tz) {
        double dx = tx - player.posX;
        double dz = tz - player.posZ;
        double dy = ty - (player.posY + player.getEyeHeight());
        double hDist = Math.sqrt(dx*dx + dz*dz);
        float targetPitch = (float)(-Math.toDegrees(Math.atan2(dy, Math.max(hDist, 1e-4))));
        return (int)(targetPitch - player.rotationPitch);
    }

    private double randRange(double min, double max) {
        return min + sharedRandom.nextDouble() * (max - min);
    }

    private int randInt(int min, int maxExcl) {
        return min + random.nextInt(maxExcl - min);
    }
}
