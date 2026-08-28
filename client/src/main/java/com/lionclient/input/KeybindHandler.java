package com.lionclient.input;

import com.lionclient.feature.module.Module;
import com.lionclient.feature.module.ModuleManager;
import com.lionclient.gui.ClickGuiScreen;
import com.lionclient.gui.ModernClickGuiScreen;
import net.minecraft.client.Minecraft;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.input.Keyboard;

import com.lionclient.feature.module.impl.ClickGuiModule;

import com.lionclient.util.KeyBindUtil;
import java.util.HashMap;
import java.util.Map;

public final class KeybindHandler {

    private final ModuleManager moduleManager;
    private final Map<Integer, Boolean> prevDownMap = new HashMap<Integer, Boolean>();

    private KeybindHandler(ModuleManager moduleManager) {
        this.moduleManager = moduleManager;
    }

    public static void register(ModuleManager moduleManager) {
        net.minecraftforge.fml.common.eventhandler.EventBus bus =
                FMLCommonHandler.instance().bus();
        bus.register(new KeybindHandler(moduleManager));
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        Minecraft mc = Minecraft.getMinecraft();

        for (Module module : moduleManager.getModules()) {
            int kc = module.getKeyCode();
            if (kc == Keyboard.KEY_NONE) {
                prevDownMap.remove(kc);
                continue;
            }

            boolean wasDown = Boolean.TRUE.equals(prevDownMap.get(kc));

            if (!shouldDispatch(mc, module)) {
                prevDownMap.put(kc, false);
                continue;
            }

            boolean down = KeyBindUtil.isKeyDown(kc);
            boolean pressedEdge = down && !wasDown;

            if (module.handlesOwnKeybind()) {
                if (down != wasDown) {
                    module.onKeybind(down, pressedEdge);
                }
            } else if (pressedEdge) {
                module.toggle();
            }

            prevDownMap.put(kc, down);
        }
    }

    private static boolean shouldDispatch(Minecraft mc, Module module) {
        if (mc == null) return false;
        if (module instanceof ClickGuiModule) return true;
        Object screen = mc.currentScreen;
        if (screen == null) return true;
        if (screen instanceof ClickGuiScreen)       return true;
        if (screen instanceof ModernClickGuiScreen) return true;
        return false;
    }
}
