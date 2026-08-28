package com.lionclient.feature.module.impl;

import com.lionclient.feature.module.Category;
import com.lionclient.feature.module.Module;
import com.lionclient.feature.setting.BooleanSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import org.lwjgl.input.Keyboard;

public final class SafeWalkModule extends Module {
    private final BooleanSetting blocksOnly = new BooleanSetting("Blocks Only", false);
    private final BooleanSetting pitchCheck = new BooleanSetting("Pitch Check", false);
    private final BooleanSetting disableOnForward = new BooleanSetting("Disable on Forward", false);

    public SafeWalkModule() {
        super("SafeWalk", "Prevents you from falling off the edge of blocks.", Category.MOVEMENT, Keyboard.KEY_NONE);
        addSetting(blocksOnly);
        addSetting(pitchCheck);
        addSetting(disableOnForward);
    }

    public boolean canSafeWalk() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null) {
            return false;
        }

        if (disableOnForward.isEnabled() && mc.gameSettings.keyBindForward.isKeyDown()) {
            return false;
        }

        if (pitchCheck.isEnabled() && mc.thePlayer.rotationPitch < 70.0F) {
            return false;
        }

        if (blocksOnly.isEnabled()) {
            ItemStack held = mc.thePlayer.getHeldItem();
            if (held == null || !(held.getItem() instanceof ItemBlock)) {
                return false;
            }
        }

        return true;
    }
}
