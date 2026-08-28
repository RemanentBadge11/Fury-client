package com.lionclient.session;

import net.minecraft.client.Minecraft;
import net.minecraft.util.Session;
import net.minecraftforge.fml.common.ObfuscationReflectionHelper;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class SessionManager {
    private static final SessionManager INSTANCE = new SessionManager();
    private final List<String> recentAlts = new ArrayList<String>();
    private File altsFile;

    private SessionManager() {
        initFile();
        loadAlts();
    }

    public static SessionManager getInstance() {
        return INSTANCE;
    }

    private void initFile() {
        try {
            File dir = new File(Minecraft.getMinecraft().mcDataDir, "furyclient");
            if (!dir.exists()) {
                dir.mkdirs();
            }
            altsFile = new File(dir, "alts.txt");
        } catch (Throwable ignored) {}
    }

    public boolean setOfflineSession(String username) {
        if (username == null || username.trim().isEmpty()) {
            return false;
        }

        String cleanName = username.trim();
        if (cleanName.length() > 16) {
            cleanName = cleanName.substring(0, 16);
        }

        try {
            String uuidStr = UUID.nameUUIDFromBytes(("OfflinePlayer:" + cleanName).getBytes(StandardCharsets.UTF_8))
                    .toString().replace("-", "");
            UUID uuidObj = UUID.fromString(uuidStr.replaceFirst(
                    "(\\p{XDigit}{8})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}{12})",
                    "$1-$2-$3-$4-$5"
            ));

            Session newSession = new Session(cleanName, uuidStr, "0", "legacy");
            Minecraft mc = Minecraft.getMinecraft();

            // 1. Mutate mc.session
            try {
                ObfuscationReflectionHelper.setPrivateValue(Minecraft.class, mc, newSession, "field_71449_j", "session");
            } catch (Throwable t1) {
                try {
                    java.lang.reflect.Field field = Minecraft.class.getDeclaredField("session");
                    field.setAccessible(true);
                    field.set(mc, newSession);
                } catch (Throwable t2) {
                    java.lang.reflect.Field field = Minecraft.class.getDeclaredField("field_71449_j");
                    field.setAccessible(true);
                    field.set(mc, newSession);
                }
            }

            // 2. Synchronize mc.thePlayer GameProfile if in-game
            if (mc.thePlayer != null) {
                com.mojang.authlib.GameProfile newProfile = new com.mojang.authlib.GameProfile(uuidObj, cleanName);
                try {
                    java.lang.reflect.Field gameProfileField = net.minecraft.entity.player.EntityPlayer.class.getDeclaredField("gameProfile");
                    gameProfileField.setAccessible(true);
                    gameProfileField.set(mc.thePlayer, newProfile);
                } catch (Throwable t1) {
                    try {
                        java.lang.reflect.Field gameProfileField = net.minecraft.entity.player.EntityPlayer.class.getDeclaredField("field_146106_i");
                        gameProfileField.setAccessible(true);
                        gameProfileField.set(mc.thePlayer, newProfile);
                    } catch (Throwable ignored) {}
                }
            }

            // 3. If connected to a multiplayer server, auto-reconnect to establish new handshake with proxy
            net.minecraft.client.multiplayer.ServerData serverData = mc.getCurrentServerData();
            if (mc.theWorld != null && serverData != null) {
                final net.minecraft.client.multiplayer.ServerData currentServer = serverData;
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            Thread.sleep(100L);
                            lion.client.forge.MCAccess.runOnClientThread(new Runnable() {
                                @Override
                                public void run() {
                                    try {
                                        if (Minecraft.getMinecraft().theWorld != null) {
                                            Minecraft.getMinecraft().theWorld.sendQuittingDisconnectingPacket();
                                        }
                                        Minecraft.getMinecraft().loadWorld(null);
                                        Minecraft.getMinecraft().displayGuiScreen(new net.minecraft.client.multiplayer.GuiConnecting(
                                                new net.minecraft.client.gui.GuiMultiplayer(new net.minecraft.client.gui.GuiMainMenu()),
                                                Minecraft.getMinecraft(), currentServer));
                                    } catch (Throwable t) {
                                        lion.client.ClientLogger.error("[FuryClient] Reconnect error", t);
                                    }
                                }
                            });
                        } catch (Throwable ignored) {}
                    }
                }, "FuryClient-SessionReconnect").start();
            }

            addRecentAlt(cleanName);
            return true;
        } catch (Throwable t) {
            lion.client.ClientLogger.error("[FuryClient] Failed to swap session to " + cleanName, t);
            return false;
        }
    }

    public String getCurrentUsername() {
        try {
            Minecraft mc = Minecraft.getMinecraft();
            if (mc != null && mc.getSession() != null) {
                return mc.getSession().getUsername();
            }
        } catch (Throwable ignored) {}
        return "Player";
    }

    public List<String> getRecentAlts() {
        return new ArrayList<String>(recentAlts);
    }

    public void addRecentAlt(String username) {
        if (username == null || username.trim().isEmpty()) return;
        String name = username.trim();
        recentAlts.remove(name);
        recentAlts.add(0, name);
        if (recentAlts.size() > 20) {
            recentAlts.remove(recentAlts.size() - 1);
        }
        saveAlts();
    }

    private void loadAlts() {
        if (altsFile == null || !altsFile.exists()) return;
        try (FileReader reader = new FileReader(altsFile)) {
            java.io.BufferedReader br = new java.io.BufferedReader(reader);
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (!line.isEmpty() && !recentAlts.contains(line)) {
                    recentAlts.add(line);
                }
            }
        } catch (Throwable ignored) {}
    }

    private void saveAlts() {
        if (altsFile == null) return;
        try (FileWriter writer = new FileWriter(altsFile)) {
            for (String alt : recentAlts) {
                writer.write(alt + "\n");
            }
        } catch (Throwable ignored) {}
    }
}
