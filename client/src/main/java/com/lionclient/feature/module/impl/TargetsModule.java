package com.lionclient.feature.module.impl;

import com.lionclient.feature.module.Category;
import com.lionclient.feature.module.Module;
import com.lionclient.feature.setting.BooleanSetting;
import org.lwjgl.input.Keyboard;

public final class TargetsModule extends Module {
    private static TargetsModule instance;

    // Target Options
    private final BooleanSetting targetPlayers = new BooleanSetting("Players", true);
    private final BooleanSetting targetInvisibles = new BooleanSetting("Invisibles", true);
    private final BooleanSetting targetDead = new BooleanSetting("Dead Players", false);

    // Anti-Bot Options
    private final BooleanSetting tabListCheck = new BooleanSetting("Tab List Check", true);
    private final BooleanSetting pingCheck = new BooleanSetting("Ping Check", true);
    private final BooleanSetting npcNameFilter = new BooleanSetting("NPC Name Filter", true);
    private final BooleanSetting watchdogFilter = new BooleanSetting("Watchdog Bot Filter", true);

    // Team Options
    private final BooleanSetting checkScoreboardTeam = new BooleanSetting("Scoreboard Team", true);
    private final BooleanSetting checkNameColor = new BooleanSetting("Name Color", true);
    private final BooleanSetting checkArmorColor = new BooleanSetting("Armor Color", true);

    public TargetsModule() {
        super("Targets", "Universal target filtering & Anti-Bot rules for combat modules.", Category.COMBAT, Keyboard.KEY_NONE);
        instance = this;
        setEnabled(true);

        addSetting(targetPlayers);
        addSetting(targetInvisibles);
        addSetting(targetDead);

        addSetting(tabListCheck);
        addSetting(pingCheck);
        addSetting(npcNameFilter);
        addSetting(watchdogFilter);

        addSetting(checkScoreboardTeam);
        addSetting(checkNameColor);
        addSetting(checkArmorColor);
    }

    public static TargetsModule getInstance() {
        return instance;
    }

    public boolean isTargetPlayers() { return targetPlayers.isEnabled(); }
    public boolean isTargetInvisibles() { return targetInvisibles.isEnabled(); }
    public boolean isTargetDead() { return targetDead.isEnabled(); }

    public boolean isTabListCheck() { return tabListCheck.isEnabled(); }
    public boolean isPingCheck() { return pingCheck.isEnabled(); }
    public boolean isNpcNameFilter() { return npcNameFilter.isEnabled(); }
    public boolean isWatchdogFilter() { return watchdogFilter.isEnabled(); }

    public boolean isCheckScoreboardTeam() { return checkScoreboardTeam.isEnabled(); }
    public boolean isCheckNameColor() { return checkNameColor.isEnabled(); }
    public boolean isCheckArmorColor() { return checkArmorColor.isEnabled(); }
}
