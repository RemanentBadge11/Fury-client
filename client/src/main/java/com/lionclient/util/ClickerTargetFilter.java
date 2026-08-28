package com.lionclient.util;

import com.lionclient.feature.module.impl.AntiBotModule;
import com.lionclient.feature.module.impl.BedwarsModule;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;

public final class ClickerTargetFilter {
    
    private ClickerTargetFilter() {}

    public static boolean isEnemy(Entity entity) {
        return TargetFilter.isValidTarget(entity);
    }
}
