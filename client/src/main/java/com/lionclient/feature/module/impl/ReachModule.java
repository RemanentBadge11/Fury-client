package com.lionclient.feature.module.impl;

import com.lionclient.feature.module.Category;
import com.lionclient.feature.module.Module;
import com.lionclient.feature.setting.BooleanSetting;
import com.lionclient.feature.setting.DecimalSetting;
import com.lionclient.feature.setting.EnumSetting;
import com.lionclient.feature.setting.NumberSetting;
import com.lionclient.util.MouseButtonHelper;
import com.lionclient.util.TargetFilter;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityOtherPlayerMP;
import net.minecraft.client.particle.EffectRenderer;
import net.minecraft.client.particle.EntityFX;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemAxe;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemSword;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.minecraftforge.client.event.MouseEvent;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.input.Keyboard;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Reach — Full Vape V4.21 architecture port.
 *
 * Features:
 *   - Random min/max range interpolation per attack
 *   - Normal mode: flat chance roll on attack
 *   - Advanced mode: target tracking with hit cooldown (10 ticks)
 *   - Misplace engine: client-side entity position displacement
 *   - Disadvantage mode: reverse misplace direction
 *   - Particle offset for visual consistency during misplace
 *   - Safety checks: sprint-only, water-disable, vertical-check, weapon-only
 */
public final class ReachModule extends Module {

    // ======================== Enums ========================

    public enum ChanceMode {
        NORMAL("Normal"), ADVANCED("Advanced");
        private final String name;
        ChanceMode(String n) { this.name = n; }
        @Override public String toString() { return name; }
    }

    // ======================== Settings ========================

    private final DecimalSetting minReach =
            new DecimalSetting("Min Reach", 3.0, 6.0, 0.01, 3.0);
    private final DecimalSetting maxReach =
            new DecimalSetting("Max Reach", 3.0, 6.0, 0.01, 3.1);
    private final NumberSetting chance =
            new NumberSetting("Chance", 0, 100, 1, 100);
    private final EnumSetting<ChanceMode> chanceMode =
            new EnumSetting<>("Chance Mode", ChanceMode.values(), ChanceMode.ADVANCED);
    private final BooleanSetting misplace =
            new BooleanSetting("Misplace", false);
    private final BooleanSetting disadvantage =
            new BooleanSetting("Disadvantage", false);
    private final BooleanSetting verticalCheck =
            new BooleanSetting("Vertical Check", false);
    private final BooleanSetting onlyWhileSprinting =
            new BooleanSetting("Only While Sprinting", false);
    private final BooleanSetting disableInWater =
            new BooleanSetting("Disable In Water", false);
    private final BooleanSetting weaponOnly =
            new BooleanSetting("Weapon Only", false);

    // ======================== Internal State ========================

    private final Random random = new Random();
    private final Map<Integer, EntityPositionState> entityStates = new HashMap<>();
    private Entity lastTarget = null;
    private boolean reachActive = false;
    private int hitCooldown = 0;

    // ======================== Constructor ========================

    public ReachModule() {
        super("Reach", "Extends attack reach distance.", Category.COMBAT, Keyboard.KEY_NONE);
        addSetting(minReach);
        addSetting(maxReach);
        addSetting(chance);
        addSetting(chanceMode);
        addSetting(misplace);
        addSetting(disadvantage);
        addSetting(verticalCheck);
        addSetting(onlyWhileSprinting);
        addSetting(disableInWater);
        addSetting(weaponOnly);
    }

    // ======================== HUD ========================

    @Override
    public String getHudInfo() {
        return String.format("%.2f-%.2f", minReach.getValue(), maxReach.getValue());
    }

    // ======================== Lifecycle ========================

    @Override
    protected void onEnable() {
        entityStates.clear();
        lastTarget = null;
        reachActive = false;
        hitCooldown = 0;
    }

    @Override
    protected void onDisable() {
        restoreAllPositions();
        entityStates.clear();
        lastTarget = null;
        reachActive = false;
        hitCooldown = 0;
    }

    // ======================== Event Hooks ========================

    /**
     * ClientTickEvent — handles advanced target tracking, misplace updates,
     * and position restore cycle each game tick.
     */
    @Override
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START || !isEnabled()) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null || mc.theWorld == null) return;

        // Advanced mode: track target and evaluate reach chance
        if (chanceMode.getValue() == ChanceMode.ADVANCED) {
            updateTarget(mc);
        }

        // Misplace engine: update displaced positions
        if (misplace.isEnabled()) {
            updateMisplacedPositions(mc);
            applyMisplace(mc, false);
        }
    }

    /**
     * RenderTickEvent — apply/restore misplaced positions around render frames
     * so that the client sees the displaced entity positions during rendering.
     */
    @Override
    public void onRenderTick(TickEvent.RenderTickEvent event) {
        if (!isEnabled() || !misplace.isEnabled()) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null || mc.theWorld == null) return;

        if (event.phase == TickEvent.Phase.START) {
            // Pre-render: apply misplaced positions with historical interpolation
            applyMisplace(mc, true);
        } else {
            // Post-render: restore actual positions
            restorePositions(mc, true);
        }
    }

    /**
     * MouseEvent — intercept left-click attacks for extended reach raytrace.
     * Only fires when misplace is OFF (misplace handles reach via displacement).
     */
    @Override
    public void onMouseEvent(MouseEvent event) {
        if (misplace.isEnabled()) return;

        Minecraft mc = Minecraft.getMinecraft();
        if (event.button != 0 || !event.buttonstate) return;
        if (MouseButtonHelper.isDispatchingSyntheticEvent()) return;
        if (mc.thePlayer == null || mc.theWorld == null || mc.currentScreen != null || !mc.inGameHasFocus) return;
        if (!isReachAllowed(mc)) return;

        double reachDistance = getReachDistance();
        if (reachDistance <= 3.0) return;

        Entity target = rayTraceEntity(mc, reachDistance);
        if (!(target instanceof EntityLivingBase) || target == mc.thePlayer) return;
        if (!TargetFilter.isValidTarget(target)) return;

        double distance = mc.thePlayer.getDistanceToEntity(target);
        if (distance <= 3.0 || distance > reachDistance) return;

        // Vertical check: prevent hitting entities too far above/below
        if (verticalCheck.isEnabled()) {
            double yDelta = Math.abs(target.posY - mc.thePlayer.posY);
            if (yDelta >= 0.2) return;
        }

        // Advanced mode: use pre-evaluated reachActive flag
        if (chanceMode.getValue() == ChanceMode.ADVANCED && !reachActive) return;

        // Normal mode: roll chance on each attack
        if (chanceMode.getValue() == ChanceMode.NORMAL) {
            if (random.nextInt(100) >= chance.getValue()) return;
        }

        // Hijack objectMouseOver + pointedEntity to the extended-range target and
        // let vanilla clickMouse() perform the attack+swing using the extended
        // hit vector (Sakura/raven ghost-reach pattern). Calling
        // playerController.attackEntity() directly would emit a C02 whose range
        // is validated by the server against the target's *server* position,
        // causing Matrix/Grim/Polar "attack range" flags. We must NOT cancel
        // the event — vanilla clickMouse must run to fire the attack naturally.
        Vec3 eye = mc.thePlayer.getPositionEyes(1.0F);
        MovingObjectPosition mop = getHitVec(mc, target, reachDistance, eye);
        Vec3 hitVec = mop != null ? mop.hitVec : target.getPositionEyes(1.0F);
        mc.objectMouseOver = new MovingObjectPosition(target, hitVec);
        mc.pointedEntity = target;

        // Consume the advanced-mode activation so we don't re-fire every tick
        // during the 10-tick cooldown window.
        reachActive = false;
        return;
    }

    /**
     * Computes the precise hit vector on the target's bounding box along the
     * extended reach ray, so the resulting C02 attack packet carries a hitVec
     * that lands within the extended reach distance (server trusts client hitVec).
     */
    private MovingObjectPosition getHitVec(Minecraft mc, Entity target, double reachDistance, Vec3 eye) {
        Vec3 look = mc.thePlayer.getLook(1.0F);
        Vec3 end = eye.addVector(look.xCoord * reachDistance, look.yCoord * reachDistance, look.zCoord * reachDistance);
        float border = target.getCollisionBorderSize();
        AxisAlignedBB bb = target.getEntityBoundingBox().expand(border, border, border);
        return bb.calculateIntercept(eye, end);
    }

    // ======================== Core Logic ========================

    /**
     * Determines if reach extension is currently allowed based on
     * safety settings (sprint, water, weapon).
     */
    private boolean isReachAllowed(Minecraft mc) {
        if (!isEnabled()) return false;
        if (misplace.isEnabled()) return false;

        if (disableInWater.isEnabled() && (mc.thePlayer.isInWater() || mc.thePlayer.isInLava())) {
            return false;
        }

        if (onlyWhileSprinting.isEnabled() && !mc.thePlayer.isSprinting()) {
            return false;
        }

        if (weaponOnly.isEnabled()) {
            ItemStack held = mc.thePlayer.getHeldItem();
            if (held == null || (!(held.getItem() instanceof ItemSword) && !(held.getItem() instanceof ItemAxe))) {
                return false;
            }
        }

        return true;
    }

    /**
     * Returns a randomized reach distance between minReach and maxReach.
     * Returns vanilla reach (3.0) if reach extension is not allowed.
     */
    private double getReachDistance() {
        double min = minReach.getValue();
        double max = maxReach.getValue();
        if (min >= max) return min;
        return min + random.nextDouble() * (max - min);
    }

    /**
     * Advanced mode target tracking with hit cooldown.
     * Mimics Vape's updateTarget() — raytrace at max reach distance each tick,
     * validate target continuity, evaluate chance on cooldown expiry.
     */
    private void updateTarget(Minecraft mc) {
        Entity tracedEntity = rayTraceEntity(mc, maxReach.getValue());

        if (hitCooldown > 0) {
            --hitCooldown;
        }

        // If no target or target changed, reset
        if (tracedEntity == null || (lastTarget != null && !tracedEntity.equals(lastTarget))) {
            lastTarget = null;
            reachActive = false;
            return;
        }

        // Evaluate reach activation on cooldown expiry
        if (isReachAllowedAdvanced(mc) && hitCooldown == 0) {
            reachActive = random.nextInt(100) < chance.getValue();
            if (reachActive) {
                hitCooldown = 10; // 10-tick cooldown between reach activations
            }
        }

        lastTarget = tracedEntity;
    }

    /**
     * Safety check variant for advanced mode (misplace is always off here).
     */
    private boolean isReachAllowedAdvanced(Minecraft mc) {
        if (!isEnabled()) return false;

        if (disableInWater.isEnabled() && (mc.thePlayer.isInWater() || mc.thePlayer.isInLava())) {
            return false;
        }

        if (onlyWhileSprinting.isEnabled() && !mc.thePlayer.isSprinting()) {
            return false;
        }

        if (weaponOnly.isEnabled()) {
            ItemStack held = mc.thePlayer.getHeldItem();
            if (held == null || (!(held.getItem() instanceof ItemSword) && !(held.getItem() instanceof ItemAxe))) {
                return false;
            }
        }

        return true;
    }

    // ======================== Raytrace ========================

    /**
     * Performs an extended entity raytrace from the player's eye position
     * along the look vector up to the specified reach distance.
     */
    private Entity rayTraceEntity(Minecraft mc, double reachDistance) {
        EntityPlayer player = mc.thePlayer;
        Vec3 eyes = player.getPositionEyes(1.0F);
        Vec3 look = player.getLook(1.0F);
        Vec3 reachVector = eyes.addVector(
                look.xCoord * reachDistance,
                look.yCoord * reachDistance,
                look.zCoord * reachDistance
        );

        Entity pointedEntity = null;
        double bestDistance = reachDistance;

        List<?> entities = mc.theWorld.getEntitiesWithinAABBExcludingEntity(
                player,
                player.getEntityBoundingBox()
                        .addCoord(look.xCoord * reachDistance, look.yCoord * reachDistance, look.zCoord * reachDistance)
                        .expand(1.0, 1.0, 1.0)
        );

        for (Object object : entities) {
            if (!(object instanceof Entity)) continue;
            Entity entity = (Entity) object;
            if (!entity.canBeCollidedWith() || entity == player) continue;

            float border = entity.getCollisionBorderSize();
            AxisAlignedBB box = entity.getEntityBoundingBox().expand(border, border, border);
            MovingObjectPosition intercept = box.calculateIntercept(eyes, reachVector);

            if (box.isVecInside(eyes)) {
                if (bestDistance >= 0.0) {
                    pointedEntity = entity;
                    bestDistance = 0.0;
                }
                continue;
            }

            if (intercept == null) continue;

            double distance = eyes.distanceTo(intercept.hitVec);
            if (distance < bestDistance || bestDistance == 0.0) {
                pointedEntity = entity;
                bestDistance = distance;
            }
        }

        return pointedEntity;
    }

    // ======================== Misplace Engine ========================

    /**
     * Computes the angle from source to target in XZ plane (degrees).
     * Handles all quadrant cases for correct perpendicular offset direction.
     */
    private float computeAngle(double sourceX, double sourceZ, double targetX, double targetZ) {
        double deltaX = targetX - sourceX;
        double deltaZ = targetZ - sourceZ;
        float angle = (float) Math.toDegrees(-Math.atan(deltaX / deltaZ));

        if (deltaZ < 0.0 && deltaX < 0.0) {
            angle = (float) (90.0 + Math.toDegrees(Math.atan(deltaZ / deltaX)));
        } else if (deltaZ < 0.0 && deltaX > 0.0) {
            angle = (float) (-90.0 + Math.toDegrees(Math.atan(deltaZ / deltaX)));
        }

        return angle;
    }

    /**
     * Updates all tracked entity misplaced positions.
     * Calculates perpendicular displacement offset based on angle to player
     * and configured reach extension distance.
     */
    private void updateMisplacedPositions(Minecraft mc) {
        EntityPlayer player = mc.thePlayer;
        float directionOffset = disadvantage.isEnabled() ? -90.0f : 90.0f;

        for (Object obj : mc.theWorld.loadedEntityList) {
            if (!(obj instanceof EntityOtherPlayerMP)) continue;
            EntityOtherPlayerMP remotePlayer = (EntityOtherPlayerMP) obj;
            if (remotePlayer == player) continue;

            double misplaceDistance = minReach.getValue() - 3.0;
            double distanceToPlayer = Math.hypot(player.posX - remotePlayer.posX, player.posZ - remotePlayer.posZ);
            float angle = computeAngle(player.posX, player.posZ, remotePlayer.posX, remotePlayer.posZ);

            // Clamp misplace distance when entities are close to prevent overlap
            double remainingDistance = distanceToPlayer - misplaceDistance;
            if (remainingDistance < 0.5) {
                misplaceDistance += remainingDistance - 0.5;
                if (misplaceDistance < 0.0) misplaceDistance = 0.0;
            }

            double offsetX = Math.cos(Math.toRadians(angle + directionOffset)) * misplaceDistance;
            double offsetZ = Math.sin(Math.toRadians(angle + directionOffset)) * misplaceDistance;

            int entityId = remotePlayer.getEntityId();
            EntityPositionState state = entityStates.get(entityId);
            boolean hadPrevious = state != null;

            if (!hadPrevious) {
                state = new EntityPositionState();
                state.entityId = entityId;
            }

            entityStates.put(entityId, state);

            // Store actual positions
            state.actualPosX = remotePlayer.posX;
            state.actualPosZ = remotePlayer.posZ;
            state.actualLastTickPosX = remotePlayer.lastTickPosX;
            state.actualLastTickPosZ = remotePlayer.lastTickPosZ;
            state.actualPrevPosX = remotePlayer.prevPosX;
            state.actualPrevPosZ = remotePlayer.prevPosZ;
            state.actualServerPosX = remotePlayer.serverPosX;
            state.actualServerPosZ = remotePlayer.serverPosZ;

            // Compute misplaced positions
            state.misplacedPosX = state.actualPosX - offsetX;
            state.misplacedPosZ = state.actualPosZ - offsetZ;
            state.misplacedLastTickPosX = state.actualLastTickPosX - offsetX;
            state.misplacedLastTickPosZ = state.actualLastTickPosZ - offsetZ;
            state.misplacedPrevPosX = state.actualPrevPosX - offsetX;
            state.misplacedPrevPosZ = state.actualPrevPosZ - offsetZ;

            // Smooth interpolation: use previous frame's misplaced position for lastTickPos
            if (hadPrevious) {
                state.misplacedLastTickPosX = state.previousMisplacedLastTickPosX;
                state.misplacedLastTickPosZ = state.previousMisplacedLastTickPosZ;
            }
        }
    }

    /**
     * Applies misplaced positions to entities for client-side rendering/collision.
     * @param includeHistorical true during render frames (updates lastTickPos, prevPos)
     */
    private void applyMisplace(Minecraft mc, boolean includeHistorical) {
        for (Object obj : mc.theWorld.loadedEntityList) {
            if (!(obj instanceof EntityOtherPlayerMP)) continue;
            EntityOtherPlayerMP remotePlayer = (EntityOtherPlayerMP) obj;
            if (remotePlayer.getEntityId() == mc.thePlayer.getEntityId()) continue;

            int entityId = remotePlayer.getEntityId();
            EntityPositionState state = entityStates.get(entityId);
            if (state == null) continue;

            // Store actual positions before applying misplace
            state.actualPosX = remotePlayer.posX;
            state.actualPosZ = remotePlayer.posZ;
            state.actualLastTickPosX = remotePlayer.lastTickPosX;
            state.actualLastTickPosZ = remotePlayer.lastTickPosZ;
            state.actualPrevPosX = remotePlayer.prevPosX;
            state.actualPrevPosZ = remotePlayer.prevPosZ;
            state.actualServerPosX = remotePlayer.serverPosX;
            state.actualServerPosZ = remotePlayer.serverPosZ;

            // Apply displaced positions
            remotePlayer.posX = state.misplacedPosX;
            remotePlayer.posZ = state.misplacedPosZ;

            if (includeHistorical) {
                remotePlayer.lastTickPosX = state.misplacedLastTickPosX;
                remotePlayer.lastTickPosZ = state.misplacedLastTickPosZ;
                remotePlayer.prevPosX = state.misplacedPrevPosX;
                remotePlayer.prevPosZ = state.misplacedPrevPosZ;
            }

            // Update entity bounding box to match misplaced position
            remotePlayer.setPosition(remotePlayer.posX, remotePlayer.posY, remotePlayer.posZ);

            // Restore X/Z to actual for server-side consistency (bounding box is now displaced)
            if (!includeHistorical) {
                remotePlayer.posX = state.actualPosX;
                remotePlayer.posZ = state.actualPosZ;
            }
        }
    }

    /**
     * Restores all entity positions to their actual server-synced values.
     * @param restoreHistorical true during render frames
     */
    private void restorePositions(Minecraft mc, boolean restoreHistorical) {
        for (Map.Entry<Integer, EntityPositionState> entry : entityStates.entrySet()) {
            EntityPositionState state = entry.getValue();
            Entity entity = mc.theWorld.getEntityByID(state.entityId);
            if (!(entity instanceof EntityOtherPlayerMP)) continue;

            EntityOtherPlayerMP remotePlayer = (EntityOtherPlayerMP) entity;

            if (restoreHistorical) {
                // Store current misplaced position for next frame's lastTickPos interpolation
                state.previousMisplacedLastTickPosX = state.misplacedPosX;
                state.previousMisplacedLastTickPosZ = state.misplacedPosZ;

                remotePlayer.posX = state.actualPosX;
                remotePlayer.posZ = state.actualPosZ;
                remotePlayer.lastTickPosX = state.actualLastTickPosX;
                remotePlayer.lastTickPosZ = state.actualLastTickPosZ;
                remotePlayer.prevPosX = state.actualPrevPosX;
                remotePlayer.prevPosZ = state.actualPrevPosZ;
            }

            // Reset bounding box to actual position
            remotePlayer.setPosition(remotePlayer.posX, remotePlayer.posY, remotePlayer.posZ);
        }
    }

    /**
     * Full position restore on disable — ensures no entities are left displaced.
     */
    private void restoreAllPositions() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.theWorld == null) return;

        for (Map.Entry<Integer, EntityPositionState> entry : entityStates.entrySet()) {
            EntityPositionState state = entry.getValue();
            Entity entity = mc.theWorld.getEntityByID(state.entityId);
            if (!(entity instanceof EntityOtherPlayerMP)) continue;

            EntityOtherPlayerMP remotePlayer = (EntityOtherPlayerMP) entity;
            remotePlayer.posX = state.actualPosX;
            remotePlayer.posZ = state.actualPosZ;
            remotePlayer.lastTickPosX = state.actualLastTickPosX;
            remotePlayer.lastTickPosZ = state.actualLastTickPosZ;
            remotePlayer.prevPosX = state.actualPrevPosX;
            remotePlayer.prevPosZ = state.actualPrevPosZ;
            remotePlayer.setPosition(remotePlayer.posX, remotePlayer.posY, remotePlayer.posZ);
        }
    }

    // ======================== Entity Position State ========================

    /**
     * Tracks both actual and misplaced coordinate state for a single entity.
     * Used by the misplace engine to swap positions around render/tick frames.
     */
    private static final class EntityPositionState {
        int entityId;

        // Actual server-synced positions
        double actualPosX, actualPosZ;
        double actualLastTickPosX, actualLastTickPosZ;
        double actualPrevPosX, actualPrevPosZ;
        double actualServerPosX, actualServerPosZ;

        // Client-displaced positions
        double misplacedPosX, misplacedPosZ;
        double misplacedLastTickPosX, misplacedLastTickPosZ;
        double misplacedPrevPosX, misplacedPrevPosZ;

        // Previous frame's misplaced position for smooth interpolation
        double previousMisplacedLastTickPosX, previousMisplacedLastTickPosZ;
    }
}
