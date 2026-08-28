package com.lionclient.command.commands;

import com.lionclient.LionClient;
import com.lionclient.command.Command;
import com.lionclient.session.SessionManager;
import com.lionclient.util.ChatUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public class NickCommand extends Command {

    public NickCommand() {
        super(new ArrayList<String>(Arrays.asList("nick", "alt", "account", "name")));
    }

    @Override
    public void runCommand(ArrayList<String> args) {
        String clientPrefix = "&8[&c" + LionClient.NAME + "&8]&r ";

        if (args.size() < 2) {
            ChatUtil.sendFormatted(clientPrefix + String.format(
                    "&7Usage: .%s <newname> | .%s list&r",
                    args.get(0).toLowerCase(Locale.ROOT),
                    args.get(0).toLowerCase(Locale.ROOT)
            ));
            return;
        }

        String sub = args.get(1);

        if (sub.equalsIgnoreCase("list") || sub.equalsIgnoreCase("l")) {
            List<String> alts = SessionManager.getInstance().getRecentAlts();
            if (alts.isEmpty()) {
                ChatUtil.sendFormatted(clientPrefix + "&7No recent nicknames saved.&r");
            } else {
                ChatUtil.sendFormatted(clientPrefix + "&7Recent Nicknames:&r");
                String current = SessionManager.getInstance().getCurrentUsername();
                for (String alt : alts) {
                    boolean isCurrent = alt.equalsIgnoreCase(current);
                    ChatUtil.sendFormatted(String.format("&7» &f%s %s&r", alt, isCurrent ? "&a(Active)" : ""));
                }
            }
            return;
        }

        boolean success = SessionManager.getInstance().setOfflineSession(sub);
        if (success) {
            ChatUtil.sendFormatted(clientPrefix + String.format("&7Changed session username to &a%s&r", SessionManager.getInstance().getCurrentUsername()));
        } else {
            ChatUtil.sendFormatted(clientPrefix + "&cFailed to swap session username.&r");
        }
    }
}
