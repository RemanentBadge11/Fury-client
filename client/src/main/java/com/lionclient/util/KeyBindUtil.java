package com.lionclient.util;

import net.minecraft.client.settings.KeyBinding;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

public final class KeyBindUtil {

    private KeyBindUtil() {}

    public static String getKeyName(int keyCode) {
        if (keyCode == Keyboard.KEY_NONE) {
            return "None";
        }

        if (keyCode < 0) {
            int mouseButton = keyCode + 100;
            switch (mouseButton) {
                case 0:
                    return "LMB";
                case 1:
                    return "RMB";
                case 2:
                    return "MMB";
                case 3:
                    return "MOUSE3";
                case 4:
                    return "MOUSE4";
                case 5:
                    return "MOUSE5";
                case 6:
                    return "MOUSE6";
                case 7:
                    return "MOUSE7";
                default:
                    if (mouseButton >= 0 && mouseButton < Mouse.getButtonCount()) {
                        String buttonName = Mouse.getButtonName(mouseButton);
                        return buttonName != null ? buttonName : "MOUSE" + mouseButton;
                    }
                    return "MOUSE" + mouseButton;
            }
        }

        if (keyCode >= Keyboard.KEYBOARD_SIZE) {
            return "KEY" + keyCode;
        }

        String keyName = Keyboard.getKeyName(keyCode);
        return keyName != null ? keyName : "KEY" + keyCode;
    }

    public static boolean isKeyDown(int keyCode) {
        if (keyCode == Keyboard.KEY_NONE) {
            return false;
        }

        if (keyCode < 0) {
            int button = keyCode + 100;
            if (button >= 0 && button < Mouse.getButtonCount()) {
                return Mouse.isButtonDown(button);
            }
            return false;
        }

        if (keyCode < Keyboard.KEYBOARD_SIZE) {
            return Keyboard.isKeyDown(keyCode);
        }

        return false;
    }

    public static void setKeyBindState(int keyCode, boolean pressed) {
        KeyBinding.setKeyBindState(keyCode, pressed);
    }

    public static void pressKeyOnce(int keyCode) {
        KeyBinding.onTick(keyCode);
    }
}
