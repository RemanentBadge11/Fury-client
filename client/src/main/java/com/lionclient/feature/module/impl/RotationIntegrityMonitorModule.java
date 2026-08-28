package com.lionclient.feature.module.impl;

import com.lionclient.feature.module.Category;
import com.lionclient.feature.module.Module;
import com.lionclient.feature.setting.BooleanSetting;
import com.lionclient.feature.setting.NumberSetting;
import lion.client.ClientLogger;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.C03PacketPlayer;
import net.minecraft.util.MathHelper;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.input.Keyboard;

/**
 * Passive rotation audit module.
 *
 * This module does not alter aim, delay packets, or hide behavior. It records
 * client-side signals that server-side integrity checks commonly model:
 * large single-tick rotation deltas, abrupt acceleration, repeated exact steps,
 * pitch bounds, and packet bursts.
 */
public final class RotationIntegrityMonitorModule extends Module {
    private final NumberSetting yawDeltaLimit =
            new NumberSetting("Yaw Delta Limit", 5, 180, 1, 35);
    private final NumberSetting pitchDeltaLimit =
            new NumberSetting("Pitch Delta Limit", 5, 90, 1, 25);
    private final NumberSetting accelerationLimit =
            new NumberSetting("Accel Limit", 10, 180, 1, 55);
    private final NumberSetting repeatLimit =
            new NumberSetting("Repeat Limit", 2, 20, 1, 5);
    private final BooleanSetting logPackets =
            new BooleanSetting("Packet Cadence", true);

    private boolean initialized;
    private float lastYaw;
    private float lastPitch;
    private float lastYawDelta;
    private float lastPitchDelta;
    private float repeatedYawDelta;
    private float repeatedPitchDelta;
    private int repeatCount;
    private long lastMovementPacketMs;

    public RotationIntegrityMonitorModule() {
        super("RotationIntegrity", "Logs local rotation signatures for anticheat audits.", Category.CLIENT, Keyboard.KEY_NONE);
        addSetting(yawDeltaLimit);
        addSetting(pitchDeltaLimit);
        addSetting(accelerationLimit);
        addSetting(repeatLimit);
        addSetting(logPackets);
        setEnabled(true);
    }

    @Override
    protected void onEnable() {
        initialized = false;
        repeatCount = 0;
        lastMovementPacketMs = 0L;
    }

    @Override
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START) {
            return;
        }

        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null || mc.theWorld == null) {
            initialized = false;
            return;
        }

        EntityPlayerSP player = mc.thePlayer;
        if (!initialized) {
            lastYaw = player.rotationYaw;
            lastPitch = player.rotationPitch;
            lastYawDelta = 0.0F;
            lastPitchDelta = 0.0F;
            repeatedYawDelta = 0.0F;
            repeatedPitchDelta = 0.0F;
            initialized = true;
            return;
        }

        float yawDelta = MathHelper.wrapAngleTo180_float(player.rotationYaw - lastYaw);
        float pitchDelta = player.rotationPitch - lastPitch;
        float yawAccel = MathHelper.wrapAngleTo180_float(yawDelta - lastYawDelta);
        float pitchAccel = pitchDelta - lastPitchDelta;

        if (Math.abs(yawDelta) > yawDeltaLimit.getValue()) {
            flag("large yaw delta", "yawDelta=" + fmt(yawDelta));
        }
        if (Math.abs(pitchDelta) > pitchDeltaLimit.getValue()) {
            flag("large pitch delta", "pitchDelta=" + fmt(pitchDelta));
        }
        if (Math.abs(yawAccel) > accelerationLimit.getValue()
                || Math.abs(pitchAccel) > accelerationLimit.getValue()) {
            flag("rotation acceleration spike",
                    "yawAccel=" + fmt(yawAccel) + " pitchAccel=" + fmt(pitchAccel));
        }
        if (player.rotationPitch < -90.0F || player.rotationPitch > 90.0F) {
            flag("invalid pitch bounds", "pitch=" + fmt(player.rotationPitch));
        }

        updateRepeatSignature(yawDelta, pitchDelta);

        lastYaw = player.rotationYaw;
        lastPitch = player.rotationPitch;
        lastYawDelta = yawDelta;
        lastPitchDelta = pitchDelta;
    }

    @Override
    public void onOutboundPacket(Packet<?> packet) {
        if (!logPackets.isEnabled() || !(packet instanceof C03PacketPlayer)) {
            return;
        }

        long now = System.currentTimeMillis();
        if (lastMovementPacketMs > 0L) {
            long spacing = now - lastMovementPacketMs;
            if (spacing < 10L) {
                flag("movement packet burst", "spacingMs=" + spacing);
            } else if (spacing > 250L) {
                flag("movement packet gap", "spacingMs=" + spacing);
            }
        }
        lastMovementPacketMs = now;
    }

    private void updateRepeatSignature(float yawDelta, float pitchDelta) {
        boolean sameYaw = Math.abs(yawDelta - repeatedYawDelta) < 0.0001F;
        boolean samePitch = Math.abs(pitchDelta - repeatedPitchDelta) < 0.0001F;
        boolean moving = Math.abs(yawDelta) > 0.0001F || Math.abs(pitchDelta) > 0.0001F;

        if (moving && sameYaw && samePitch) {
            repeatCount++;
            if (repeatCount >= repeatLimit.getValue()) {
                flag("repeated exact rotation step",
                        "count=" + repeatCount + " yawDelta=" + fmt(yawDelta)
                                + " pitchDelta=" + fmt(pitchDelta));
            }
            return;
        }

        repeatedYawDelta = yawDelta;
        repeatedPitchDelta = pitchDelta;
        repeatCount = moving ? 1 : 0;
    }

    private void flag(String reason, String detail) {
        String message = "[RotationIntegrity] " + reason + " (" + detail + ")";
        System.out.println(message);
        ClientLogger.warn(message);
    }

    private static String fmt(float value) {
        return String.format(java.util.Locale.ROOT, "%.4f", value);
    }
}
