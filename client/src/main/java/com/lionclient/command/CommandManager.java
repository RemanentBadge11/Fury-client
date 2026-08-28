package com.lionclient.command;

import com.lionclient.LionClient;
import com.lionclient.command.commands.BindCommand;
import com.lionclient.command.commands.ConfigCommand;
import com.lionclient.command.commands.IgnCommand;
import com.lionclient.command.commands.NickCommand;
import com.lionclient.event.SendPacketEvent;
import com.lionclient.util.ChatUtil;
import net.minecraft.network.play.client.C01PacketChatMessage;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class CommandManager {
    private final List<Command> commands = new ArrayList<Command>();

    public CommandManager() {
        commands.add(new BindCommand());
        commands.add(new NickCommand());
        commands.add(new IgnCommand());
        commands.add(new ConfigCommand());
    }

    public void registerEventBus() {
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void onSendPacket(SendPacketEvent event) {
        if (event.getPacket() instanceof C01PacketChatMessage) {
            C01PacketChatMessage chatPacket = (C01PacketChatMessage) event.getPacket();
            String msg = chatPacket.getMessage();
            if (isTypingCommand(msg)) {
                event.setCanceled(true);
                handleCommand(msg);
            }
        }
    }

    public boolean isTypingCommand(String string) {
        if (string == null || string.length() < 2) {
            return false;
        }
        return string.charAt(0) == '.' && Character.isLetterOrDigit(string.charAt(1));
    }

    public void handleCommand(String input) {
        List<String> params = Arrays.asList(input.substring(1).trim().split("\\s+"));
        ArrayList<String> args = new ArrayList<String>(params);
        if (params.get(0).isEmpty()) {
            ChatUtil.sendFormatted("&8[&c" + LionClient.NAME + "&8]&r Unknown command");
            return;
        }

        for (Command command : commands) {
            for (String name : command.getNames()) {
                if (params.get(0).equalsIgnoreCase(name)) {
                    command.runCommand(args);
                    return;
                }
            }
        }
        ChatUtil.sendFormatted("&8[&c" + LionClient.NAME + "&8]&r Unknown command (&o" + params.get(0) + "&r)");
    }
}
