package com.lionclient.feature.module.impl;

import com.lionclient.feature.module.Category;
import com.lionclient.feature.module.Module;
import com.lionclient.feature.setting.EnumSetting;
import com.lionclient.feature.setting.NumberSetting;
import com.lionclient.util.AnimationRenderHelper;
import com.lionclient.util.AnimationRenderHelper.AnimationMode;
import org.lwjgl.input.Keyboard;

public final class AnimationsModule extends Module {

    private static AnimationsModule instance;

    private final EnumSetting<AnimationMode> mode = new EnumSetting<>("Mode", AnimationMode.values(), AnimationMode.EXHIBITION);
    private final NumberSetting scale = new NumberSetting("Scale", 50, 150, 5, 100);
    private final NumberSetting swingSpeed = new NumberSetting("SwingSpeed", 0, 100, 5, 0);

    public AnimationsModule() {
        super("Animations", "Customizes first person player block and swing animations.", Category.RENDER, Keyboard.KEY_NONE);
        instance = this;
        addSetting(mode);
        addSetting(scale);
        addSetting(swingSpeed);
    }

    public static AnimationsModule getInstance() {
        return instance;
    }

    public AnimationMode getMode() {
        return mode.getValue();
    }

    public int getScale() {
        return scale.getValue();
    }

    public int getSwingSpeed() {
        return swingSpeed.getValue();
    }

    @Override
    public String getHudInfo() {
        return mode.getValue().toString();
    }
}
