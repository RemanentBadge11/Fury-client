package com.lionclient.feature.module.impl;

import com.lionclient.event.PrePlayerInputEvent;
import com.lionclient.feature.module.Category;
import com.lionclient.feature.module.Module;
import com.lionclient.feature.setting.DecimalSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.C02PacketUseEntity;
import net.minecraft.potion.Potion;
import org.lwjgl.input.Keyboard;

public final class WTapModule extends Module {

    private final DecimalSetting delay = new DecimalSetting("Delay", 0.0D, 10.0D, 0.5D, 5.5D);
    private final DecimalSetting duration = new DecimalSetting("Duration", 1.0D, 5.0D, 0.5D, 1.5D);

    private boolean active = false;
    private boolean stopForward = false;
    private long delayTicks = 0L;
    private long durationTicks = 0L;
    private long lastAttackMs = 0L;

    public WTapModule() {
        super("WTap", "Automatically resets sprint on attack to maximize knockback.", Category.COMBAT, Keyboard.KEY_NONE);
        addSetting(delay);
        addSetting(duration);
    }

    @Override
    protected void onEnable() {
        active = false;
        stopForward = false;
        delayTicks = 0L;
        durationTicks = 0L;
        lastAttackMs = 0L;
    }

    @Override
    protected void onDisable() {
        active = false;
        stopForward = false;
        delayTicks = 0L;
        durationTicks = 0L;
    }

    @Override
    public void onOutboundPacket(Packet<?> packet) {
        if (!isEnabled()) return;
        if (packet instanceof C02PacketUseEntity) {
            C02PacketUseEntity useEntity = (C02PacketUseEntity) packet;
            if (useEntity.getAction() == C02PacketUseEntity.Action.ATTACK) {
                Minecraft mc = Minecraft.getMinecraft();
                if (mc.thePlayer == null) return;
                long now = System.currentTimeMillis();
                if (!active && (now - lastAttackMs >= 500L) && mc.thePlayer.isSprinting()) {
                    lastAttackMs = now;
                    active = true;
                    stopForward = false;
                    delayTicks = (long) (50.0F * (float) delay.getValue());
                    durationTicks = (long) (50.0F * (float) duration.getValue());
                }
            }
        }
    }

    @Override
    public void onPrePlayerInput(PrePlayerInputEvent event) {
        if (!isEnabled() || !active) return;

        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null) {
            active = false;
            return;
        }

        if (!stopForward && !canTrigger(mc)) {
            active = false;
            delayTicks = 0L;
            durationTicks = 0L;
        } else if (delayTicks > 0L) {
            delayTicks -= 50L;
        } else {
            if (durationTicks > 0L) {
                durationTicks -= 50L;
                stopForward = true;
                event.setForward(0.0F);
                if (mc.thePlayer.movementInput != null) {
                    mc.thePlayer.movementInput.moveForward = 0.0F;
                }
            }
            if (durationTicks <= 0L) {
                active = false;
            }
        }
    }

    private boolean canTrigger(Minecraft mc) {
        if (mc.thePlayer == null || mc.thePlayer.movementInput == null) return false;
        return !(mc.thePlayer.movementInput.moveForward < 0.8F)
                && !mc.thePlayer.isCollidedHorizontally
                && ((float) mc.thePlayer.getFoodStats().getFoodLevel() > 6.0F || mc.thePlayer.capabilities.allowFlying)
                && (mc.thePlayer.isSprinting() || (!mc.thePlayer.isUsingItem() && !mc.thePlayer.isPotionActive(Potion.blindness) && mc.gameSettings.keyBindSprint.isKeyDown()));
    }

    @Override
    public String getHudInfo() {
        return active ? "Active" : "";
    }
}
