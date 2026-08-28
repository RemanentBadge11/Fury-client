package com.lionclient.util;

import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.init.Items;
import net.minecraft.item.*;
import net.minecraft.potion.PotionEffect;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public final class ItemUtil {

    private static final Minecraft mc = Minecraft.getMinecraft();
    private static final List<Integer> specialItems = new ArrayList<>();

    static {
        specialItems.add(1);
        specialItems.add(3);
        specialItems.add(5);
        specialItems.add(6);
        specialItems.add(8);
        specialItems.add(10);
        specialItems.add(11);
        specialItems.add(12);
        specialItems.add(14);
        specialItems.add(21);
        specialItems.add(22);
    }

    private ItemUtil() {}

    public static boolean isNotSpecialItem(ItemStack itemStack) {
        if (itemStack == null) {
            return false;
        }
        Item item = itemStack.getItem();
        if (item instanceof ItemPotion) {
            return ((ItemPotion) item).getEffects(itemStack).stream()
                .map(PotionEffect::getPotionID)
                .noneMatch(specialItems::contains);
        }
        if (item instanceof ItemEnderPearl) return false;
        if (item instanceof ItemFood) {
            if (item != Items.spider_eye) return false;
        }
        if (item instanceof ItemMonsterPlacer) return false;
        return item != Items.nether_star;
    }

    public static boolean isBlock(ItemStack itemStack) {
        if (itemStack == null || itemStack.stackSize < 1) {
            return false;
        }
        Item item = itemStack.getItem();
        return item instanceof ItemBlock;
    }

    public static boolean isProjectile(ItemStack itemStack) {
        if (itemStack == null || itemStack.stackSize < 1) {
            return false;
        }
        Item item = itemStack.getItem();
        return item instanceof ItemEgg || item instanceof ItemSnowball;
    }

    public static double getAttackBonus(ItemStack itemStack) {
        double attackBonus = 0.0;
        if (itemStack == null) {
            return 0.0;
        }
        Item item = itemStack.getItem();
        if (item instanceof ItemSword) {
            attackBonus += 4.0D + ((ItemSword) item).getDamageVsEntity();
        } else if (item instanceof ItemTool) {
            attackBonus += 1.0D + ((ItemTool) item).getToolMaterial().getDamageVsEntity();
        }
        if (itemStack.isItemEnchanted()) {
            attackBonus += (double) EnchantmentHelper.getEnchantmentLevel(Enchantment.fireAspect.effectId, itemStack);
            attackBonus += (double) EnchantmentHelper.getEnchantmentLevel(Enchantment.sharpness.effectId, itemStack) * 1.25;
        }
        return attackBonus;
    }

    public static float getToolEfficiency(ItemStack itemStack) {
        float efficiency = 1.0f;
        if (itemStack != null && itemStack.getItem() instanceof ItemTool) {
            efficiency = ((ItemTool) itemStack.getItem()).getToolMaterial().getEfficiencyOnProperMaterial();
            if (efficiency > 1.0f) {
                int enchantLevel = EnchantmentHelper.getEnchantmentLevel(Enchantment.efficiency.effectId, itemStack);
                if (enchantLevel > 0) {
                    efficiency += (float) (enchantLevel * enchantLevel + 1);
                }
            }
        }
        return efficiency;
    }

    public static float getToolEfficiency(ItemStack itemStack, Block block) {
        float efficiency = 1.0f;
        if (itemStack != null) {
            efficiency = itemStack.canHarvestBlock(block) || !(itemStack.getItem() instanceof ItemPickaxe)
                    ? itemStack.getStrVsBlock(block) : 1.0f;
            if (itemStack.getItem() instanceof ItemTool) {
                if (efficiency > 1.0f) {
                    int enchantLevel = EnchantmentHelper.getEnchantmentLevel(Enchantment.efficiency.effectId, itemStack);
                    if (enchantLevel > 0) {
                        efficiency += (float) (enchantLevel * enchantLevel + 1);
                    }
                }
            }
        }
        return efficiency;
    }

    public static int findBestToolSlot(int currentSlot, Block block) {
        if (mc.thePlayer == null) return currentSlot;
        ItemStack currentItem = mc.thePlayer.inventory.getStackInSlot(currentSlot);
        int bestSlot = currentSlot;
        float bestStrength = getToolEfficiency(currentItem, block);
        for (int i = 0; i < 9; ++i) {
            ItemStack itemStack = mc.thePlayer.inventory.getStackInSlot(i);
            if (itemStack == null) continue;
            float strength = getToolEfficiency(itemStack, block);
            if (strength > bestStrength) {
                bestSlot = i;
                bestStrength = strength;
            }
        }
        return bestSlot;
    }

    public static double getArmorProtection(ItemStack itemStack) {
        double protection = 0.0;
        if (itemStack != null && itemStack.getItem() instanceof ItemArmor) {
            protection = ((ItemArmor) itemStack.getItem()).damageReduceAmount;
            if (itemStack.isItemEnchanted()) {
                protection += (double) EnchantmentHelper.getEnchantmentLevel(Enchantment.protection.effectId, itemStack) * 0.8;
                protection += (double) EnchantmentHelper.getEnchantmentLevel(Enchantment.featherFalling.effectId, itemStack) * 0.05;
                protection += (double) EnchantmentHelper.getEnchantmentLevel(Enchantment.projectileProtection.effectId, itemStack) * 0.01;
            }
        }
        return protection;
    }

    public static double getBowAttackBonus(ItemStack itemStack) {
        double attackBonus = 0.0;
        if (itemStack != null && itemStack.getItem() instanceof ItemBow) {
            attackBonus = 2.0;
            if (itemStack.isItemEnchanted()) {
                int power = EnchantmentHelper.getEnchantmentLevel(Enchantment.power.effectId, itemStack);
                if (power > 0) {
                    attackBonus += (double) (power + 1) * 0.25;
                }
                attackBonus += (double) EnchantmentHelper.getEnchantmentLevel(Enchantment.flame.effectId, itemStack) * 0.25;
                attackBonus += (double) EnchantmentHelper.getEnchantmentLevel(Enchantment.infinity.effectId, itemStack) * 0.05;
            }
        }
        return attackBonus;
    }

    public static int findSwordInInventorySlot(int startSlot, boolean checkDurability) {
        int bestSlot = -1;
        double bestAttackBonus = 0.0;
        if (startSlot < 0 || mc.thePlayer == null) return bestSlot;
        for (int i = 0; i < 36; ++i) {
            int currentSlot = (startSlot + i) % 36;
            ItemStack itemStack = mc.thePlayer.inventory.getStackInSlot(currentSlot);
            if (itemStack == null || !(itemStack.getItem() instanceof ItemSword)) continue;
            if (checkDurability && itemStack.isItemDamaged()) {
                if (itemStack.getMaxDamage() - itemStack.getItemDamage() < 30) continue;
            }
            double attackBonus = getAttackBonus(itemStack);
            if (attackBonus > bestAttackBonus) {
                bestSlot = currentSlot;
                bestAttackBonus = attackBonus;
            }
        }
        return bestSlot;
    }

    public static int findBowInventorySlot(int startSlot, boolean checkDurability) {
        int bestSlot = -1;
        double bestAttackBonus = 0.0;
        if (startSlot < 0 || mc.thePlayer == null) return bestSlot;
        for (int i = 0; i < 36; ++i) {
            int currentSlot = (startSlot + i) % 36;
            ItemStack itemStack = mc.thePlayer.inventory.getStackInSlot(currentSlot);
            if (itemStack == null || !(itemStack.getItem() instanceof ItemBow)) continue;
            if (checkDurability && itemStack.isItemDamaged()) {
                if (itemStack.getMaxDamage() - itemStack.getItemDamage() < 30) continue;
            }
            double attackBonus = getBowAttackBonus(itemStack);
            if (attackBonus > bestAttackBonus) {
                bestSlot = currentSlot;
                bestAttackBonus = attackBonus;
            }
        }
        return bestSlot;
    }

    public static int findInventorySlot(String toolClass, int startSlot, boolean checkDurability) {
        int bestSlot = -1;
        float bestEfficiency = 1.0f;
        if (startSlot < 0 || mc.thePlayer == null) return bestSlot;
        for (int i = 0; i < 36; ++i) {
            int currentSlot = (startSlot + i) % 36;
            ItemStack itemStack = mc.thePlayer.inventory.getStackInSlot(currentSlot);
            if (itemStack == null || !(itemStack.getItem() instanceof ItemTool)) continue;
            if (!itemStack.getItem().getToolClasses(itemStack).contains(toolClass)) continue;
            if (checkDurability && itemStack.isItemDamaged()) {
                if (itemStack.getMaxDamage() - itemStack.getItemDamage() < 30) continue;
            }
            float efficiency = getToolEfficiency(itemStack);
            if (efficiency > bestEfficiency) {
                bestSlot = currentSlot;
                bestEfficiency = efficiency;
            }
        }
        return bestSlot;
    }

    public static int findArmorInventorySlot(int armorType, boolean checkDurability) {
        int bestSlot = -1;
        double bestProtection = 0.0;
        if (mc.thePlayer == null) return bestSlot;
        for (int i = 0; i < 40; ++i) {
            ItemStack itemStack = mc.thePlayer.inventory.getStackInSlot(i);
            if (itemStack == null || !(itemStack.getItem() instanceof ItemArmor)) continue;
            if (((ItemArmor) itemStack.getItem()).armorType != armorType) continue;
            if (checkDurability && itemStack.isItemDamaged()) {
                if (itemStack.getMaxDamage() - itemStack.getItemDamage() < 30) continue;
            }
            double protection = getArmorProtection(itemStack);
            if (protection >= bestProtection) {
                bestSlot = i;
                bestProtection = protection;
            }
        }
        return bestSlot;
    }

    public enum ItemType {
        Block {
            public boolean contains(ItemStack stack) { return isBlock(stack); }
        },
        Projectile {
            public boolean contains(ItemStack stack) { return isProjectile(stack); }
        },
        FishRod {
            public boolean contains(ItemStack stack) { return stack != null && stack.getItem() instanceof ItemFishingRod; }
        },
        GoldApple {
            public boolean contains(ItemStack stack) { return stack != null && stack.getItem() instanceof ItemAppleGold; }
        },
        Arrow {
            public boolean contains(ItemStack stack) { return stack != null && stack.getItem() == Items.arrow; }
        };
        public abstract boolean contains(ItemStack stack);
    }
}
