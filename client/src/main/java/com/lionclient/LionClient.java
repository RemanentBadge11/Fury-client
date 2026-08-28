package com.lionclient;

import com.lionclient.combat.lag.LagHandler;
import com.lionclient.feature.module.ModuleManager;
import com.lionclient.feature.module.impl.ClickGuiModule;
import com.lionclient.feature.module.impl.HudModule;
import com.lionclient.gui.ClickGuiScreen;
import com.lionclient.gui.HudEditorScreen;
import com.lionclient.gui.ModernClickGuiScreen;
import com.lionclient.input.KeybindHandler;
import com.lionclient.network.KnockbackDelayBuffer;
import com.lionclient.network.PacketStallHandler;

import net.minecraft.client.Minecraft;
import net.minecraftforge.client.event.MouseEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

public final class LionClient {
    public static final String MOD_ID  = "furyclient";
    public static final String NAME    = "FuryClient";
    public static final String VERSION = "1.0.5";

    static {
        registerFakeModContainer();
    }

    private static LionClient instance;

    private final ModuleManager moduleManager = new ModuleManager();
    private final com.lionclient.command.CommandManager commandManager = new com.lionclient.command.CommandManager();
    private final KnockbackDelayBuffer knockbackDelayBuffer = new KnockbackDelayBuffer();
    private final PacketStallHandler packetStallHandler = new PacketStallHandler();
    private final ClickGuiScreen       clickGuiScreen       = new ClickGuiScreen(moduleManager);
    private final ModernClickGuiScreen modernClickGuiScreen = new ModernClickGuiScreen(moduleManager);

    public static LionClient getInstance() { return instance; }

    public ModuleManager getModuleManager() { return moduleManager; }

    public com.lionclient.config.ConfigManager getConfigManager() { return moduleManager.getConfigManager(); }

    public com.lionclient.command.CommandManager getCommandManager() { return commandManager; }

    public KnockbackDelayBuffer getKnockbackDelayBuffer() { return knockbackDelayBuffer; }

    public PacketStallHandler getPacketStallHandler() { return packetStallHandler; }

    public void bootstrap() {
        instance = this;
        KeybindHandler.register(moduleManager);
        commandManager.registerEventBus();
        net.minecraftforge.fml.common.eventhandler.EventBus forgeBus =
                net.minecraftforge.common.MinecraftForge.EVENT_BUS;
        net.minecraftforge.fml.common.eventhandler.EventBus fmlBus =
                net.minecraftforge.fml.common.FMLCommonHandler.instance().bus();
        try {
            forgeBus.register(this);
        } catch (Throwable t) {
            lion.client.ClientLogger.error("[FuryClient] forgeBus.register failed", t);
        }
        if (fmlBus != forgeBus) {
            try {
                fmlBus.register(this);
            } catch (Throwable t) {
                lion.client.ClientLogger.error("[FuryClient] fmlBus.register failed", t);
            }
        }
        try {
            forgeBus.register(LagHandler.get());
        } catch (Throwable t) {
            lion.client.ClientLogger.error("[FuryClient] LagHandler register failed", t);
        }
    }

    private net.minecraft.client.gui.GuiScreen previousScreen = null;

    public void toggleClickGui() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.currentScreen == clickGuiScreen || mc.currentScreen == modernClickGuiScreen) {
            net.minecraft.client.gui.GuiScreen target = previousScreen;
            previousScreen = null;
            if (mc.theWorld == null && isIngameScreen(target)) {
                target = null;
            }
            mc.displayGuiScreen(target);
            return;
        }
        previousScreen = mc.currentScreen;
        net.minecraft.client.gui.GuiScreen target = ClickGuiModule.getGuiStyle() == ClickGuiModule.GuiStyle.CLASSIC
                ? clickGuiScreen
                : modernClickGuiScreen;
        mc.displayGuiScreen(target);
    }

    private static boolean isIngameScreen(net.minecraft.client.gui.GuiScreen screen) {
        if (screen == null) return false;
        return screen instanceof net.minecraft.client.gui.GuiIngameMenu
            || screen instanceof net.minecraft.client.gui.GuiChat
            || screen instanceof net.minecraft.client.gui.inventory.GuiContainer;
    }

    public void refreshClickGuiStyle() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.currentScreen != clickGuiScreen && mc.currentScreen != modernClickGuiScreen) return;
        mc.displayGuiScreen(ClickGuiModule.getGuiStyle() == ClickGuiModule.GuiStyle.CLASSIC
                ? clickGuiScreen
                : modernClickGuiScreen);
    }

    public void openHudEditor() {
        HudModule hud = HudModule.getInstance();
        if (hud == null) return;
        Minecraft.getMinecraft().displayGuiScreen(new HudEditorScreen(hud));
    }

    public void resetClientState() {
        previousScreen = null;
        try { com.lionclient.feature.module.impl.AimAssistModule.target = null; } catch (Throwable ignored) {}
        try {
            com.lionclient.feature.module.impl.BackTrackModule backtrack = moduleManager != null ? moduleManager.getModule(com.lionclient.feature.module.impl.BackTrackModule.class) : null;
            if (backtrack != null) backtrack.clear();
        } catch (Throwable ignored) {}
        try { LagHandler.get().clear(); } catch (Throwable ignored) {}
        try { if (packetStallHandler != null) packetStallHandler.clear(); } catch (Throwable ignored) {}
    }

    @SubscribeEvent
    public void onWorldUnload(net.minecraftforge.event.world.WorldEvent.Unload event) {
        resetClientState();
    }

    @SubscribeEvent
    public void onDisconnect(net.minecraftforge.fml.common.network.FMLNetworkEvent.ClientDisconnectionFromServerEvent event) {
        resetClientState();
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null || mc.theWorld == null) {
            resetClientState();
            return;
        }
        moduleManager.onClientTick(event);
        if (event.phase == TickEvent.Phase.START) {
            try { com.lionclient.combat.ClientRotationHelper.get().updateServerRotations(); } catch (Throwable ignored) {}
            try { com.lionclient.combat.ClientRotationHelper.get().onRunTickStart(); } catch (Throwable ignored) {}
            try {
                if (com.lionclient.combat.ClientRotationHelper.get().tryClaimPrePlayerInteractPost()) {
                    net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(new com.lionclient.event.PrePlayerInteractEvent());
                }
            } catch (Throwable ignored) {}
            return;
        }
        moduleManager.onClientTick();
        try { LagHandler.get().onGameTick(); } catch (Throwable ignored) {}
        try { knockbackDelayBuffer.onClientTick(); } catch (Throwable ignored) {}
        try { packetStallHandler.onTick(); } catch (Throwable ignored) {}
        try { com.lionclient.combat.ClientRotationHelper.get().restoreTickSwap(); } catch (Throwable ignored) {}
        try { com.lionclient.combat.ClientRotationHelper.get().endOfTickReset(); } catch (Throwable ignored) {}
    }

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null || mc.theWorld == null) return;
        moduleManager.onPlayerTick(event);
        if (event.phase == TickEvent.Phase.START && event.player == Minecraft.getMinecraft().thePlayer) {
            try { com.lionclient.combat.ClientRotationHelper.get().applyTickSwap(event.player); } catch (Throwable ignored) {}
        }
    }

    @SubscribeEvent
    public void onMouseInput(MouseEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null || mc.theWorld == null) return;
        moduleManager.onMouseEvent(event);
    }

    @SubscribeEvent
    public void onPlayerJump(LivingEvent.LivingJumpEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null || mc.theWorld == null) return;
        moduleManager.onPlayerJump(event);
    }

    @SubscribeEvent
    public void onPrePlayerInput(com.lionclient.event.PrePlayerInputEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null || mc.theWorld == null) return;
        moduleManager.onPrePlayerInput(event);
    }

    @SubscribeEvent
    public void onRenderTick(TickEvent.RenderTickEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null || mc.theWorld == null) return;
        moduleManager.onRenderTick(event);
    }

    @SubscribeEvent
    public void onRenderWorld(RenderWorldLastEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null || mc.theWorld == null) return;
        moduleManager.onRenderWorld(event);
    }

    @SubscribeEvent
    public void onRenderOverlay(RenderGameOverlayEvent.Text event) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null || mc.theWorld == null) return;
        moduleManager.onRenderOverlay(event);
    }

    @SuppressWarnings("unchecked")
    private static void registerFakeModContainer() {
        try {
            Class<?> loaderCls    = Class.forName("net.minecraftforge.fml.common.Loader");
            Class<?> containerCls = Class.forName("net.minecraftforge.fml.common.ModContainer");
            Object   loader       = loaderCls.getMethod("instance").invoke(null);

            final java.io.File src = resolveOurJarFile();

            final Object fake = java.lang.reflect.Proxy.newProxyInstance(
                    LionClient.class.getClassLoader(),
                    new Class<?>[]{ containerCls },
                    (proxy, method, args) -> {
                        switch (method.getName()) {
                            case "getSource":            return src;
                            case "getModId":             return MOD_ID;
                            case "getName":              return NAME;
                            case "getVersion":           return VERSION;
                            case "isImmutable":          return true;
                            case "getOwnedPackages":     return java.util.Collections.emptyList();
                            case "getRequirements":      return java.util.Collections.emptySet();
                            case "getDependencies":      return java.util.Collections.emptyList();
                            case "getDependants":        return java.util.Collections.emptyList();
                            case "getCustomModProperties":  return java.util.Collections.emptyMap();
                            case "getSharedModDescriptor":  return java.util.Collections.emptyMap();
                            case "getMod":
                            case "getModObject":         return LionClient.getInstance();
                            case "matches":              return args != null && args.length > 0 && args[0] == LionClient.getInstance();
                            case "getMetadata":
                                try {
                                    Class<?> metaCls = Class.forName("net.minecraftforge.fml.common.ModMetadata");
                                    Object meta = metaCls.newInstance();
                                    metaCls.getField("modId").set(meta, MOD_ID);
                                    metaCls.getField("name").set(meta, NAME);
                                    metaCls.getField("version").set(meta, VERSION);
                                    return meta;
                                } catch (Throwable t) { return null; }
                            default:
                                Class<?> rt = method.getReturnType();
                                if (rt == boolean.class || rt == Boolean.class) return false;
                                if (rt == int.class    || rt == Integer.class)  return 0;
                                return null;
                        }
                    });

            try {
                java.util.List<Object> list =
                        (java.util.List<Object>) loaderCls.getMethod("getActiveModList").invoke(loader);
                if (!list.contains(fake)) list.add(fake);
            } catch (Throwable ignored) {}

            java.lang.reflect.Field modControllerField = loaderCls.getDeclaredField("modController");
            modControllerField.setAccessible(true);
            Object modController = modControllerField.get(loader);
            if (modController == null) {
                lion.client.ClientLogger.error("[FakeMod] modController is null on Loader", null);
                return;
            }

            try {
                java.lang.reflect.Field activeContainerField =
                        modController.getClass().getDeclaredField("activeContainer");
                activeContainerField.setAccessible(true);
                activeContainerField.set(modController, fake);
                Object verify = activeContainerField.get(modController);
                lion.client.ClientLogger.info("[FakeMod] activeContainer set, verify=" + (verify == fake));
            } catch (Throwable t) {
                lion.client.ClientLogger.error("[FakeMod] set activeContainer failed", t);
            }

            try {
                java.lang.reflect.Field packageOwnersField =
                        modController.getClass().getDeclaredField("packageOwners");
                packageOwnersField.setAccessible(true);
                Object packageOwners = packageOwnersField.get(modController);
                if (packageOwners != null) {
                    java.lang.reflect.Method put = null;
                    for (java.lang.reflect.Method m : packageOwners.getClass().getMethods()) {
                        if ("put".equals(m.getName()) && m.getParameterTypes().length == 2) {
                            put = m;
                            break;
                        }
                    }
                    if (put != null) {
                        java.util.Set<String> pkgs = scanJarPackages(src);
                        for (String pkg : pkgs) put.invoke(packageOwners, pkg, fake);
                        lion.client.ClientLogger.info("[FakeMod] added " + pkgs.size() + " packages to packageOwners");
                    }
                }
            } catch (Throwable t) {
                lion.client.ClientLogger.error("[FakeMod] populate packageOwners failed", t);
            }

            try {
                java.lang.reflect.Field eventChannelsField = modController.getClass().getDeclaredField("eventChannels");
                eventChannelsField.setAccessible(true);
                Object currentEventChannels = eventChannelsField.get(modController);
                if (currentEventChannels instanceof java.util.Map) {
                    java.util.Map<String, Object> newMap = new java.util.HashMap<>((java.util.Map<String, Object>) currentEventChannels);
                    Class<?> eventBusCls = Class.forName("com.google.common.eventbus.EventBus");
                    Object dummyBus = eventBusCls.getConstructor(String.class).newInstance("FakeModEventBus");
                    newMap.put(MOD_ID, dummyBus);
                    
                    Class<?> immutableMapCls = Class.forName("com.google.common.collect.ImmutableMap");
                    java.lang.reflect.Method copyOf = immutableMapCls.getMethod("copyOf", java.util.Map.class);
                    Object newImmutableMap = copyOf.invoke(null, newMap);
                    
                    eventChannelsField.set(modController, newImmutableMap);
                    lion.client.ClientLogger.info("[FakeMod] added EventBus to eventChannels");
                }
            } catch (Throwable t) {
                lion.client.ClientLogger.error("[FakeMod] populate eventChannels failed", t);
            }
        } catch (Throwable t) {
            try { lion.client.ClientLogger.error("[FakeMod] registerFakeModContainer failed", t); }
            catch (Throwable ignored) {}
        }
    }

    private static java.io.File resolveOurJarFile() {
        try {
            java.net.URL url = LionClient.class.getProtectionDomain().getCodeSource().getLocation();
            if (url == null) return null;
            String s = url.toString();
            if (s.startsWith("jar:")) {
                s = s.substring(4);
                int bang = s.indexOf("!/");
                if (bang >= 0) s = s.substring(0, bang);
            }
            if (s.startsWith("file:")) {
                try { return new java.io.File(new java.net.URI(s)); }
                catch (Throwable ignored) {
                    String path = s.substring(5);
                    while (path.startsWith("/")) path = path.substring(1);
                    return new java.io.File(java.net.URLDecoder.decode(path, "UTF-8"));
                }
            }
            return new java.io.File(s);
        } catch (Throwable t) {
            try { lion.client.ClientLogger.error("[FakeMod] resolveOurJarFile failed", t); }
            catch (Throwable ignored) {}
            return null;
        }
    }

    private static java.util.Set<String> scanJarPackages(java.io.File jarFile) {
        java.util.Set<String> packages = new java.util.HashSet<>();
        if (jarFile == null || !jarFile.isFile()) return packages;
        try (java.util.jar.JarFile jar = new java.util.jar.JarFile(jarFile)) {
            java.util.Enumeration<java.util.jar.JarEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                String name = entries.nextElement().getName();
                if (!name.endsWith(".class")) continue;
                int slash = name.lastIndexOf('/');
                if (slash <= 0) continue;
                String pkg = name.substring(0, slash).replace('/', '.');
                if (pkg.startsWith("com.lionclient") || pkg.startsWith("lion.client")) {
                    packages.add(pkg);
                }
            }
        } catch (Throwable ignored) {}
        return packages;
    }
}
