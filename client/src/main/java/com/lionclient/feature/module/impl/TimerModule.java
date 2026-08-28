package com.lionclient.feature.module.impl;

import com.lionclient.feature.module.Category;
import com.lionclient.feature.module.Module;
import com.lionclient.feature.setting.DecimalSetting;
import net.minecraft.client.Minecraft;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.input.Keyboard;

import java.lang.reflect.Field;

public final class TimerModule extends Module {

    private final DecimalSetting speed = new DecimalSetting("Speed", 0.1D, 10.0D, 0.1D, 1.0D);

    private static Field timerField = null;

    static {
        try {
            timerField = Minecraft.class.getDeclaredField("timer");
        } catch (NoSuchFieldException e) {
            try {
                timerField = Minecraft.class.getDeclaredField("field_71428_T");
            } catch (NoSuchFieldException ignored) {}
        }
        if (timerField != null) {
            timerField.setAccessible(true);
        }
    }

    public TimerModule() {
        super("Timer", "Modifies client game tick speed.", Category.MOVEMENT, Keyboard.KEY_NONE);
        addSetting(speed);
    }

    @Override
    protected void onDisable() {
        resetTimerSpeed();
    }

    @Override
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;

        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null || mc.theWorld == null) {
            resetTimerSpeed();
            return;
        }

        if (isEnabled()) {
            setTimerSpeed(mc, (float) speed.getValue());
        } else {
            resetTimerSpeed();
        }
    }

    private void setTimerSpeed(Minecraft mc, float timerSpeed) {
        net.minecraft.util.Timer timer = getTimer(mc);
        if (timer != null) {
            timer.timerSpeed = timerSpeed;
        }
    }

    private void resetTimerSpeed() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc != null) {
            net.minecraft.util.Timer timer = getTimer(mc);
            if (timer != null) {
                timer.timerSpeed = 1.0F;
            }
        }
    }

    private net.minecraft.util.Timer getTimer(Minecraft mc) {
        if (timerField == null || mc == null) return null;
        try {
            return (net.minecraft.util.Timer) timerField.get(mc);
        } catch (Exception e) {
            return null;
        }
    }
}
