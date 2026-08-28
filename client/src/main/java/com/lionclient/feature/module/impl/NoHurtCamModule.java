package com.lionclient.feature.module.impl;

import com.lionclient.feature.module.Category;
import com.lionclient.feature.module.Module;
import com.lionclient.feature.setting.NumberSetting;
import net.minecraft.client.Minecraft;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.input.Keyboard;

public final class NoHurtCamModule extends Module {

    private final NumberSetting multiplier = new NumberSetting("Multiplier", 0, 100, 5, 0);

    public NoHurtCamModule() {
        super("NoHurtCam", "Disables or reduces camera shake when taking damage.", Category.RENDER, Keyboard.KEY_NONE);
        addSetting(multiplier);
    }

    @Override
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START || !isEnabled()) return;

        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null) return;

        if (mc.thePlayer.hurtTime > 0) {
            int mult = multiplier.getValue();
            if (mult == 0) {
                mc.thePlayer.hurtTime = 0;
            } else if (mult < 100) {
                mc.thePlayer.hurtTime = (int) (mc.thePlayer.hurtTime * (mult / 100.0F));
            }
        }
    }

    @Override
    public String getHudInfo() {
        return multiplier.getValue() + "%";
    }
}
