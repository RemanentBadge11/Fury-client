package com.lionclient.command.commands;

import com.lionclient.LionClient;
import com.lionclient.command.Command;
import com.lionclient.feature.module.Module;
import com.lionclient.util.ChatUtil;
import com.lionclient.util.KeyBindUtil;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

public class BindCommand extends Command {

    public BindCommand() {
        super(new ArrayList<String>(Arrays.asList("bind", "b")));
    }

    @Override
    public void runCommand(ArrayList<String> args) {
        String clientPrefix = "&8[&c" + LionClient.NAME + "&8]&r ";

        if (args.size() < 3) {
            if (args.size() == 2 && (args.get(1).equalsIgnoreCase("l") || args.get(1).equalsIgnoreCase("list"))) {
                List<Module> modules = LionClient.getInstance().getModuleManager().getModules().stream()
                        .filter(m -> m.getKeyCode() != Keyboard.KEY_NONE)
                        .collect(Collectors.toList());

                if (modules.isEmpty()) {
                    ChatUtil.sendFormatted(clientPrefix + "&7No binds set.&r");
                } else {
                    ChatUtil.sendFormatted(clientPrefix + "&7Active Binds:&r");
                    for (Module module : modules) {
                        ChatUtil.sendFormatted(String.format("&7» &f%s&7: &c[%s]&r",
                                module.getName(), KeyBindUtil.getKeyName(module.getKeyCode())));
                    }
                }
            } else {
                ChatUtil.sendFormatted(clientPrefix + String.format(
                        "&7Usage: .%s <module> <key> | .%s <module> none | .%s list&r",
                        args.get(0).toLowerCase(Locale.ROOT),
                        args.get(0).toLowerCase(Locale.ROOT),
                        args.get(0).toLowerCase(Locale.ROOT)
                ));
            }
        } else {
            String keyInput = args.get(2).toUpperCase();
            int keyIndex = 0;

            if (keyInput.equalsIgnoreCase("NONE") || keyInput.equalsIgnoreCase("NULL") || keyInput.equalsIgnoreCase("0")) {
                keyIndex = Keyboard.KEY_NONE;
            } else {
                keyIndex = Keyboard.getKeyIndex(keyInput);

                if (keyIndex == Keyboard.KEY_NONE) {
                    int buttonIndex = getMouseButtonIndex(keyInput);
                    if (buttonIndex != -1) {
                        keyIndex = buttonIndex - 100;
                    }
                }
            }

            if (!args.get(1).equals("*")) {
                Module module = LionClient.getInstance().getModuleManager().getModule(args.get(1));
                if (module == null) {
                    ChatUtil.sendFormatted(clientPrefix + String.format("&cModule not found (&o%s&c)&r", args.get(1)));
                } else {
                    module.setKeyCode(keyIndex);
                    if (keyIndex == Keyboard.KEY_NONE) {
                        ChatUtil.sendFormatted(clientPrefix + String.format("&7Unbound &f%s&r", module.getName()));
                    } else {
                        ChatUtil.sendFormatted(clientPrefix + String.format("&7Bound &f%s&7 to &c[%s]&r",
                                module.getName(), KeyBindUtil.getKeyName(keyIndex)));
                    }
                }
            } else {
                for (Module module : LionClient.getInstance().getModuleManager().getModules()) {
                    module.setKeyCode(keyIndex);
                }
                if (keyIndex == Keyboard.KEY_NONE) {
                    ChatUtil.sendFormatted(clientPrefix + "&7Unbound all modules.&r");
                } else {
                    ChatUtil.sendFormatted(clientPrefix + String.format("&7Bound all modules to &c[%s]&r",
                            KeyBindUtil.getKeyName(keyIndex)));
                }
            }
        }
    }

    private int getMouseButtonIndex(String buttonName) {
        if (buttonName.startsWith("MOUSE")) {
            try {
                String numStr = buttonName.substring(5);
                int buttonNum = Integer.parseInt(numStr);
                if (buttonNum >= 0 && buttonNum < Mouse.getButtonCount()) {
                    return buttonNum;
                }
            } catch (NumberFormatException | StringIndexOutOfBoundsException ignored) {}
        }

        int buttonIndex = Mouse.getButtonIndex(buttonName);
        if (buttonIndex != -1) {
            return buttonIndex;
        }

        switch (buttonName) {
            case "LBUTTON":
            case "LMB":
            case "LEFTCLICK":
                return 0;
            case "RBUTTON":
            case "RMB":
            case "RIGHTCLICK":
                return 1;
            case "MBUTTON":
            case "MMB":
            case "MIDDLECLICK":
            case "SCROLLCLICK":
                return 2;
            case "MOUSE3":
            case "XBUTTON1":
            case "SIDEBUTTON1":
            case "BOTTOMSIDE":
                return 3;
            case "MOUSE4":
            case "XBUTTON2":
            case "SIDEBUTTON2":
            case "TOPSIDE":
                return 4;
            case "MOUSE5":
                return 5;
            case "MOUSE6":
                return 6;
            case "MOUSE7":
                return 7;
            default:
                return -1;
        }
    }
}
