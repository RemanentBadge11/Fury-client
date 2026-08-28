package com.lionclient.util;

import net.minecraft.client.Minecraft;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.IChatComponent;

public final class ChatUtil {
    private static final Minecraft mc = Minecraft.getMinecraft();

    private ChatUtil() {}

    public static void send(IChatComponent component) {
        if (mc != null && mc.thePlayer != null) {
            mc.thePlayer.addChatMessage(component);
        }
    }

    public static void sendFormatted(String text) {
        send(new ChatComponentText(formatColor(text)));
    }

    public static void sendRaw(String text) {
        send(new ChatComponentText(text));
    }

    public static void sendMessage(String text) {
        if (mc != null && mc.thePlayer != null) {
            mc.thePlayer.sendChatMessage(text);
        }
    }

    public static String formatColor(String text) {
        if (text == null) return "";
        char[] chars = text.toCharArray();
        for (int i = 0; i < chars.length - 1; ++i) {
            if (chars[i] == '&' && "0123456789AaBbCcDdEeFfKkLlMmNnOoRr".indexOf(chars[i + 1]) != -1) {
                chars[i] = '\u00A7';
                chars[i + 1] = Character.toLowerCase(chars[i + 1]);
            }
        }
        return new String(chars);
    }
}
