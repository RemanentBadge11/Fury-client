package com.lionclient.command.commands;

import com.lionclient.LionClient;
import com.lionclient.command.Command;
import com.lionclient.config.ConfigManager;
import com.lionclient.util.ChatUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ConfigCommand extends Command {

    public ConfigCommand() {
        super(new ArrayList<String>(Arrays.asList("config", "cfg", "preset", "profile")));
    }

    @Override
    public void runCommand(ArrayList<String> args) {
        String clientPrefix = "&8[&c" + LionClient.NAME + "&8]&r ";
        ConfigManager cm = LionClient.getInstance().getConfigManager();

        if (args.size() < 2) {
            ChatUtil.sendFormatted(clientPrefix + "&7Config Usage:&r");
            ChatUtil.sendFormatted("&7» &f.config load <name>&7 - Load profile&r");
            ChatUtil.sendFormatted("&7» &f.config save <name>&7 - Save active profile&r");
            ChatUtil.sendFormatted("&7» &f.config list&7 - List all saved profiles&r");
            ChatUtil.sendFormatted("&7» &f.config delete <name>&7 - Delete profile&r");
            ChatUtil.sendFormatted("&7» &f.config folder&7 - Open config directory&r");
            return;
        }

        String action = args.get(1).toLowerCase();

        if (action.equals("list") || action.equals("l")) {
            List<String> configs = cm.listConfigs();
            if (configs.isEmpty()) {
                ChatUtil.sendFormatted(clientPrefix + "&7No saved config profiles found.&r");
            } else {
                ChatUtil.sendFormatted(clientPrefix + "&7Available Config Profiles (&a" + configs.size() + "&7):&r");
                String active = cm.getCurrentConfigName();
                for (String name : configs) {
                    boolean isActive = name.equalsIgnoreCase(active);
                    ChatUtil.sendFormatted(String.format("&7» &f%s %s&r", name, isActive ? "&a(Active)" : ""));
                }
            }
            return;
        }

        if (action.equals("folder") || action.equals("dir") || action.equals("open")) {
            cm.openFolder();
            ChatUtil.sendFormatted(clientPrefix + "&7Opened config folder in Windows Explorer.&r");
            return;
        }

        if (args.size() < 3 && (action.equals("load") || action.equals("save") || action.equals("delete") || action.equals("remove"))) {
            ChatUtil.sendFormatted(clientPrefix + "&cSpecify a config profile name.&r");
            return;
        }

        if (action.equals("save") || action.equals("s")) {
            String name = args.get(2);
            cm.saveAs(name);
            ChatUtil.sendFormatted(clientPrefix + String.format("&aSaved config profile (&o%s.json&r&a)&r", name));
            return;
        }

        if (action.equals("delete") || action.equals("remove") || action.equals("del")) {
            String name = args.get(2);
            boolean deleted = cm.deleteConfig(name);
            if (deleted) {
                ChatUtil.sendFormatted(clientPrefix + String.format("&7Deleted config profile (&c%s.json&r&7)&r", name));
            } else {
                ChatUtil.sendFormatted(clientPrefix + String.format("&cFailed to delete config profile (&o%s.json&r&c)&r", name));
            }
            return;
        }

        if (action.equals("load")) {
            String name = args.get(2);
            cm.load(name);
            return;
        }

        // Direct load shorthand: .config <name> or .cfg <name>
        String directName = args.get(1);
        cm.load(directName);
    }
}
