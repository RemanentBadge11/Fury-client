package com.lionclient.feature.module.impl;


import com.lionclient.feature.module.Category;
import com.lionclient.feature.module.Module;
import com.lionclient.feature.setting.BooleanSetting;
import com.lionclient.feature.setting.DecimalSetting;
import com.lionclient.feature.setting.NumberSetting;
import com.lionclient.util.simulation.Simulation;
import java.text.DecimalFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.Potion;
import net.minecraft.util.BlockPos;
import net.minecraft.util.Vec3;

import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.input.Keyboard;

/**
 * BridgeAssist — exact 1:1 port of Sakura's BridgeAssist.
 */
public final class BridgeAssistModule extends Module {

    private final DecimalSetting edgeOffset = new DecimalSetting("Edge Offset", 0.0, 0.3, 0.01, 0.0);
    private final NumberSetting jumpDelay = new NumberSetting("Jump Delay", 5, 300, 5, 50);
    private final NumberSetting sneakDelay = new NumberSetting("Sneak Delay", 5, 300, 5, 5);
    private final BooleanSetting adaptive = new BooleanSetting("Adaptive", false);
    private final BooleanSetting holdSneak = new BooleanSetting("Require Sneak", false);
    private final BooleanSetting holdBlocks = new BooleanSetting("Require Blocks", true);
    private final BooleanSetting onlyBackwards = new BooleanSetting("Require Backwards", true);
    private final BooleanSetting onlyRightClick = new BooleanSetting("Require Rightclick", true);
    private final BooleanSetting onlyLookdown = new BooleanSetting("Require Lookdown", true);

    private double edge = 0.3;
    private final double[][] RADIUS = new double[][]{
            {-this.edge, -this.edge}, {this.edge, -this.edge}, 
            {-this.edge, this.edge}, {this.edge, this.edge}
    };
    
    private boolean sneaking;
    private int jumpDelayTicks;
    private int jumpStartTick = -1;
    private int delayTicks;
    private int startTick = -1;
    private double lastMotion = 0.0;
    private double cachedSpeedMultiplier = 1.0;
    private int lastPotionCheckTick = 0;
    private final int earlySneak = 20;

    public BridgeAssistModule() {
        super("BridgeAssist", "Assists with bridging", Category.MOVEMENT, Keyboard.KEY_NONE);
        addSetting(edgeOffset);
        addSetting(jumpDelay);
        addSetting(sneakDelay);
        addSetting(adaptive);
        addSetting(holdSneak);
        addSetting(holdBlocks);
        addSetting(onlyBackwards);
        addSetting(onlyRightClick);
        addSetting(onlyLookdown);
    }

    @Override
    protected void onEnable() {
        sneaking = false;
        startTick = -1;
        jumpStartTick = -1;
        cachedSpeedMultiplier = 1.0;
        lastPotionCheckTick = 0;
        lastMotion = 0.0;
    }

    @Override
    protected void onDisable() {
        sneaking = false;
        sneak(false);
    }

    // ============================================================
    // MoveStateEvent equivalent — called from ModuleManager dispatch
    // ============================================================
    @Override
    public void onPrePlayerInput(com.lionclient.event.PrePlayerInputEvent event) {
        if (!this.conditionals()) {
            return;
        }
        if (!this.sneaking) {
            event.setSneak(false);
        }
    }

    // ============================================================
    // UpdateEvent equivalent — use PlayerTickEvent for correct timing
    // ============================================================
    @Override
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null || mc.theWorld == null) return;
        
        if (mc.currentScreen != null) {
            return;
        }
        
        if (!this.conditionals()) {
            this.sneaking = false;
            this.startTick = -1;
            this.jumpStartTick = -1;
            this.cachedSpeedMultiplier = 1.0;
            this.lastPotionCheckTick = 0;
            this.updateSneakState(Keyboard.isKeyDown(mc.gameSettings.keyBindSneak.getKeyCode()));
            this.lastMotion = 0.0;
            return;
        }
        
        EntityPlayerSP player = mc.thePlayer;
        int ticks = player.ticksExisted;
        if (ticks - this.lastPotionCheckTick > 20) {
            this.cachedSpeedMultiplier = this.getSpeedEffect(player);
            this.lastPotionCheckTick = ticks;
        }
        
        Vec3 position = new Vec3(player.posX, player.posY, player.posZ);
        Simulation sim = Simulation.create();
        
        if (player.isSneaking()) {
            sim.setForward(player.moveForward / 0.3f);
            sim.setStrafe(player.moveStrafing / 0.3f);
            sim.setSneak(false);
        } else {
            sim.setForward(player.moveForward);
            sim.setStrafe(player.moveStrafing);
        }
        
        sim.tick();
        
        Vec3 simPosition = sim.getPosition();
        Vec3 simMotion = sim.getMotion();
        double motionSq = simMotion.xCoord * simMotion.xCoord + simMotion.zCoord * simMotion.zCoord;
        boolean isMovingFast = motionSq > 0.01;
        
        double adaptiveEdgeOffset = this.edgeOffset.getValue();
        int adaptiveJumpDelay = this.jumpDelay.getValue();
        int adaptiveSneakDelay = this.sneakDelay.getValue();
        
        if (this.adaptive.isEnabled() && isMovingFast) {
            double currentMotion = Math.sqrt(motionSq);
            double[] adaptiveValues = this.calculateAdaptive(currentMotion, this.cachedSpeedMultiplier);
            adaptiveEdgeOffset = adaptiveValues[0];
            adaptiveJumpDelay = (int) adaptiveValues[1];
            adaptiveSneakDelay = (int) adaptiveValues[2];
        }
        
        if (Keyboard.isKeyDown(mc.gameSettings.keyBindJump.getKeyCode()) && !player.onGround && (player.moveForward != 0.0f || player.moveStrafing != 0.0f) && adaptiveSneakDelay > 0) {
            this.jumpStartTick = ticks;
            this.jumpDelayTicks = this.calculateChance(adaptiveJumpDelay);
            this.pressSneak(true);
            return;
        }
        
        double edgeDist = this.computeEdge(simPosition, position, mc);
        
        if (Double.isNaN(edgeDist)) {
            if (this.sneaking) {
                this.tryReleaseSneak(true, mc);
            }
            return;
        }
        
        if (edgeDist > adaptiveEdgeOffset) {
            this.pressSneak(true);
        } else if (this.sneaking) {
            this.tryReleaseSneak(true, mc);
        }
    }

    private double[] calculateAdaptive(double currentMotion, double speedMultiplier) {
        double baseOffset = this.edgeOffset.getValue();
        double baseJumpDelay = this.jumpDelay.getValue();
        double baseSneakDelay = this.sneakDelay.getValue();
        double speedFactor = Math.min(2.0, currentMotion * 10.0);
        double speedEffectFactor = Math.max(1.0, speedMultiplier);
        double combinedFactor = Math.min(3.0, speedFactor * speedEffectFactor);
        double offsetReduction = 1.0 - combinedFactor * 0.15;
        double adaptiveOffset = Math.max(baseOffset * 0.4, baseOffset * offsetReduction);
        double jumpDelayIncrease = 1.0 + combinedFactor * 0.3;
        double adaptiveJumpDelay = Math.min(300.0, baseJumpDelay * jumpDelayIncrease);
        double sneakDelayIncrease = 1.0 + combinedFactor * 0.2;
        double adaptiveSneakDelay = Math.min(300.0, baseSneakDelay * sneakDelayIncrease);
        return new double[]{adaptiveOffset, adaptiveJumpDelay, adaptiveSneakDelay};
    }

    public boolean conditionals() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.currentScreen != null) {
            return false;
        }
        EntityPlayerSP player = mc.thePlayer;
        if (player == null || player.capabilities.isFlying) {
            return false;
        }
        if (this.onlyRightClick.isEnabled() && !mc.gameSettings.keyBindUseItem.isKeyDown()) {
            return false;
        }
        if (this.holdBlocks.isEnabled() && !this.isBlock(player)) {
            return false;
        }
        if (this.onlyLookdown.isEnabled() && player.rotationPitch < 70.0f) {
            return false;
        }
        if (this.onlyBackwards.isEnabled() && (double) player.moveForward > -0.2) {
            return false;
        }
        return !this.holdSneak.isEnabled() || Keyboard.isKeyDown(mc.gameSettings.keyBindSneak.getKeyCode());
    }

    private void pressSneak(boolean resetUnsneak) {
        this.sneak(true);
        this.sneaking = true;
        if (resetUnsneak) {
            this.startTick = -1;
        }
    }

    private void tryReleaseSneak(boolean resetDelay, Minecraft mc) {
        int ticks = mc.thePlayer.ticksExisted;
        if (this.startTick == -1 && this.jumpStartTick == -1) {
            this.startTick = ticks;
            this.delayTicks = this.calculateChance(this.sneakDelay.getValue());
        }
        if (this.isWaiting(ticks, this.jumpStartTick, this.jumpDelayTicks) || this.isWaiting(ticks, this.startTick, this.delayTicks)) {
            return;
        }
        this.releaseSneak(resetDelay);
    }

    private int calculateChance(double value) {
        double raw = (value - 50.0) / 50.0;
        return (int) raw + (Math.random() < raw - (double) ((int) raw) ? 1 : 0);
    }

    private boolean isWaiting(int current, int start, int delay) {
        return start != -1 && current - start < delay;
    }

    private void releaseSneak(boolean reset) {
        this.sneak(false);
        this.sneaking = false;
        if (reset) {
            this.jumpStartTick = -1;
            this.startTick = -1;
        }
    }

    private void sneak(boolean state) {
        Minecraft mc = Minecraft.getMinecraft();
        net.minecraft.client.settings.KeyBinding.setKeyBindState(
                mc.gameSettings.keyBindSneak.getKeyCode(), 
                state || Keyboard.isKeyDown(mc.gameSettings.keyBindSneak.getKeyCode())
        );
    }

    private void updateSneakState(boolean state) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer != null && mc.thePlayer.isSneaking() != state) {
            this.sneak(state);
        }
    }

    private double computeEdge(Vec3 pos1, Vec3 pos2, Minecraft mc) {
        int floorY = (int) (pos1.yCoord - 0.01);
        double best = Double.NaN;
        for (double[] c : this.RADIUS) {
            int bx = (int) Math.floor(pos2.xCoord + c[0]);
            int bz = (int) Math.floor(pos2.zCoord + c[1]);
            if (!this.isAirBlock(bx, floorY, bz, mc)) {
                boolean zDiff;
                double offX = Math.abs(pos1.xCoord - (double) (bx + (pos1.xCoord < (double) bx + 0.5 ? 0 : 1)));
                double offZ = Math.abs(pos1.zCoord - (double) (bz + (pos1.zCoord < (double) bz + 0.5 ? 0 : 1)));
                boolean xDiff = (int) Math.floor(pos1.xCoord) != bx;
                zDiff = (int) Math.floor(pos1.zCoord) != bz;
                double cornerDist = xDiff ? (zDiff ? Math.max(offX, offZ) : offX) : (zDiff ? offZ : 0.0);
                best = Double.isNaN(best) ? cornerDist : Math.min(best, cornerDist);
            }
        }
        return best;
    }

    private boolean isAirBlock(int x, int y, int z, Minecraft mc) {
        try {
            return mc.theWorld.getBlockState(new BlockPos(x, y, z)).getBlock().getUnlocalizedName().contains("air");
        } catch (Exception e) {
            return false;
        }
    }

    private boolean isBlock(EntityPlayerSP player) {
        ItemStack held = player.getHeldItem();
        return held != null && held.getItem() instanceof ItemBlock;
    }

    private double getSpeedEffect(EntityPlayerSP player) {
        if (player.isPotionActive(Potion.moveSpeed)) {
            int amplifier = player.getActivePotionEffect(Potion.moveSpeed).getAmplifier();
            return 1.0 + (double) (amplifier + 1) * 0.2;
        }
        return 1.0;
    }
}
