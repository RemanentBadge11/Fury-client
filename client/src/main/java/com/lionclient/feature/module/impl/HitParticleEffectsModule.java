package com.lionclient.feature.module.impl;

import com.lionclient.feature.module.Category;
import com.lionclient.feature.module.Module;
import com.lionclient.feature.setting.BooleanSetting;
import com.lionclient.feature.setting.EnumSetting;
import com.lionclient.feature.setting.NumberSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.EnumParticleTypes;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.lwjgl.input.Keyboard;

import java.util.Random;

public final class HitParticleEffectsModule extends Module {

    public enum Mode {
        Vanilla("Vanilla"),
        Critical("Critical"),
        Redstone("Redstone"),
        Custom("Custom");

        private final String name;
        Mode(String name) { this.name = name; }
        @Override public String toString() { return name; }
    }

    private final EnumSetting<Mode> mode = new EnumSetting<>("Mode", Mode.values(), Mode.Vanilla);
    private final NumberSetting amount = new NumberSetting("Amount", 1, 20, 1, 5);
    private final BooleanSetting onlyCrits = new BooleanSetting("Only Crits", false);
    private final NumberSetting red = new NumberSetting("Red", 0, 255, 5, 255);
    private final NumberSetting green = new NumberSetting("Green", 0, 255, 5, 0);
    private final NumberSetting blue = new NumberSetting("Blue", 0, 255, 5, 0);

    private final Random random = new Random();

    public HitParticleEffectsModule() {
        super("HitParticleEffects", "Spawns custom visual particle effects when attacking entities.", Category.RENDER, Keyboard.KEY_NONE);
        addSetting(mode);
        addSetting(amount);
        addSetting(onlyCrits);
        addSetting(red);
        addSetting(green);
        addSetting(blue);
    }

    @SubscribeEvent
    public void onAttack(AttackEntityEvent event) {
        if (!isEnabled()) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null || mc.theWorld == null) return;
        if (event.entityPlayer != mc.thePlayer) return;
        if (!(event.target instanceof EntityLivingBase)) return;

        EntityLivingBase target = (EntityLivingBase) event.target;

        if (onlyCrits.isEnabled()) {
            boolean isCritical = mc.thePlayer.fallDistance > 0.0F
                && !mc.thePlayer.onGround
                && !mc.thePlayer.isOnLadder()
                && !mc.thePlayer.isInWater()
                && !mc.thePlayer.isRiding();
            if (!isCritical) return;
        }

        double x = target.posX;
        double y = target.posY + target.getEyeHeight() / 2.0;
        double z = target.posZ;

        int particleCount = amount.getValue();
        for (int i = 0; i < particleCount; i++) {
            double offsetX = random.nextGaussian() * 0.2;
            double offsetY = random.nextGaussian() * 0.2;
            double offsetZ = random.nextGaussian() * 0.2;

            switch (mode.getValue()) {
                case Vanilla:
                    mc.theWorld.spawnParticle(EnumParticleTypes.CRIT, x + offsetX, y + offsetY, z + offsetZ, 0.0, 0.0, 0.0);
                    break;
                case Critical:
                    mc.theWorld.spawnParticle(EnumParticleTypes.CRIT_MAGIC, x + offsetX, y + offsetY, z + offsetZ, 0.0, 0.0, 0.0);
                    break;
                case Redstone:
                case Custom:
                    double rColor = red.getValue() / 255.0;
                    double gColor = green.getValue() / 255.0;
                    double bColor = blue.getValue() / 255.0;
                    if (rColor == 0.0 && gColor == 0.0 && bColor == 0.0) {
                        rColor = 1.0;
                    }
                    mc.theWorld.spawnParticle(EnumParticleTypes.REDSTONE, x + offsetX, y + offsetY, z + offsetZ, rColor, gColor, bColor);
                    break;
            }
        }
    }
}
