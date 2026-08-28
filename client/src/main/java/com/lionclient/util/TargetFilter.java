package com.lionclient.util;

import com.lionclient.feature.module.impl.AntiBotModule;
import com.lionclient.feature.module.impl.BedwarsModule;
import com.lionclient.feature.module.impl.TargetsModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityArmorStand;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemArmor;
import net.minecraft.item.ItemStack;
import net.minecraft.scoreboard.ScorePlayerTeam;
import net.minecraft.scoreboard.Team;
import net.minecraft.util.AxisAlignedBB;

import java.util.Collection;
import java.util.List;

public final class TargetFilter {

    private TargetFilter() {}

    public static boolean isValidTarget(Entity entity) {
        Minecraft mc = Minecraft.getMinecraft();
        if (entity == null || mc == null || mc.thePlayer == null || mc.theWorld == null) {
            return false;
        }

        if (entity == mc.thePlayer) {
            return false;
        }

        if (!(entity instanceof EntityLivingBase)) {
            return false;
        }

        EntityLivingBase living = (EntityLivingBase) entity;

        if (isShop(mc, living)) {
            return false;
        }

        if (living instanceof EntityPlayer) {
            EntityPlayer target = (EntityPlayer) living;

            if (AntiBotModule.shouldIgnore(target)) {
                return false;
            }

            // Advanced NPC filter: 0-ping, §k invisible prefix, fake team colors
            if (isBot(mc, target)) {
                return false;
            }

            if (BedwarsModule.isTeammate(target)) {
                return false;
            }

            TargetsModule targets = TargetsModule.getInstance();
            if (targets != null && targets.isEnabled()) {
                if (!targets.isTargetPlayers()) {
                    return false;
                }
                if (!targets.isTargetDead() && (target.isDead || target.deathTime > 0 || target.getHealth() <= 0.0F)) {
                    return false;
                }
                if (!targets.isTargetInvisibles() && target.isInvisible()) {
                    return false;
                }

                if ((targets.isCheckScoreboardTeam() || targets.isCheckNameColor() || targets.isCheckArmorColor())
                        && isSameTeam(mc, target)) {
                    return false;
                }
            }
        }

        return true;
    }

    public static boolean isBot(Minecraft mc, EntityPlayer player) {
        if (player == null || mc == null || mc.thePlayer == null || player == mc.thePlayer) {
            return false;
        }

        if (mc.getNetHandler() == null) return false;

        NetworkPlayerInfo playerInfo = mc.getNetHandler().getPlayerInfo(player.getUniqueID());
        if (playerInfo == null) {
            playerInfo = mc.getNetHandler().getPlayerInfo(player.getName());
        }

        // If playerInfo is null (e.g. singleplayer, LAN, or custom server), do NOT assume it's a bot
        if (playerInfo == null) {
            return false;
        }

        if (player.getName() != null && player.getName().startsWith("§k") && player.isInvisible()) {
            return true;
        }

        TargetsModule targets = TargetsModule.getInstance();
        if (targets != null && targets.isEnabled() && targets.isPingCheck()) {
            if (playerInfo.getResponseTime() < 1) {
                return true;
            }
        }

        ScorePlayerTeam playerTeam = playerInfo.getPlayerTeam();
        if (playerTeam != null && playerTeam.getTeamName() != null && playerTeam.getTeamName().isEmpty() && "§c".equals(playerTeam.getColorPrefix())) {
            return true;
        }

        return false;
    }

    private static int lastShopCacheTick = -1;
    private static final AxisAlignedBB[] cachedShops = new AxisAlignedBB[128];
    private static int cachedShopCount = 0;

    public static boolean isShop(Minecraft mc, EntityLivingBase entity) {
        if (entity == null || mc == null || mc.theWorld == null || entity == mc.thePlayer) {
            return false;
        }

        if (mc.thePlayer.ticksExisted != lastShopCacheTick) {
            lastShopCacheTick = mc.thePlayer.ticksExisted;
            cachedShopCount = 0;
            List<Entity> loadedEntities = mc.theWorld.loadedEntityList;
            int size = loadedEntities.size();
            for (int i = 0; i < size; i++) {
                Entity o = loadedEntities.get(i);
                if (o instanceof EntityArmorStand) {
                    String name = o.getName();
                    if (name != null && (name.contains("RIGHT CLICK") || name.contains("ITEM SHOP") || name.contains("UPGRADES")
                            || name.contains("BANKER") || name.contains("STREAK POWERS"))) {
                        if (cachedShopCount < cachedShops.length) {
                            cachedShops[cachedShopCount++] = o.getEntityBoundingBox();
                        }
                    }
                }
            }
        }

        AxisAlignedBB bb = entity.getEntityBoundingBox();
        if (bb == null) return false;

        AxisAlignedBB searchBox = bb.expand(0.5, 0.5, 0.5);
        for (int i = 0; i < cachedShopCount; i++) {
            AxisAlignedBB shopBox = cachedShops[i];
            if (shopBox != null && shopBox.intersectsWith(searchBox)) {
                return true;
            }
        }
        
        return false;
    }

    public static boolean isSameTeam(Minecraft mc, EntityPlayer player) {
        if (player == null || mc == null || mc.thePlayer == null || player == mc.thePlayer) {
            return true;
        }

        TargetsModule targets = TargetsModule.getInstance();
        if (targets == null || !targets.isEnabled()) {
            return false;
        }

        if (mc.getNetHandler() == null) return false;

        NetworkPlayerInfo selfInfo = mc.getNetHandler().getPlayerInfo(mc.thePlayer.getUniqueID());
        NetworkPlayerInfo targetInfo = mc.getNetHandler().getPlayerInfo(player.getUniqueID());

        if (selfInfo == null || targetInfo == null) return false;

        ScorePlayerTeam selfTeam = selfInfo.getPlayerTeam();
        ScorePlayerTeam targetTeam = targetInfo.getPlayerTeam();

        // 1. Check Scoreboard Team Name
        if (targets.isCheckScoreboardTeam() && selfTeam != null && targetTeam != null) {
            if (selfTeam.getTeamName() != null && selfTeam.getTeamName().equals(targetTeam.getTeamName())) {
                return true;
            }
        }

        // 2. Check Name Color (ignoring default gray/white/reset colors)
        if (targets.isCheckNameColor() && selfTeam != null && targetTeam != null) {
            String selfPrefix = selfTeam.getColorPrefix();
            String targetPrefix = targetTeam.getColorPrefix();
            if (selfPrefix != null && targetPrefix != null && !selfPrefix.isEmpty() && !targetPrefix.isEmpty()) {
                String cleanSelf = selfPrefix.trim();
                String cleanTarget = targetPrefix.trim();
                if (!cleanSelf.equals("§7") && !cleanSelf.equals("§f") && !cleanSelf.equals("§r") && cleanSelf.equals(cleanTarget)) {
                    return true;
                }
            }
        }

        // 3. Check Armor Color (Leather armor dye matching for team games)
        if (targets.isCheckArmorColor()) {
            ItemStack selfChest = mc.thePlayer.getCurrentArmor(2);
            ItemStack targetChest = player.getCurrentArmor(2);
            if (selfChest != null && targetChest != null && selfChest.getItem() instanceof ItemArmor && targetChest.getItem() instanceof ItemArmor) {
                ItemArmor selfArmor = (ItemArmor) selfChest.getItem();
                ItemArmor targetArmor = (ItemArmor) targetChest.getItem();
                if (selfArmor.getArmorMaterial() == ItemArmor.ArmorMaterial.LEATHER && targetArmor.getArmorMaterial() == ItemArmor.ArmorMaterial.LEATHER) {
                    if (selfArmor.hasColor(selfChest) && targetArmor.hasColor(targetChest)) {
                        return selfArmor.getColor(selfChest) == targetArmor.getColor(targetChest);
                    }
                }
            }
        }

        return false;
    }
}
