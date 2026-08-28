package com.lionclient.command.commands;

import com.lionclient.LionClient;
import com.lionclient.command.Command;
import com.lionclient.session.SessionManager;
import com.lionclient.util.ChatUtil;

import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.util.ArrayList;
import java.util.Arrays;

public class IgnCommand extends Command {

    public IgnCommand() {
        super(new ArrayList<String>(Arrays.asList("ign", "username", "myname")));
    }

    @Override
    public void runCommand(ArrayList<String> args) {
        String clientPrefix = "&8[&c" + LionClient.NAME + "&8]&r ";
        String username = SessionManager.getInstance().getCurrentUsername();

        try {
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(username), null);
            ChatUtil.sendFormatted(clientPrefix + String.format("&7Your username has been copied to clipboard (&a%s&7)&r", username));
        } catch (Throwable t) {
            ChatUtil.sendFormatted(clientPrefix + "&cFailed to copy username to clipboard.&r");
        }
    }
}
