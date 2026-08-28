package com.lionclient.util;

import java.lang.reflect.Method;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import org.lwjgl.input.Mouse;

public final class ClickerInventoryBridge {
    
    private ClickerInventoryBridge() {}

    public static void doInventoryClick(GuiScreen guiScreen, Method clickMethod, long delay) {
        if (guiScreen == null || clickMethod == null) {
            return;
        }

        Minecraft mc = Minecraft.getMinecraft();
        int mouseX = Mouse.getX() * guiScreen.width / mc.displayWidth;
        int mouseY = guiScreen.height - Mouse.getY() * guiScreen.height / mc.displayHeight - 1;

        try {
            clickMethod.invoke(guiScreen, Integer.valueOf(mouseX), Integer.valueOf(mouseY), Integer.valueOf(0));
        } catch (Throwable ignored) {
        }
    }
}
