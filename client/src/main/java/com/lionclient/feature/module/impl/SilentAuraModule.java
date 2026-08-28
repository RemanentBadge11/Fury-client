package com.lionclient.feature.module.impl;

import com.lionclient.combat.ClientRotationHelper;
import com.lionclient.event.ClientRotationEvent;
import com.lionclient.event.SendPacketEvent;
import com.lionclient.feature.module.Category;
import com.lionclient.feature.module.Module;
import com.lionclient.feature.setting.*;
import com.lionclient.util.ClickPattern;
import com.lionclient.util.TargetFilter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.boss.EntityDragon;
import net.minecraft.entity.boss.EntityWither;
import net.minecraft.entity.monster.EntityIronGolem;
import net.minecraft.entity.monster.EntityMob;
import net.minecraft.entity.monster.EntitySilverfish;
import net.minecraft.entity.monster.EntitySlime;
import net.minecraft.entity.passive.EntityAnimal;
import net.minecraft.entity.passive.EntityBat;
import net.minecraft.entity.passive.EntitySquid;
import net.minecraft.entity.passive.EntityVillager;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemSword;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.client.*;
import net.minecraft.network.Packet;
import net.minecraft.util.*;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.ReflectionHelper;
import org.lwjgl.input.Keyboard;

import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * SilentAura — OpenMyau+ 1:1 silent combat aura with LionClient's ClickPattern CPS engine.
 * Uses tick-based 20-slot attack pattern for human-like CPS distribution that bypasses
 * anti-cheat CPS analysis (Polar, Grim, Matrix).
 */
public final class SilentAuraModule extends Module {

    public enum CpsMode { Normal, Record, Randomizer, Pattern }
    public enum SortMode { Distance, Health, HurtTime, FOVAngle }
    public enum RotationMode { NONE, Silent, LiquidBounce, Godly, PI_CONTROLLER }
    public enum MoveFixMode { NONE, Silent, Strict }
    public enum AutoBlockMode { NONE, VANILLA, SPOOF, HYPIXEL }
    public enum HudPosMode { TOP_LEFT, TOP_RIGHT, BOT_LEFT, BOT_RIGHT }
    public enum VisualizerStyleMode { Box, Crosshair, Both }
    public enum TargetArea { CENTER, CLOSEST }

    // HitFlick States
    private enum FlickState { IDLE, FLICKING_AWAY, ATTACKING }

    // ======================== SETTINGS ========================
    private final EnumSetting<CpsMode> cpsMode = new EnumSetting<>("CPS Mode", CpsMode.values(), CpsMode.Normal);
    private final DecimalSetting attackRange = new DecimalSetting("AttackRange", 3.0, 6.0, 0.1, 3.0);
    private final DecimalSetting swingRange = new DecimalSetting("SwingRange", 3.0, 6.0, 0.1, 3.5);
    private final DecimalSetting autoBlockRange = new DecimalSetting("AutoBlockRange", 3.0, 8.0, 0.1, 5.0);
    private final BooleanSetting autoRange = new BooleanSetting("AutoRange", false);
    private final DecimalSetting maxReach = new DecimalSetting("MaxReach", 3.0, 6.0, 0.1, 4.2);

    private final NumberSetting minCPS = new NumberSetting("MinCPS", 8, 16, 1, 10);
    private final NumberSetting maxCPS = new NumberSetting("MaxCPS", 10, 18, 1, 14);
    private final NumberSetting fov = new NumberSetting("FOV", 30, 360, 1, 360);
    private final BooleanSetting aimCheck = new BooleanSetting("AimCheck", false);
    private final DecimalSetting aimCheckFOV = new DecimalSetting("AimCheckFOV", 10.0, 180.0, 1.0, 45.0);

    private final EnumSetting<SortMode> sort = new EnumSetting<>("Sort", SortMode.values(), SortMode.Distance);
    private final DecimalSetting minHealth = new DecimalSetting("MinHealth", 0.0, 36.0, 1.0, 0.0);
    private final DecimalSetting maxHealth = new DecimalSetting("MaxHealth", 0.0, 36.0, 1.0, 36.0);

    private final BooleanSetting throughWalls = new BooleanSetting("ThroughWalls", true);
    private final BooleanSetting throughWallsBlock = new BooleanSetting("ThroughWallsBlock", false);
    private final BooleanSetting players = new BooleanSetting("Players", true);
    private final BooleanSetting bosses = new BooleanSetting("Bosses", false);
    private final BooleanSetting mobs = new BooleanSetting("Mobs", false);
    private final BooleanSetting animals = new BooleanSetting("Animals", false);
    private final BooleanSetting golems = new BooleanSetting("Golems", false);
    private final BooleanSetting silverfishSetting = new BooleanSetting("Silverfish", false);
    private final BooleanSetting teams = new BooleanSetting("Teams", true);
    private final BooleanSetting botCheck = new BooleanSetting("BotCheck", true);

    private final BooleanSetting requirePress = new BooleanSetting("RequirePress", false);
    private final BooleanSetting weaponsOnly = new BooleanSetting("WeaponsOnly", true);
    private final BooleanSetting allowTools = new BooleanSetting("AllowTools", false);
    private final BooleanSetting allowMining = new BooleanSetting("AllowMining", true);
    private final BooleanSetting inventoryCheck = new BooleanSetting("InventoryCheck", true);

    private final EnumSetting<RotationMode> rotations = new EnumSetting<>("Rotations", RotationMode.values(), RotationMode.Silent);
    private final NumberSetting smoothing = new NumberSetting("Smoothing", 0, 100, 1, 0);
    private final NumberSetting angleStep = new NumberSetting("AngleStep", 30, 180, 5, 90);
    private final BooleanSetting smoothBack = new BooleanSetting("SmoothBack", true);
    private final EnumSetting<MoveFixMode> moveFix = new EnumSetting<>("MoveFix", MoveFixMode.values(), MoveFixMode.Silent);

    private final DecimalSetting maxTurnSpeed = new DecimalSetting("MaxSpeed", 5.0, 180.0, 1.0, 25.0);
    private final DecimalSetting minTurnSpeed = new DecimalSetting("MinSpeed", 1.0, 90.0, 1.0, 5.0);
    private final DecimalSetting smoothFactor = new DecimalSetting("SmoothFactor", 0.1, 1.0, 0.05, 0.5);
    private final BooleanSetting predictMotion = new BooleanSetting("Predict", true);
    private final DecimalSetting predictSize = new DecimalSetting("PredictSize", 0.0, 3.0, 0.1, 1.0);
    private final BooleanSetting predictRandomize = new BooleanSetting("PredictRandomize", true);
    private final DecimalSetting predictRandomRange = new DecimalSetting("PredictRandRange", 0.0, 1.0, 0.05, 0.3);

    private final DecimalSetting bodyMin = new DecimalSetting("BodyMin", 0.0, 1.0, 0.05, 0.1);
    private final DecimalSetting bodyMax = new DecimalSetting("BodyMax", 0.0, 1.0, 0.05, 0.9);
    private final DecimalSetting hSearch = new DecimalSetting("HSearch", 0.0, 1.0, 0.05, 0.5);

    private final EnumSetting<AutoBlockMode> autoBlock = new EnumSetting<>("AutoBlock", AutoBlockMode.values(), AutoBlockMode.NONE);
    private final BooleanSetting noSlowSpoof = new BooleanSetting("NoSlowSpoof", true);
    private final BooleanSetting cpsDrift = new BooleanSetting("CPSDrift", true);
    private final BooleanSetting contextAware = new BooleanSetting("ContextAware", true);

    // PI Controller Settings
    private final EnumSetting<TargetArea> targetArea = new EnumSetting<>("Target Area", TargetArea.values(), TargetArea.CLOSEST);
    private final NumberSetting aimSpeed = new NumberSetting("Aim Speed", 1, 10, 1, 7);

    // HitFlick Settings
    private final BooleanSetting hitFlick = new BooleanSetting("HitFlick", true);
    private final NumberSetting flickAngle = new NumberSetting("Flick Angle", 0, 360, 1, 180);
    private final NumberSetting flickChance = new NumberSetting("Flick Chance", 0, 100, 1, 100);
    private final NumberSetting flickDelay = new NumberSetting("Flick Delay", 0, 2000, 25, 200);
    private final BooleanSetting randomizeFlick = new BooleanSetting("Randomize Flick", true);
    private final NumberSetting randomizeFlickRange = new NumberSetting("Flick Range", 0, 180, 1, 10);
    private final BooleanSetting strafeInvert = new BooleanSetting("Strafe Invert", false);
    private final BooleanSetting blinkFlick = new BooleanSetting("Blink", true);

    private final BooleanSetting visualizeAim = new BooleanSetting("VisualizeAim", true);
    private final EnumSetting<VisualizerStyleMode> visualizerStyle = new EnumSetting<>("VisualizerStyle", VisualizerStyleMode.values(), VisualizerStyleMode.Box);
    private final BooleanSetting showTargetHUD = new BooleanSetting("ShowTargetHUD", true);
    private final EnumSetting<HudPosMode> hudPosition = new EnumSetting<>("HUDPos", HudPosMode.values(), HudPosMode.TOP_LEFT);
    private final DecimalSetting hudScale = new DecimalSetting("HUDScale", 0.5, 2.0, 0.1, 1.0);
    private final BooleanSetting showTargetBox = new BooleanSetting("ShowTargetBox", true);

    private final EnumSetting<AutoClickerModule.Randomization> randomization =
            new EnumSetting<>("Randomization", AutoClickerModule.Randomization.values(), AutoClickerModule.Randomization.EXTRA);
    private final NumberSetting randomStrength = new NumberSetting("Random Strength", 0, 100, 1, 35);

    // ======================== STATE ========================
    private EntityLivingBase target = null;
    public EntityLivingBase getTarget() { return target; }
    private AxisAlignedBB targetBox = null;
    private Vec3 currentAimVec = null;

    private float serverYaw = 0.0f;
    private float serverPitch = 0.0f;
    private boolean isBlockingState = false;

    // Click pattern engine — same as LeftClicker uses for human-like CPS
    private final ClickPattern clickPattern = ClickPattern.create();

    // Motion prediction history (OpenMyau style)
    private final float[] pastMotionX = new float[5];
    private final float[] pastMotionY = new float[5];
    private final float[] pastMotionZ = new float[5];
    private int motionHistoryCount = 0;

    // HitFlick State
    private FlickState flickState = FlickState.IDLE;
    private int flickStateTicks = 0;
    private boolean flickAttackQueued = false;
    private boolean flickAttacked = false;
    private float currentFlickAngle = 0.0f;
    private long lastFlickTime = 0L;
    private boolean isBlinking = false;
    private int blinkTicks = 0;
    private final Queue<Packet<?>> heldPackets = new LinkedList<>();

    private float flickSnapYaw = 0.0f;
    private float flickSnapPitch = 0.0f;
    
    // PI Controller & Jitter State
    private float yawIntegral = 0.0f;
    private float pitchIntegral = 0.0f;
    private float pitchProportionalScale = 1.0f;
    private float pitchIntegralScale = 1.0f;
    private float yawProportionalScale = 1.0f;
    private float yawIntegralScale = 1.0f;
    
    private double jitterXCurrent = 0.0;
    private double jitterXTarget = 0.0;
    private long lastJitterXTime = 0L;
    
    private double jitterYCurrent = 0.0;
    private double jitterYTarget = 0.0;
    private long lastJitterYTime = 0L;
    
    private double jitterZCurrent = 0.0;
    private double jitterZTarget = 0.0;
    private long lastJitterZTime = 0L;

    // Target switch cooldown
    private long lastTargetSwitchTime = 0L;
    private int targetSwitchCount = 0;

    private long fightStartTime = 0L;
    private double fatigueAccum = 0.0;

    private boolean forgeRegistered = false;

    private static final Field leftClickCounterField = findLeftClickCounterField();

    public SilentAuraModule() {
        super("SilentAura", "OpenMyau+ 1:1 Silent Aura with ClickPattern CPS", Category.COMBAT, Keyboard.KEY_NONE);

        addSetting(cpsMode);
        addSetting(randomization);
        addSetting(randomStrength);
        addSetting(attackRange);
        addSetting(swingRange);
        addSetting(autoBlockRange);
        addSetting(autoRange);
        addSetting(maxReach);
        addSetting(minCPS);
        addSetting(maxCPS);
        addSetting(fov);
        addSetting(aimCheck);
        addSetting(aimCheckFOV);
        addSetting(sort);
        addSetting(minHealth);
        addSetting(maxHealth);
        addSetting(throughWalls);
        addSetting(throughWallsBlock);
        addSetting(players);
        addSetting(bosses);
        addSetting(mobs);
        addSetting(animals);
        addSetting(golems);
        addSetting(silverfishSetting);
        addSetting(teams);
        addSetting(botCheck);
        addSetting(requirePress);
        addSetting(weaponsOnly);
        addSetting(allowTools);
        addSetting(allowMining);
        addSetting(inventoryCheck);
        addSetting(rotations);
        addSetting(smoothing);
        addSetting(angleStep);
        addSetting(smoothBack);
        addSetting(moveFix);
        addSetting(maxTurnSpeed);
        addSetting(minTurnSpeed);
        addSetting(smoothFactor);
        addSetting(predictMotion);
        addSetting(predictSize);
        addSetting(predictRandomize);
        addSetting(predictRandomRange);
        addSetting(bodyMin);
        addSetting(bodyMax);
        addSetting(hSearch);
        addSetting(autoBlock);
        addSetting(noSlowSpoof);
        addSetting(cpsDrift);
        addSetting(contextAware);
        addSetting(targetArea);
        addSetting(aimSpeed);
        addSetting(hitFlick);
        addSetting(flickAngle);
        addSetting(flickChance);
        addSetting(flickDelay);
        addSetting(randomizeFlick);
        addSetting(randomizeFlickRange);
        addSetting(strafeInvert);
        addSetting(blinkFlick);
        addSetting(visualizeAim);
        addSetting(visualizerStyle);
        addSetting(showTargetHUD);
        addSetting(hudPosition);
        addSetting(hudScale);
        addSetting(showTargetBox);
    }

    @Override
    protected void onEnable() {
        Minecraft mc = Minecraft.getMinecraft();
        target = null;
        targetBox = null;
        currentAimVec = null;
        isBlockingState = false;
        motionHistoryCount = 0;
        lastTargetSwitchTime = 0L;
        targetSwitchCount = 0;
        fightStartTime = 0L;
        fatigueAccum = 0.0;

        if (mc.thePlayer != null) {
            serverYaw = mc.thePlayer.rotationYaw;
            serverPitch = mc.thePlayer.rotationPitch;
        }

        yawIntegral = 0.0f;
        pitchIntegral = 0.0f;
        flickState = FlickState.IDLE;
        heldPackets.clear();
        isBlinking = false;
        flickStateTicks = 0;
        randomizePIGains();

        // Initialize click pattern
        normalizeRanges();
        clickPattern.reconfigure(
            minCPS.getValue(),
            maxCPS.getValue(),
            randomStrength.getValue(),
            mapTechnique()
        );

        registerForge();
    }

    private void normalizeRanges() {
        if (maxCPS.getValue() < minCPS.getValue()) {
            maxCPS.setManualValue(minCPS.getValue());
        }
    }

    @Override
    protected void onDisable() {
        unregisterForge();
        if (isBlockingState) {
            stopBlocking();
        }
        target = null;
        targetBox = null;
        currentAimVec = null;
        motionHistoryCount = 0;
        flushBlinkPackets();
        flickState = FlickState.IDLE;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer != null) {
            serverYaw = mc.thePlayer.rotationYaw;
            serverPitch = mc.thePlayer.rotationPitch;
        }
    }

    private synchronized void registerForge() {
        if (!forgeRegistered) {
            try {
                MinecraftForge.EVENT_BUS.register(this);
                forgeRegistered = true;
            } catch (Throwable ignored) {}
        }
    }

    private synchronized void unregisterForge() {
        if (forgeRegistered) {
            try {
                MinecraftForge.EVENT_BUS.unregister(this);
            } catch (Throwable ignored) {}
            forgeRegistered = false;
        }
    }

    // ======================== MAIN TICK LOOP ========================

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START || !isEnabled()) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null || mc.theWorld == null) {
            target = null;
            return;
        }

        // 1. Target selection
        updateTargetSelection(mc);

        if (target != null) {
            if (fightStartTime == 0L) {
                fightStartTime = System.currentTimeMillis();
                fatigueAccum = 0.0;
            }
            double border = target.getCollisionBorderSize();
            targetBox = target.getEntityBoundingBox().expand(border, border, border);
            updateTargetMotionHistory(target);
        } else {
            fightStartTime = 0L;
            targetBox = null;
            currentAimVec = null;
            motionHistoryCount = 0;
            return;
        }

        // 2. Compute rotations
        computeRotations(mc);

        // 3. Tick HitFlick State Machine
        tickHitFlick(mc);

        // 4. AutoBlock (pre-attack)
        handleAutoBlock(mc);

        // 5. ClickPattern tick-based attack (if not flicking)
        if (flickState == FlickState.IDLE) {
            executeTickAttacks(mc);
        }

        // 6. AutoBlock (post-attack re-block)
        if (isBlockingState && autoBlock.getValue() != AutoBlockMode.NONE && isHoldingSword(mc)) {
            startBlocking(mc);
        }
    }

    // ======================== CLICK PATTERN ATTACK ENGINE ========================
    // Uses ClickPattern from LeftClicker: 20-slot boolean array per second,
    // shuffled for human-like distribution. ExtraPlus adds fatigue pauses and bursts.

    private void executeTickAttacks(Minecraft mc) {
        if (target == null || targetBox == null) return;

        double distToBB = getDistanceToBoundingBox(mc, target);
        double effectiveRange = getDynamicRange(mc);
        boolean inAttackRange = distToBB <= effectiveRange;
        boolean inSwingRange = distToBB <= swingRange.getValue();

        if (!isInFov(mc, target)) return;

        if (requirePress.isEnabled()) {
            int keyCode = mc.gameSettings.keyBindAttack.getKeyCode();
            boolean pressed = keyCode < 0
                ? org.lwjgl.input.Mouse.isButtonDown(keyCode + 100)
                : Keyboard.isKeyDown(keyCode);
            if (!pressed) return;
        }

        if (weaponsOnly.isEnabled() && !isHoldingValidWeapon(mc)) return;

        if (inventoryCheck.isEnabled() && mc.currentScreen != null) return;

        // Check if pattern needs reconfiguration (every 20 ticks = 1 second)
        if (clickPattern.check()) {
            normalizeRanges();
            clickPattern.reconfigure(
                minCPS.getValue(),
                maxCPS.getValue(),
                randomStrength.getValue(),
                mapTechnique()
            );
        }

        // Ask the pattern: should we attack this tick?
        boolean shouldAttack = clickPattern.nextAttack();
        if (!shouldAttack) return;

        // Reset leftClickCounter so MC doesn't throttle our attacks
        if (leftClickCounterField != null && !mc.thePlayer.capabilities.isCreativeMode) {
            try { leftClickCounterField.setInt(mc, 0); } catch (Throwable ignored) {}
        }

        // Unblock before attacking
        if (isBlockingState && autoBlock.getValue() != AutoBlockMode.NONE
                && autoBlock.getValue() != AutoBlockMode.VANILLA) {
            stopBlocking();
        }

        // Check if HitFlick should engage instead of normal attack
        if (hitFlick.isEnabled() && flickState == FlickState.IDLE) {
            if (flickDelayTimerHasElapsed() && ThreadLocalRandom.current().nextDouble(0, 100) <= flickChance.getValue()) {
                startHitFlick(mc);
                return; // Attack will happen inside the Flick loop
            }
        }

        // RayTrace sanity check for PI Controller (only attack if crosshair intersects bounds)
        if (rotations.getValue() == RotationMode.PI_CONTROLLER) {
            if (!isLookingAtTarget(mc, target)) {
                return;
            }
        }

        if (inSwingRange) {
            mc.thePlayer.swingItem();
        }

        if (inAttackRange) {
            if (!throughWalls.isEnabled() && !hasLineOfSight(mc, target)) {
                return;
            }
            mc.playerController.attackEntity(mc.thePlayer, target);
            CPSModule.addLeftClick();
        }
    }

    // ======================== HITFLICK MECHANICS ========================
    
    private void startHitFlick(Minecraft mc) {
        flickState = FlickState.FLICKING_AWAY;
        flickStateTicks = 0;
        flickAttackQueued = true;
        flickAttacked = false;
        
        float baseAngle = (float) flickAngle.getValue();
        if (randomizeFlick.isEnabled()) {
            float offsetRange = (float) randomizeFlickRange.getValue();
            float randOffset = (ThreadLocalRandom.current().nextFloat() - 0.5f) * offsetRange;
            baseAngle += randOffset;
        }
        
        if (strafeInvert.isEnabled()) {
            boolean left = Keyboard.isKeyDown(mc.gameSettings.keyBindLeft.getKeyCode());
            boolean right = Keyboard.isKeyDown(mc.gameSettings.keyBindRight.getKeyCode());
            if (left && !right) {
                baseAngle = -baseAngle;
            }
        }
        
        currentFlickAngle = baseAngle;
        lastFlickTime = System.currentTimeMillis();
        
        if (blinkFlick.isEnabled()) {
            isBlinking = true;
            blinkTicks = 0;
        }
    }
    
    private boolean flickDelayTimerHasElapsed() {
        return (System.currentTimeMillis() - lastFlickTime) > flickDelay.getValue();
    }
    
    private void flushBlinkPackets() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.getNetHandler() != null && !heldPackets.isEmpty()) {
            for (Packet<?> p : heldPackets) {
                mc.getNetHandler().getNetworkManager().sendPacket(p);
            }
            heldPackets.clear();
        }
        isBlinking = false;
        blinkTicks = 0;
    }
    
    private void tickHitFlick(Minecraft mc) {
        if (flickState == FlickState.IDLE) return;
        
        if (target == null || targetBox == null) {
            flickState = FlickState.IDLE;
            flushBlinkPackets();
            return;
        }
        
        flickStateTicks++;
        
        if (isBlinking) {
            blinkTicks++;
            if (blinkTicks >= 7) { // Max blink hold to prevent kicks
                flushBlinkPackets();
            }
        }
        
        if (flickState == FlickState.FLICKING_AWAY) {
            if (flickStateTicks >= 3) {
                flickState = FlickState.ATTACKING;
                flickStateTicks = 0;
                // Snap rotation back to the real target aim point
                serverYaw = flickSnapYaw;
                serverPitch = flickSnapPitch;
                yawIntegral = 0.0f;
                pitchIntegral = 0.0f;
            }
        } else if (flickState == FlickState.ATTACKING) {
            if (!flickAttacked && flickAttackQueued) {
                double effectiveRange = getDynamicRange(mc);
                if (getDistanceToBoundingBox(mc, target) <= effectiveRange) {
                    mc.thePlayer.swingItem();
                    mc.playerController.attackEntity(mc.thePlayer, target);
                    CPSModule.addLeftClick();
                }
                flickAttacked = true;
                flickAttackQueued = false;
            }
            if (flickStateTicks >= 2) {
                flickState = FlickState.IDLE;
                flushBlinkPackets();
            }
        }
    }
    
    private boolean isLookingAtTarget(Minecraft mc, EntityLivingBase targetEntity) {
        if (targetEntity == null) return false;
        
        // Convert server yaw/pitch to vector
        float f1 = MathHelper.cos(-serverYaw * 0.017453292f - (float)Math.PI);
        float f2 = MathHelper.sin(-serverYaw * 0.017453292f - (float)Math.PI);
        float f3 = -MathHelper.cos(-serverPitch * 0.017453292f);
        float f4 = MathHelper.sin(-serverPitch * 0.017453292f);
        Vec3 lookVec = new Vec3((f2 * f3), f4, (f1 * f3));
        
        Vec3 eyePos = mc.thePlayer.getPositionEyes(1.0f);
        double range = Math.max(attackRange.getValue(), swingRange.getValue());
        Vec3 endVec = eyePos.addVector(lookVec.xCoord * range, lookVec.yCoord * range, lookVec.zCoord * range);
        
        AxisAlignedBB bb = targetEntity.getEntityBoundingBox().expand(
            targetEntity.getCollisionBorderSize(), targetEntity.getCollisionBorderSize(), targetEntity.getCollisionBorderSize());
            
        MovingObjectPosition intercept = bb.calculateIntercept(eyePos, endVec);
        return intercept != null;
    }

    // ======================== ROTATION COMPUTATION (PI CONTROLLER) ========================

    private void computeRotations(Minecraft mc) {
        if (target == null || targetBox == null) {
            flickState = FlickState.IDLE;
            return;
        }
        
        RotationMode rotMode = rotations.getValue();
        if (rotMode == RotationMode.NONE) return;

        EntityPlayer player = mc.thePlayer;
        double ex = player.posX;
        double ey = player.posY + player.getEyeHeight();
        double ez = player.posZ;

        Vec3 aimPoint;
        if (rotMode == RotationMode.LiquidBounce || rotMode == RotationMode.Godly) {
            aimPoint = searchBestAimPoint(mc, targetBox, ex, ey, ez);
        } else if (targetArea.getValue() == TargetArea.CLOSEST) {
            double cx = MathHelper.clamp_double(ex, targetBox.minX, targetBox.maxX);
            double cy = MathHelper.clamp_double(ey, targetBox.minY, targetBox.maxY);
            double cz = MathHelper.clamp_double(ez, targetBox.minZ, targetBox.maxZ);
            aimPoint = new Vec3(cx, cy, cz);
        } else {
            aimPoint = new Vec3(
                (targetBox.minX + targetBox.maxX) / 2.0,
                (targetBox.minY + targetBox.maxY) / 2.0,
                (targetBox.minZ + targetBox.maxZ) / 2.0
            );
        }

        if (predictMotion.isEnabled()) {
            aimPoint = applyPrediction(aimPoint, target);
        }

        // --- Vape Aim Jitter Simulation ---
        double targetSpeedH = Math.sqrt(target.motionX * target.motionX + target.motionZ * target.motionZ);
        updateJitter(targetSpeedH);
        
        double jitteredX = aimPoint.xCoord + jitterXCurrent * (1.0 + targetSpeedH);
        double jitteredZ = aimPoint.zCoord + jitterZCurrent * (1.0 + targetSpeedH);
        double jitteredY = aimPoint.yCoord + jitterYCurrent;
        currentAimVec = new Vec3(jitteredX, jitteredY, jitteredZ);

        double dx = jitteredX - ex;
        double dy = jitteredY - ey;
        double dz = jitteredZ - ez;
        double distH = MathHelper.sqrt_double(dx * dx + dz * dz);

        float targetYaw = (float) (Math.atan2(dz, dx) * 180.0 / Math.PI) - 90.0f;
        float targetPitch = (float) (-(Math.atan2(dy, distH) * 180.0 / Math.PI));

        // HitFlick Offset Addition
        if (flickState == FlickState.FLICKING_AWAY) {
            flickSnapYaw = targetYaw;
            flickSnapPitch = targetPitch;
            targetYaw += currentFlickAngle;
        }

        if (rotMode == RotationMode.PI_CONTROLLER || rotMode == RotationMode.Silent) {
            // --- PI Controller Implementation ---
            float yawError = MathHelper.wrapAngleTo180_float(targetYaw - serverYaw);
            float pitchError = MathHelper.wrapAngleTo180_float(targetPitch - serverPitch);

            float previousYawStep = serverYaw - mc.thePlayer.prevRotationYaw;
            float previousPitchStep = serverPitch - mc.thePlayer.prevRotationPitch;

            float integrationStep = 0.05f; // Fixed tick delta (50ms)
            boolean yawSameDir = Math.signum(yawError) == Math.signum(previousYawStep);
            
            double pSpeedH = Math.sqrt(player.motionX * player.motionX + player.motionZ * player.motionZ);
            
            float pGain = 0.45f * pitchProportionalScale;
            float iGain = 0.91f * pitchIntegralScale;
            
            // Gain dynamic dampening
            float yawPGain = (Math.abs(yawError) < 5.0f ? 0.05f : 0.1f) * yawProportionalScale;
            float yawIGain = 0.33f * yawIntegralScale;

            if (Math.abs(player.motionY) > 0.1) {
                pitchError *= (float) (1.0 + Math.random() * 0.32);
            }
            if (yawSameDir && Math.abs(yawError) < 20.0f) {
                yawPGain *= 2.5f;
                previousYawStep *= (float) (1.0 + Math.min(targetSpeedH + pSpeedH, 0.25));
            }

            double distToTarget = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (distToTarget < 0.8) {
                double distScale = distToTarget / 0.8;
                pitchError *= (float) (distScale * distScale);
                yawError *= (float) distScale;
            }

            float pitchControlError = pitchError - previousPitchStep + (previousYawStep * integrationStep * (ThreadLocalRandom.current().nextBoolean() ? -1 : 1));
            float yawControlError = yawError - previousYawStep;

            pitchIntegral += pitchControlError * integrationStep;
            yawIntegral += yawControlError * integrationStep;

            if (Math.abs(yawError) > 120.0f) {
                yawIntegral = 0.0f;
            }

            float pitchAdjustment = pGain * pitchControlError + iGain * pitchIntegral;
            float yawAdjustment = yawPGain * yawControlError + yawIGain * yawIntegral;

            // Base speed application
            float speedScale = (float) aimSpeed.getValue() / 10.0f;
            yawAdjustment *= speedScale;
            pitchAdjustment *= speedScale;

            serverYaw += yawError + (yawAdjustment / 3.0f);
            serverPitch = MathHelper.clamp_float(serverPitch + pitchAdjustment, -90.0f, 90.0f);
            
        } else {
            // Legacy LiquidBounce / Godly Smoothing
            float minSpd = (float) minTurnSpeed.getValue();
            float maxSpd = (float) maxTurnSpeed.getValue();
            float speed = minSpd + ThreadLocalRandom.current().nextFloat() * (maxSpd - minSpd);
            float factor = (float) smoothFactor.getValue();

            float yawDiff = MathHelper.wrapAngleTo180_float(targetYaw - serverYaw);
            float pitchDiff = MathHelper.wrapAngleTo180_float(targetPitch - serverPitch);
            yawDiff = MathHelper.clamp_float(yawDiff, -speed, speed) * factor;
            pitchDiff = MathHelper.clamp_float(pitchDiff, -speed, speed) * factor;

            if (rotMode == RotationMode.Godly) {
                ThreadLocalRandom rnd = ThreadLocalRandom.current();
                yawDiff += rnd.nextFloat() * 2.6f - 1.3f;
                pitchDiff += rnd.nextFloat() * 1.4f - 0.7f;
            }

            serverYaw += yawDiff;
            serverPitch = MathHelper.clamp_float(serverPitch + pitchDiff, -90.0f, 90.0f);
        }

        // Apply GCD fix
        float[] fixed = applyGCD(serverYaw, serverPitch, mc);
        serverYaw = fixed[0];
        serverPitch = fixed[1];
    }
    
    private void randomizePIGains() {
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        pitchProportionalScale = 0.85f + rnd.nextFloat() * 0.3f;
        pitchIntegralScale = 0.85f + rnd.nextFloat() * 0.3f;
        yawProportionalScale = 0.8f + rnd.nextFloat() * 0.4f;
        yawIntegralScale = 0.85f + rnd.nextFloat() * 0.3f;
    }

    private void updateJitter(double targetSpeedH) {
        long now = System.currentTimeMillis();
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        
        if (now - lastJitterXTime > rnd.nextInt(100, 1000)) {
            jitterXTarget = rnd.nextDouble(-0.15, 0.15);
            lastJitterXTime = now;
        }
        if (now - lastJitterZTime > rnd.nextInt(100, 1000)) {
            jitterZTarget = rnd.nextDouble(-0.15, 0.15);
            lastJitterZTime = now;
        }
        if (now - lastJitterYTime > rnd.nextInt(100, 1000)) {
            jitterYTarget = rnd.nextDouble(-0.3, 0.25);
            lastJitterYTime = now;
        }

        // Smoothly interpolate current jitter to target
        jitterXCurrent = smoothJitter(jitterXCurrent, jitterXTarget, rnd);
        jitterZCurrent = smoothJitter(jitterZCurrent, jitterZTarget, rnd);
        jitterYCurrent = smoothJitter(jitterYCurrent, jitterYTarget, rnd);
    }
    
    private double smoothJitter(double current, double target, ThreadLocalRandom rnd) {
        double step = 0.01 + rnd.nextDouble(0.0, 0.05);
        if (target > current) {
            current = Math.min(target, current + step);
        } else if (target < current) {
            current = Math.max(target, current - step);
        }
        return current;
    }

    /** Search best aim point on bounding box — LiquidBounce/Godly style from OpenMyau */
    private Vec3 searchBestAimPoint(Minecraft mc, AxisAlignedBB bb, double ex, double ey, double ez) {
        double scanRange = Math.max(attackRange.getValue(), swingRange.getValue());
        double bMin = bodyMin.getValue();
        double bMax = bodyMax.getValue();
        double hMax = hSearch.getValue();

        double bestX = 0, bestY = 0, bestZ = 0;
        double bestScore = Double.MAX_VALUE;
        ThreadLocalRandom rnd = ThreadLocalRandom.current();

        for (double x = 0.0; x <= hMax + 0.001; x += 0.25) {
            for (double y = bMin; y <= bMax + 0.001; y += 0.25) {
                for (double z = 0.0; z <= hMax + 0.001; z += 0.25) {
                    double px = bb.minX + (bb.maxX - bb.minX) * x;
                    double py = bb.minY + (bb.maxY - bb.minY) * y;
                    double pz = bb.minZ + (bb.maxZ - bb.minZ) * z;
                    double dx = px - ex, dy = py - ey, dz = pz - ez;
                    double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
                    if (distance > scanRange) continue;

                    boolean visible = isPointVisible(mc, ex, ey, ez, px, py, pz);
                    if (!visible && !throughWallsBlock.isEnabled()) continue;

                    double score = distance;
                    if (!visible) score += 5.0;
                    if (distance > attackRange.getValue()) score += 10.0;
                    if (predictRandomize.isEnabled()) {
                        score += rnd.nextDouble() * predictRandomRange.getValue() * 5.0;
                    }

                    if (score < bestScore) {
                        bestScore = score;
                        bestX = px; bestY = py; bestZ = pz;
                    }
                }
            }
        }

        if (bestScore != Double.MAX_VALUE) {
            return new Vec3(bestX, bestY, bestZ);
        }
        return new Vec3(
            (bb.minX + bb.maxX) / 2.0,
            (bb.minY + bb.maxY) / 2.0,
            (bb.minZ + bb.maxZ) / 2.0
        );
    }

    /** Weighted motion prediction from OpenMyau */
    private Vec3 applyPrediction(Vec3 point, EntityLivingBase entity) {
        double speedFactor = predictSize.getValue();
        if (predictRandomize.isEnabled()) {
            double rand = predictRandomRange.getValue();
            speedFactor += ThreadLocalRandom.current().nextDouble(-rand, rand);
        }

        double avgMX = 0, avgMY = 0, avgMZ = 0;
        double totalWeight = 0;

        if (motionHistoryCount > 0) {
            for (int i = 0; i < motionHistoryCount; i++) {
                double weight = motionHistoryCount - i;
                avgMX += pastMotionX[i] * weight;
                avgMY += pastMotionY[i] * weight;
                avgMZ += pastMotionZ[i] * weight;
                totalWeight += weight;
            }
            avgMX /= totalWeight;
            avgMY /= totalWeight;
            avgMZ /= totalWeight;
        } else {
            avgMX = entity.motionX;
            avgMY = entity.motionY;
            avgMZ = entity.motionZ;
        }

        double maxVel = 2.0;
        avgMX = MathHelper.clamp_double(avgMX, -maxVel, maxVel);
        avgMY = MathHelper.clamp_double(avgMY, -maxVel, maxVel);
        avgMZ = MathHelper.clamp_double(avgMZ, -maxVel, maxVel);

        double px = entity.posX + avgMX * speedFactor;
        double py = entity.posY + avgMY * speedFactor * 0.5;
        double pz = entity.posZ + avgMZ * speedFactor;

        double ox = point.xCoord - entity.posX;
        double oy = point.yCoord - entity.posY;
        double oz = point.zCoord - entity.posZ;

        return new Vec3(px + ox, py + oy, pz + oz);
    }

    private void updateTargetMotionHistory(EntityLivingBase ent) {
        int size = pastMotionX.length;
        for (int i = size - 1; i > 0; i--) {
            pastMotionX[i] = pastMotionX[i - 1];
            pastMotionY[i] = pastMotionY[i - 1];
            pastMotionZ[i] = pastMotionZ[i - 1];
        }
        pastMotionX[0] = (float) (ent.posX - ent.prevPosX);
        pastMotionY[0] = (float) (ent.posY - ent.prevPosY);
        pastMotionZ[0] = (float) (ent.posZ - ent.prevPosZ);
        if (motionHistoryCount < size) motionHistoryCount++;
    }

    // ======================== GCD FIX ========================

    private float[] applyGCD(float yaw, float pitch, Minecraft mc) {
        float sens = mc.gameSettings.mouseSensitivity * 0.6f + 0.2f;
        float gcd = sens * sens * sens * 1.2f;
        float prevYaw = mc.thePlayer.prevRotationYaw;
        float prevPitch = mc.thePlayer.prevRotationPitch;
        float deltaYaw = yaw - prevYaw;
        float deltaPitch = pitch - prevPitch;
        deltaYaw -= deltaYaw % gcd;
        deltaPitch -= deltaPitch % gcd;
        return new float[]{prevYaw + deltaYaw, prevPitch + deltaPitch};
    }

    // ======================== AUTOBLOCK ========================

    private void handleAutoBlock(Minecraft mc) {
        if (autoBlock.getValue() == AutoBlockMode.NONE) return;
        if (target == null || !isHoldingSword(mc)) {
            if (isBlockingState) stopBlocking();
            return;
        }

        double dist = getDistanceToBoundingBox(mc, target);
        boolean inBlockRange = dist <= autoBlockRange.getValue();
        boolean inSwing = dist <= swingRange.getValue();

        if (!inBlockRange) {
            if (isBlockingState) stopBlocking();
            return;
        }

        // Block when target is in block range but not in swing range (pre-engage block)
        if (!inSwing && !isBlockingState) {
            startBlocking(mc);
        }
    }

    private void startBlocking(Minecraft mc) {
        if (mc.thePlayer == null || mc.thePlayer.getHeldItem() == null) return;
        if (!isHoldingSword(mc)) return;

        AutoBlockMode mode = autoBlock.getValue();
        if (mode == AutoBlockMode.VANILLA || mode == AutoBlockMode.SPOOF) {
            mc.getNetHandler().addToSendQueue(new C08PacketPlayerBlockPlacement(mc.thePlayer.getHeldItem()));
            mc.thePlayer.setItemInUse(mc.thePlayer.getHeldItem(), mc.thePlayer.getHeldItem().getMaxItemUseDuration());
        } else if (mode == AutoBlockMode.HYPIXEL) {
            // Hypixel-style: slot swap trick for noslow
            if (noSlowSpoof.isEnabled()) {
                int slot = ThreadLocalRandom.current().nextInt(9);
                while (slot == mc.thePlayer.inventory.currentItem) {
                    slot = ThreadLocalRandom.current().nextInt(9);
                }
                mc.getNetHandler().addToSendQueue(new C09PacketHeldItemChange(slot));
                mc.getNetHandler().addToSendQueue(new C09PacketHeldItemChange(mc.thePlayer.inventory.currentItem));
            }
            mc.getNetHandler().addToSendQueue(new C08PacketPlayerBlockPlacement(mc.thePlayer.getHeldItem()));
            mc.thePlayer.setItemInUse(mc.thePlayer.getHeldItem(), mc.thePlayer.getHeldItem().getMaxItemUseDuration());
        }

        isBlockingState = true;
    }

    private void stopBlocking() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer != null) {
            mc.getNetHandler().addToSendQueue(
                new C07PacketPlayerDigging(C07PacketPlayerDigging.Action.RELEASE_USE_ITEM, BlockPos.ORIGIN, EnumFacing.DOWN)
            );
            mc.thePlayer.stopUsingItem();
        }
        isBlockingState = false;
    }

    // ======================== TARGET SELECTION ========================

    private void updateTargetSelection(Minecraft mc) {
        if (inventoryCheck.isEnabled() && mc.currentScreen != null) {
            target = null;
            return;
        }

        if (weaponsOnly.isEnabled() && !isHoldingValidWeapon(mc)) {
            target = null;
            return;
        }

        double maxDist = Math.max(attackRange.getValue(), Math.max(swingRange.getValue(), autoBlockRange.getValue()));
        EntityLivingBase best = null;
        double bestScore = Double.MAX_VALUE;
        boolean oldTargetStillValid = false;

        int size = mc.theWorld.loadedEntityList.size();
        for (int i = 0; i < size; i++) {
            Object o = mc.theWorld.loadedEntityList.get(i);
            if (!(o instanceof EntityLivingBase)) continue;
            EntityLivingBase e = (EntityLivingBase) o;
            if (e == mc.thePlayer) continue;
            if (!e.isEntityAlive()) continue;
            if (e.deathTime > 0) continue;

            if (!isValidEntityType(e)) continue;
            if (TargetFilter.isShop(mc, e)) continue;

            if (e instanceof EntityPlayer) {
                EntityPlayer player = (EntityPlayer) e;
                if (botCheck.isEnabled() && TargetFilter.isBot(mc, player)) continue;
                if (teams.isEnabled() && TargetFilter.isSameTeam(mc, player)) continue;
            }

            double dist = getDistanceToBoundingBox(mc, e);
            if (dist > maxDist) continue;

            float health = e.getHealth();
            if (health < minHealth.getValue() || health > maxHealth.getValue()) continue;

            if (!isInFov(mc, e)) continue;

            if (aimCheck.isEnabled()) {
                float angle = getAngleToEntity(mc, e);
                if (angle > (float) aimCheckFOV.getValue()) continue;
            }

            if (target == e) {
                oldTargetStillValid = true;
            }

            double score = getScore(mc, e);
            if (score < bestScore) {
                bestScore = score;
                best = e;
            }
        }

        if (best == null) {
            target = null;
            return;
        }

        // Target switch cooldown — anti-cheat bypass
        if (target != null && target != best && oldTargetStillValid) {
            long now = System.currentTimeMillis();
            if (now - lastTargetSwitchTime < 1000L + targetSwitchCount * 250L) {
                return; // Keep current target
            }
            lastTargetSwitchTime = now;
            targetSwitchCount = Math.min(targetSwitchCount + 1, 8);
        } else if (target == best) {
            targetSwitchCount = Math.max(0, targetSwitchCount - 1);
        }

        if (target != best) {
            motionHistoryCount = 0;
        }

        target = best;
    }

    private double getScore(Minecraft mc, EntityLivingBase e) {
        switch (sort.getValue()) {
            case Health: return e.getHealth();
            case HurtTime: return e.hurtTime;
            case FOVAngle: return getAngleToEntity(mc, e);
            default: return getDistanceToBoundingBox(mc, e);
        }
    }

    // ======================== ROTATION EVENT ========================

    @SubscribeEvent
    public void onClientRotation(ClientRotationEvent event) {
        if (!isEnabled() || target == null) return;
        if (rotations.getValue() == RotationMode.NONE) return;

        event.yaw = serverYaw;
        event.pitch = serverPitch;
        event.silent = (rotations.getValue() == RotationMode.Silent
                     || rotations.getValue() == RotationMode.LiquidBounce
                     || rotations.getValue() == RotationMode.Godly);

        if (moveFix.getValue() != MoveFixMode.NONE) {
            ClientRotationHelper.get().requestSilentMoveFix();
        }
    }

    // ======================== PACKET INTERCEPTION (Blink) ========================

    @SubscribeEvent
    public void onSendPacket(SendPacketEvent event) {
        if (!isEnabled() || !isBlinking) return;
        Packet<?> packet = event.getPacket();
        if (packet instanceof C02PacketUseEntity) {
            C02PacketUseEntity use = (C02PacketUseEntity) packet;
            if (use.getAction() == C02PacketUseEntity.Action.ATTACK) {
                heldPackets.add(packet);
                event.setCanceled(true);
            }
        }
    }

    // ======================== RENDER (VISUAL ONLY) ========================

    @SubscribeEvent
    public void onRenderWorldLast(RenderWorldLastEvent event) {
        if (!isEnabled()) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null || target == null || targetBox == null) return;

        if (showTargetBox.isEnabled()) {
            renderTargetBox(mc, target, event.partialTicks);
        }

        if (visualizeAim.isEnabled() && currentAimVec != null
                && (rotations.getValue() == RotationMode.LiquidBounce || rotations.getValue() == RotationMode.Godly)) {
            renderAimPoint(mc, event.partialTicks);
        }
    }

    private void renderTargetBox(Minecraft mc, EntityLivingBase entity, float pt) {
        RenderManager rm = mc.getRenderManager();
        double x = entity.lastTickPosX + (entity.posX - entity.lastTickPosX) * pt - rm.viewerPosX;
        double y = entity.lastTickPosY + (entity.posY - entity.lastTickPosY) * pt - rm.viewerPosY;
        double z = entity.lastTickPosZ + (entity.posZ - entity.lastTickPosZ) * pt - rm.viewerPosZ;
        AxisAlignedBB bb = entity.getEntityBoundingBox().offset(-entity.posX + x, -entity.posY + y, -entity.posZ + z);

        GlStateManager.pushMatrix();
        GlStateManager.enableBlend();
        GlStateManager.disableTexture2D();
        GlStateManager.disableLighting();
        GlStateManager.depthMask(false);

        if (entity.hurtTime > 0) {
            GlStateManager.color(1.0f, 0.2f, 0.2f, 0.4f);
        } else {
            GlStateManager.color(0.2f, 1.0f, 0.3f, 0.4f);
        }

        RenderGlobal.drawSelectionBoundingBox(bb);

        GlStateManager.depthMask(true);
        GlStateManager.enableTexture2D();
        GlStateManager.disableBlend();
        GlStateManager.popMatrix();
    }

    private void renderAimPoint(Minecraft mc, float pt) {
        RenderManager rm = mc.getRenderManager();
        double ax = currentAimVec.xCoord - rm.viewerPosX;
        double ay = currentAimVec.yCoord - rm.viewerPosY;
        double az = currentAimVec.zCoord - rm.viewerPosZ;

        double size = 0.06;
        AxisAlignedBB bb = new AxisAlignedBB(ax - size, ay - size, az - size, ax + size, ay + size, az + size);

        GlStateManager.pushMatrix();
        GlStateManager.enableBlend();
        GlStateManager.disableDepth();
        GlStateManager.disableTexture2D();
        GlStateManager.disableLighting();
        GlStateManager.depthMask(false);
        GlStateManager.color(0.0f, 1.0f, 1.0f, 0.8f);

        VisualizerStyleMode style = visualizerStyle.getValue();
        if (style == VisualizerStyleMode.Box || style == VisualizerStyleMode.Both) {
            RenderGlobal.drawSelectionBoundingBox(bb);
        }

        GlStateManager.depthMask(true);
        GlStateManager.enableTexture2D();
        GlStateManager.enableDepth();
        GlStateManager.disableBlend();
        GlStateManager.popMatrix();
    }

    // ======================== HUD OVERLAY ========================

    @SubscribeEvent
    public void onRenderGameOverlay(RenderGameOverlayEvent.Post event) {
        if (event.type != RenderGameOverlayEvent.ElementType.TEXT || !isEnabled()) return;
        if (!showTargetHUD.isEnabled() || target == null || !target.isEntityAlive()) return;

        Minecraft mc = Minecraft.getMinecraft();
        ScaledResolution sr = new ScaledResolution(mc);

        int baseX = 10, baseY = 10;
        float scale = (float) hudScale.getValue();

        switch (hudPosition.getValue()) {
            case TOP_RIGHT: baseX = sr.getScaledWidth() - 110; baseY = 10; break;
            case BOT_LEFT: baseX = 10; baseY = sr.getScaledHeight() - 70; break;
            case BOT_RIGHT: baseX = sr.getScaledWidth() - 110; baseY = sr.getScaledHeight() - 70; break;
            default: break;
        }

        GlStateManager.pushMatrix();
        GlStateManager.scale(scale, scale, 1.0f);

        mc.fontRendererObj.drawStringWithShadow(target.getName(), baseX + 5, baseY + 5, 0xFFFFFF);
        mc.fontRendererObj.drawStringWithShadow(String.format("%.1f HP", target.getHealth()), baseX + 5, baseY + 18, 0x55FF55);
        mc.fontRendererObj.drawStringWithShadow(String.format("%.1f m", mc.thePlayer.getDistanceToEntity(target)), baseX + 5, baseY + 30, 0xAAAAAA);

        GlStateManager.popMatrix();
    }

    // ======================== UTILITY ========================

    private double getDistanceToBoundingBox(Minecraft mc, Entity e) {
        Vec3 eyes = mc.thePlayer.getPositionEyes(1.0f);
        AxisAlignedBB bb = e.getEntityBoundingBox();
        double cx = Math.max(bb.minX, Math.min(eyes.xCoord, bb.maxX));
        double cy = Math.max(bb.minY, Math.min(eyes.yCoord, bb.maxY));
        double cz = Math.max(bb.minZ, Math.min(eyes.zCoord, bb.maxZ));
        double dx = eyes.xCoord - cx;
        double dy = eyes.yCoord - cy;
        double dz = eyes.zCoord - cz;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    private double getDynamicRange(Minecraft mc) {
        if (!autoRange.isEnabled()) return attackRange.getValue();
        if (mc.thePlayer == null || target == null) return attackRange.getValue();

        double playerSpd = Math.sqrt(mc.thePlayer.motionX * mc.thePlayer.motionX + mc.thePlayer.motionZ * mc.thePlayer.motionZ);
        double targetSpd = Math.sqrt(target.motionX * target.motionX + target.motionZ * target.motionZ);
        double base = attackRange.getValue();
        double maxR = maxReach.getValue();
        double diff = playerSpd - targetSpd;
        double dynamicAdd = diff > 0.05 ? diff * 0.45 : 0.0;
        return Math.max(base, Math.min(maxR, base + dynamicAdd));
    }

    private boolean isInFov(Minecraft mc, Entity e) {
        float fovValue = (float) fov.getValue();
        if (fovValue >= 360.0f) return true;
        float yawDiff = Math.abs(MathHelper.wrapAngleTo180_float(getAngleToEntity(mc, e)));
        return yawDiff <= fovValue * 0.5f;
    }

    private float getAngleToEntity(Minecraft mc, Entity e) {
        double dx = e.posX - mc.thePlayer.posX;
        double dz = e.posZ - mc.thePlayer.posZ;
        float yaw = (float) (Math.atan2(dz, dx) * 180.0 / Math.PI) - 90.0f;
        return Math.abs(MathHelper.wrapAngleTo180_float(yaw - mc.thePlayer.rotationYaw));
    }

    private boolean hasLineOfSight(Minecraft mc, Entity e) {
        Vec3 eyes = mc.thePlayer.getPositionEyes(1.0f);
        Vec3 targetPos = new Vec3(e.posX, e.posY + e.getEyeHeight(), e.posZ);
        MovingObjectPosition mop = mc.theWorld.rayTraceBlocks(eyes, targetPos, false, true, false);
        return mop == null || mop.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK;
    }

    private boolean isPointVisible(Minecraft mc, double x1, double y1, double z1, double x2, double y2, double z2) {
        if (throughWallsBlock.isEnabled()) return true;
        return mc.theWorld.rayTraceBlocks(new Vec3(x1, y1, z1), new Vec3(x2, y2, z2), false, true, false) == null;
    }

    private boolean isValidEntityType(EntityLivingBase e) {
        if (e instanceof EntityPlayer) return players.isEnabled();
        if (e instanceof EntityDragon || e instanceof EntityWither) return bosses.isEnabled();
        if (e instanceof EntityIronGolem) return golems.isEnabled();
        if (e instanceof EntitySilverfish) return silverfishSetting.isEnabled();
        if (e instanceof EntityMob || e instanceof EntitySlime) return mobs.isEnabled();
        if (e instanceof EntityAnimal || e instanceof EntitySquid || e instanceof EntityBat || e instanceof EntityVillager) return animals.isEnabled();
        return false;
    }

    private boolean isHoldingValidWeapon(Minecraft mc) {
        if (mc.thePlayer == null || mc.thePlayer.getHeldItem() == null) return false;
        net.minecraft.item.Item item = mc.thePlayer.getHeldItem().getItem();
        if (item instanceof ItemSword) return true;
        if (item instanceof net.minecraft.item.ItemTool) return allowTools.isEnabled();
        return false;
    }

    private boolean isHoldingSword(Minecraft mc) {
        if (mc.thePlayer == null || mc.thePlayer.getHeldItem() == null) return false;
        return mc.thePlayer.getHeldItem().getItem() instanceof ItemSword;
    }

    private ClickPattern.PatternTechnique mapTechnique() {
        switch (randomization.getValue()) {
            case OFF: return ClickPattern.PatternTechnique.OFF;
            case NORMAL: return ClickPattern.PatternTechnique.NORMAL;
            case EXTRA: return ClickPattern.PatternTechnique.EXTRA;
            case EXTRA_PLUS: return ClickPattern.PatternTechnique.EXTRA_PLUS;
            default: return ClickPattern.PatternTechnique.EXTRA;
        }
    }

    private static Field findLeftClickCounterField() {
        try {
            Field field = ReflectionHelper.findField(Minecraft.class, "field_71429_W", "leftClickCounter");
            field.setAccessible(true);
            return field;
        } catch (Throwable ignored) {
            return null;
        }
    }

    @Override
    public String getHudInfo() {
        if (target != null) {
            Minecraft mc = Minecraft.getMinecraft();
            return String.format("%.1fm", mc.thePlayer != null ? mc.thePlayer.getDistanceToEntity(target) : 0f);
        }
        return "";
    }
}
