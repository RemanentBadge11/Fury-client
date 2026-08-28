package com.lionclient.feature.module.impl;

import com.lionclient.feature.module.Category;
import com.lionclient.feature.module.Module;
import com.lionclient.feature.setting.EnumSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.input.Keyboard;

public final class FullbrightModule extends Module {

    public enum Mode {
        GAMMA("Gamma"),
        EFFECT("Effect");

        private final String name;
        Mode(String name) { this.name = name; }
        @Override public String toString() { return name; }
    }

    private final EnumSetting<Mode> mode = new EnumSetting<>("Mode", Mode.values(), Mode.GAMMA);

    private float prevGamma = Float.NaN;
    private boolean appliedNightVision = false;

    public FullbrightModule() {
        super("Fullbright", "Removes darkness and brightens the world view.", Category.RENDER, Keyboard.KEY_NONE);
        addSetting(mode);
    }

    @Override
    protected void onEnable() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mode.getValue() == Mode.GAMMA) {
            if (mc.gameSettings != null) {
                prevGamma = mc.gameSettings.gammaSetting;
            }
        } else if (mode.getValue() == Mode.EFFECT) {
            appliedNightVision = true;
        }
    }

    @Override
    protected void onDisable() {
        Minecraft mc = Minecraft.getMinecraft();
        if (!Float.isNaN(prevGamma)) {
            if (mc.gameSettings != null) {
                mc.gameSettings.gammaSetting = prevGamma;
            }
            prevGamma = Float.NaN;
        }
        if (appliedNightVision) {
            if (mc.thePlayer != null) {
                mc.thePlayer.removePotionEffectClient(Potion.nightVision.id);
            }
            appliedNightVision = false;
        }
    }

    @Override
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !isEnabled()) return;

        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null || mc.gameSettings == null) return;

        if (mode.getValue() == Mode.GAMMA) {
            if (Float.isNaN(prevGamma)) {
                prevGamma = mc.gameSettings.gammaSetting;
            }
            mc.gameSettings.gammaSetting = 1000.0F;
        } else if (mode.getValue() == Mode.EFFECT) {
            mc.thePlayer.addPotionEffect(new PotionEffect(Potion.nightVision.id, 25940, 0));
            appliedNightVision = true;
        }
    }
}
