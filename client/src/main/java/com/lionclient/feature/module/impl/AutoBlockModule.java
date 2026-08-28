package com.lionclient.feature.module.impl;

import com.lionclient.feature.module.Category;
import com.lionclient.feature.module.Module;
import com.lionclient.feature.setting.BooleanSetting;
import com.lionclient.feature.setting.EnumSetting;
import com.lionclient.feature.setting.NumberSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemSword;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.C02PacketUseEntity;
import net.minecraft.network.play.client.C07PacketPlayerDigging;
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

/**
 * AutoBlock — automatically sword-blocks between attack swings.
 *
 * Combined from both Sakura versions:
 *
 * Sakura 6.5.2 (ka/AutoBlock):
 *   - Pre mode: sendBlockPacket() before motion
 *   - Post mode: sendBlockPacket() after motion
 *   - Fake mode: set blocking flag only (visual only)
 *   - Packet: C08 with current held item (simple form)
 *   - Unblock: C07 RELEASE_USE_ITEM at BlockPos.ORIGIN
 *
 * Sakura Newer (AutoBlockMod):
 *   - Listens for C02 attack packets to detect target
 *   - Blocks on hurtTime match
 *   - Timer-based hold duration
 *   - Range check for auto-unblock
 *
 * LionClient integration:
 *   - KillAura calls preAttackUnblock() before swing and postAttackReblock() after
 *   - onOutboundPacket() tracks C02 attack targets
 *   - Grim mode: unblock → attack → wait N ticks → reblock (Matrix/Grim bypass)
 */
public final class AutoBlockModule extends Module {

    public enum BlockMode {
        FAKE("Fake"),
        VANILLA("Vanilla"),
        INTERACT("Interact"),
        GRIM("Grim");

        private final String name;
        BlockMode(String name) { this.name = name; }
        @Override public String toString() { return name; }
    }

    // ======== Settings ========

    private final EnumSetting<BlockMode> mode = new EnumSetting<>("Mode", BlockMode.values(), BlockMode.GRIM);
    private final NumberSetting range = new NumberSetting("Range", 3, 6, 1, 4);
    private final NumberSetting reblockDelay = new NumberSetting("Reblock Delay", 0, 4, 1, 1);
    private final BooleanSetting onlyInFight = new BooleanSetting("Only In Fight", true);
    private final BooleanSetting onlyRightClick = new BooleanSetting("Only Right Click", false);

    private static AutoBlockModule instance;

    // ======== Internal State ========

    // Server-side blocking state tracking
    private boolean serverBlocking;
    // Tick counter for reblock delay after swing
    private int ticksSinceSwing;
    // Whether we swung this tick (set by outbound packet hook or KillAura)
    private boolean swungThisTick;
    // Last attack target from C02
    private EntityPlayer lastTarget;
    // Timestamp of last attack for fight timeout
    private long lastAttackMs;
    // Fight timeout — stop blocking if no attack in this many ms
    private static final long FIGHT_TIMEOUT_MS = 800;

    // ======== Constructor ========

    public AutoBlockModule() {
        super("AutoBlock", "Automatically blocks with sword between attacks.", Category.COMBAT, Keyboard.KEY_NONE);
        instance = this;
        addSetting(mode);
        addSetting(range);
        addSetting(reblockDelay);
        addSetting(onlyInFight);
        addSetting(onlyRightClick);
    }

    public static AutoBlockModule getInstance() {
        return instance;
    }

    // ======== External API (called by KillAura) ========

    /** KillAura calls this before its attack swing. */
    public void preAttackUnblock() {
        if (!isEnabled()) return;
        if (!serverBlocking) return;

        BlockMode currentMode = mode.getValue();
        if (currentMode == BlockMode.FAKE) return; // Fake mode never sends packets

        sendRelease();
    }

    /** KillAura calls this after its attack swing. */
    public void postAttackReblock() {
        if (!isEnabled()) return;
        ticksSinceSwing = 0;
        swungThisTick = true;
        lastAttackMs = System.currentTimeMillis();
    }

    /** Returns true if the server thinks we are blocking. */
    public boolean isServerBlocking() {
        return serverBlocking;
    }

    // ======== Enable / Disable ========

    @Override
    protected void onEnable() {
        serverBlocking = false;
        ticksSinceSwing = 0;
        swungThisTick = false;
        lastTarget = null;
        lastAttackMs = 0;
    }

    @Override
    protected void onDisable() {
        if (serverBlocking) {
            sendRelease();
        }
        lastTarget = null;
    }

    // ======== Outbound Packet Hook ========

    @Override
    public void onOutboundPacket(Packet<?> packet) {
        if (!isEnabled()) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.thePlayer == null || mc.theWorld == null) return;

        // Track attack packets to know when we're in a fight (like Sakura AutoBlockMod)
        if (packet instanceof C02PacketUseEntity) {
            C02PacketUseEntity useEntity = (C02PacketUseEntity) packet;
            if (useEntity.getAction() == C02PacketUseEntity.Action.ATTACK) {
                Entity entity = useEntity.getEntityFromWorld(mc.theWorld);
                if (entity instanceof EntityPlayer && entity != mc.thePlayer) {
                    lastTarget = (EntityPlayer) entity;
                    lastAttackMs = System.currentTimeMillis();
                    swungThisTick = true;
                    ticksSinceSwing = 0;

                    // For Grim/Interact mode: unblock right before the attack if still blocking
                    BlockMode currentMode = mode.getValue();
                    if ((currentMode == BlockMode.GRIM || currentMode == BlockMode.INTERACT)
                            && serverBlocking) {
                        sendRelease();
                    }
                }
            }
        }
    }

    // ======== Main Tick ========

    @Override
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;

        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null || mc.theWorld == null) return;
        if (mc.currentScreen != null) {
            if (serverBlocking) sendRelease();
            return;
        }

        // Must be holding a sword (both Sakura versions check this)
        if (!holdingSword(mc)) {
            if (serverBlocking) sendRelease();
            lastTarget = null;
            swungThisTick = false;
            return;
        }

        // Right click check (from Sakura AutoBlockMod)
        if (onlyRightClick.isEnabled() && !Mouse.isButtonDown(1)) {
            if (serverBlocking) sendRelease();
            swungThisTick = false;
            return;
        }

        BlockMode currentMode = mode.getValue();
        ticksSinceSwing++;

        // Check if we should be blocking at all
        boolean shouldBlock = shouldBeBlocking(mc);

        if (!shouldBlock) {
            if (serverBlocking) {
                sendRelease();
            }
            swungThisTick = false;
            return;
        }

        switch (currentMode) {
            case FAKE:
                handleFakeBlock(mc);
                break;

            case VANILLA:
                handleVanillaBlock(mc);
                break;

            case INTERACT:
                handleInteractBlock(mc);
                break;

            case GRIM:
                handleGrimBlock(mc);
                break;
        }

        swungThisTick = false;
    }

    // ======== Condition Check ========

    private boolean shouldBeBlocking(Minecraft mc) {
        if (!holdingSword(mc)) return false;

        if (onlyInFight.isEnabled()) {
            if (lastTarget == null || lastTarget.isDead) return false;
            if (mc.thePlayer.getDistanceToEntity(lastTarget) > range.getValue()) return false;
            if (System.currentTimeMillis() - lastAttackMs > FIGHT_TIMEOUT_MS) return false;
        }

        return true;
    }

    // ======== Mode Handlers ========

    /**
     * Fake: only set blocking flag for visual sword-block animation.
     * No packets sent — server never knows. Matches Sakura's Fake mode.
     */
    private void handleFakeBlock(Minecraft mc) {
        // Sakura Fake mode: just sets blocking = true for visual rendering
        // This makes the sword appear in blocking position client-side
        serverBlocking = true;
    }

    /**
     * Vanilla: simple block/unblock cycle with reblock delay after swing.
     */
    private void handleVanillaBlock(Minecraft mc) {
        if (swungThisTick) {
            return;
        }

        if (ticksSinceSwing > reblockDelay.getValue() && !serverBlocking) {
            sendBlock(mc);
        }
    }

    /**
     * Interact: uses interact-style block placement, reblocks after delay.
     */
    private void handleInteractBlock(Minecraft mc) {
        if (swungThisTick) {
            return;
        }

        if (ticksSinceSwing > reblockDelay.getValue() && !serverBlocking) {
            sendBlock(mc);
        }
    }

    /**
     * Grim bypass sequence:
     *   Tick N:   C07 release (if blocking) → C02 attack
     *   Tick N+D: C08 block (where D = reblockDelay, minimum 1)
     *
     * Key: Grim checks you're NOT blocking when the attack goes out,
     * and that block/unblock packets are at least 1 tick apart.
     */
    private void handleGrimBlock(Minecraft mc) {
        if (swungThisTick) {
            return;
        }

        int delay = Math.max(1, reblockDelay.getValue());
        if (ticksSinceSwing >= delay && !serverBlocking) {
            sendBlock(mc);
        }
    }

    // ======== Packet Senders (ported from Sakura) ========

    /**
     * Send the 1.8 sword block packet.
     * Sakura uses C08PacketPlayerBlockPlacement with held item.
     * Proper 1.8 format: pos (-1,-1,-1), face 255.
     */
    private void sendBlock(Minecraft mc) {
        if (mc.getNetHandler() == null) return;
        if (serverBlocking) return;

        ItemStack held = mc.thePlayer.inventory.getCurrentItem();
        if (held == null || !(held.getItem() instanceof ItemSword)) return;

        mc.getNetHandler().addToSendQueue(
            new C08PacketPlayerBlockPlacement(
                new BlockPos(-1, -1, -1),
                255,
                held,
                0.0F, 0.0F, 0.0F
            )
        );
        serverBlocking = true;
    }

    /**
     * Send release/unblock packet.
     * Exact Sakura unblock: C07 RELEASE_USE_ITEM at BlockPos.ORIGIN, EnumFacing.DOWN.
     */
    private void sendRelease() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.getNetHandler() == null) return;
        if (!serverBlocking) return;

        mc.getNetHandler().addToSendQueue(
            new C07PacketPlayerDigging(
                C07PacketPlayerDigging.Action.RELEASE_USE_ITEM,
                BlockPos.ORIGIN,
                EnumFacing.DOWN
            )
        );
        serverBlocking = false;
    }

    // ======== Utilities ========

    private boolean holdingSword(Minecraft mc) {
        if (mc.thePlayer == null) return false;
        ItemStack stack = mc.thePlayer.getHeldItem();
        return stack != null && stack.getItem() instanceof ItemSword;
    }

    @Override
    public String getHudInfo() {
        return mode.getValue().toString();
    }
}