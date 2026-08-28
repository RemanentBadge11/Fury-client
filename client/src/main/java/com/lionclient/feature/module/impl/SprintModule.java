package com.lionclient.feature.module.impl;

import com.lionclient.feature.module.Category;
import com.lionclient.feature.module.Module;
import com.lionclient.feature.setting.BooleanSetting;
import com.lionclient.feature.setting.EnumSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.input.Keyboard;

public final class SprintModule extends Module {

    private static final float FORWARD_THRESHOLD = 0.8F;

    public enum Mode {
        LEGIT("Legit"),
        OMNIDIRECTIONAL("Omnidirectional");

        private final String name;
        Mode(String name) { this.name = name; }
        @Override public String toString() { return name; }
    }

    private final EnumSetting<Mode> mode = new EnumSetting<Mode>("Mode", Mode.values(), Mode.LEGIT);
    private final BooleanSetting ignoreBlindness = new BooleanSetting("Ignore Blindness", true);
    private final BooleanSetting ignoreHunger = new BooleanSetting("Ignore Hunger", true);
    private final BooleanSetting ignoreCollision = new BooleanSetting("Ignore Collision", true);
    private final BooleanSetting stopOnSneak = new BooleanSetting("Stop on Sneak", true);
    private final BooleanSetting stopOnUsingItem = new BooleanSetting("Stop on Using Item", false);

    public SprintModule() {
        super("Sprint", "Automatically keeps you sprinting.", Category.MOVEMENT, Keyboard.KEY_NONE);
        addSetting(mode);
        addSetting(ignoreBlindness);
        addSetting(ignoreHunger);
        addSetting(ignoreCollision);
        addSetting(stopOnSneak);
        addSetting(stopOnUsingItem);
    }

    @net.minecraftforge.fml.common.eventhandler.SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START || !isEnabled()) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null || mc.theWorld == null) return;
        if (mc.gameSettings == null || mc.gameSettings.keyBindSprint == null) return;

        // Drive the sprint keybind so vanilla MC's legal-sprint validation runs
        // (forward motion, food level, collision, etc.) — avoids illegal sprint
        // states that Matrix/Grim/Polar flag. Mirrors raven/LiquidBlink pattern.
        if (canSprint(mc)) {
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindSprint.getKeyCode(), true);
        } else {
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindSprint.getKeyCode(), false);
        }
    }

    @Override
    protected void onEnable() {
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(this);
    }

    @Override
    protected void onDisable() {
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.unregister(this);
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer != null) {
            if (mc.thePlayer.isSprinting()) {
                mc.thePlayer.setSprinting(false);
            }
        }
        if (mc.gameSettings != null && mc.gameSettings.keyBindSprint != null) {
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindSprint.getKeyCode(), false);
        }
    }

    private boolean canSprint(Minecraft mc) {
        if (mc.thePlayer.movementInput.moveForward == 0 && mc.thePlayer.movementInput.moveStrafe == 0) {
            return false;
        }

        if (stopOnSneak.isEnabled() && mc.thePlayer.isSneaking()) {
            return false;
        }

        if (stopOnUsingItem.isEnabled() && mc.thePlayer.isUsingItem()) {
            return false;
        }

        if (mode.getValue() == Mode.LEGIT) {
            if (mc.thePlayer.movementInput.moveForward < FORWARD_THRESHOLD) {
                return false;
            }
        }

        if (mc.thePlayer.isCollidedHorizontally && !ignoreCollision.isEnabled()) {
            return false;
        }

        if (!ignoreHunger.isEnabled()) {
            if (mc.thePlayer.getFoodStats().getFoodLevel() <= 6 && !mc.thePlayer.capabilities.allowFlying) {
                return false;
            }
        }

        if (!ignoreBlindness.isEnabled()) {
            if (mc.thePlayer.isPotionActive(net.minecraft.potion.Potion.blindness)) {
                return false;
            }
        }

        return true;
    }
}