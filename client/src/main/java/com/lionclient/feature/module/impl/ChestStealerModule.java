package com.lionclient.feature.module.impl;

import com.lionclient.feature.module.Category;
import com.lionclient.feature.module.Module;
import com.lionclient.feature.setting.BooleanSetting;
import com.lionclient.feature.setting.NumberSetting;
import com.lionclient.util.ItemUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.inventory.GuiChest;
import net.minecraft.client.resources.I18n;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.ContainerChest;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.*;
import net.minecraft.world.WorldSettings.GameType;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.input.Keyboard;

import java.util.Random;

public final class ChestStealerModule extends Module {

    private final NumberSetting minDelay = new NumberSetting("Min Delay", 0, 20, 1, 1);
    private final NumberSetting maxDelay = new NumberSetting("Max Delay", 0, 20, 1, 2);
    private final NumberSetting openDelay = new NumberSetting("Open Delay", 0, 20, 1, 1);
    private final BooleanSetting autoClose = new BooleanSetting("Auto Close", false);
    private final BooleanSetting nameCheck = new BooleanSetting("Name Check", true);
    private final BooleanSetting skipTrash = new BooleanSetting("Skip Trash", true);
    private final BooleanSetting moreArmor = new BooleanSetting("More Armor", false);
    private final BooleanSetting moreSword = new BooleanSetting("More Sword", false);

    private int clickDelay = 0;
    private int oDelay = 0;
    private boolean inChest = false;
    private final Random random = new Random();

    public ChestStealerModule() {
        super("ChestStealer", "Automatically loots items from opened chests.", Category.PLAYER, Keyboard.KEY_NONE);
        addSetting(minDelay);
        addSetting(maxDelay);
        addSetting(openDelay);
        addSetting(autoClose);
        addSetting(nameCheck);
        addSetting(skipTrash);
        addSetting(moreArmor);
        addSetting(moreSword);
    }

    @Override
    protected void onEnable() {
        resetState();
    }

    @Override
    protected void onDisable() {
        resetState();
    }

    @Override
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START || !isEnabled()) return;

        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null || mc.theWorld == null || mc.playerController == null) {
            resetState();
            return;
        }

        if (clickDelay > 0) clickDelay--;
        if (oDelay > 0) oDelay--;

        if (!(mc.currentScreen instanceof GuiChest)) {
            inChest = false;
            return;
        }

        Container container = ((GuiChest) mc.currentScreen).inventorySlots;
        if (!(container instanceof ContainerChest)) {
            inChest = false;
            return;
        }

        if (!inChest) {
            inChest = true;
            oDelay = openDelay.getValue() + 1;
        }

        if (oDelay <= 0 && clickDelay <= 0) {
            if (!isValidGameMode(mc)) return;

            IInventory inventory = ((ContainerChest) container).getLowerChestInventory();
            if (nameCheck.isEnabled()) {
                String inventoryName = inventory.getName();
                if (!inventoryName.equals(I18n.format("container.chest")) && !inventoryName.equals(I18n.format("container.chestDouble"))) {
                    return;
                }
            }

            if (mc.thePlayer.inventory.getFirstEmptyStack() == -1) {
                if (autoClose.isEnabled()) {
                    mc.thePlayer.closeScreen();
                }
                return;
            }

            if (skipTrash.isEnabled()) {
                int bestSword = -1;
                double bestDamage = 0.0;
                int[] bestArmorSlots = new int[]{-1, -1, -1, -1};
                double[] bestArmorProtection = new double[]{0.0, 0.0, 0.0, 0.0};
                int bestPickaxeSlot = -1;
                float bestPickaxeEfficiency = 1.0F;
                int bestShovelSlot = -1;
                float bestShovelEfficiency = 1.0F;
                int bestAxeSlot = -1;
                float bestAxeEfficiency = 1.0F;
                int bestBow = -1;
                double bestBowDamage = 0.0;

                for (int i = 0; i < inventory.getSizeInventory(); i++) {
                    if (container.getSlot(i).getHasStack()) {
                        ItemStack stack = container.getSlot(i).getStack();
                        Item item = stack.getItem();
                        if (item instanceof ItemSword) {
                            double damage = ItemUtil.getAttackBonus(stack);
                            if (bestSword == -1 || damage > bestDamage) {
                                bestSword = i;
                                bestDamage = damage;
                            }
                        } else if (item instanceof ItemArmor) {
                            int armorType = ((ItemArmor) item).armorType;
                            double protectionLevel = ItemUtil.getArmorProtection(stack);
                            if (bestArmorSlots[armorType] == -1 || protectionLevel > bestArmorProtection[armorType]) {
                                bestArmorSlots[armorType] = i;
                                bestArmorProtection[armorType] = protectionLevel;
                            }
                        } else if (item instanceof ItemPickaxe) {
                            float efficiency = ItemUtil.getToolEfficiency(stack);
                            if (bestPickaxeSlot == -1 || efficiency > bestPickaxeEfficiency) {
                                bestPickaxeSlot = i;
                                bestPickaxeEfficiency = efficiency;
                            }
                        } else if (item instanceof ItemSpade) {
                            float efficiency = ItemUtil.getToolEfficiency(stack);
                            if (bestShovelSlot == -1 || efficiency > bestShovelEfficiency) {
                                bestShovelSlot = i;
                                bestShovelEfficiency = efficiency;
                            }
                        } else if (item instanceof ItemAxe) {
                            float efficiency = ItemUtil.getToolEfficiency(stack);
                            if (bestAxeSlot == -1 || efficiency > bestAxeEfficiency) {
                                bestAxeSlot = i;
                                bestAxeEfficiency = efficiency;
                            }
                        } else if (item instanceof ItemBow) {
                            double damage = ItemUtil.getBowAttackBonus(stack);
                            if (bestBow == -1 || damage > bestBowDamage) {
                                bestBow = i;
                                bestBowDamage = damage;
                            }
                        }
                    }
                }

                int swordInInventorySlot = ItemUtil.findSwordInInventorySlot(0, true);
                double damage = swordInInventorySlot != -1 ? ItemUtil.getAttackBonus(mc.thePlayer.inventory.getStackInSlot(swordInInventorySlot)) : 0.0;
                if (bestDamage > damage) {
                    shiftClick(mc, container.windowId, bestSword);
                    return;
                }

                for (int i = 0; i < 4; i++) {
                    int slot = ItemUtil.findArmorInventorySlot(i, true);
                    double protectionLevel = slot != -1 ? ItemUtil.getArmorProtection(mc.thePlayer.inventory.getStackInSlot(slot)) : 0.0;
                    if (bestArmorProtection[i] > protectionLevel) {
                        shiftClick(mc, container.windowId, bestArmorSlots[i]);
                        return;
                    }
                }

                int pickaxeSlot = ItemUtil.findInventorySlot("pickaxe", 0, true);
                float pickaxeEfficiency = pickaxeSlot != -1 ? ItemUtil.getToolEfficiency(mc.thePlayer.inventory.getStackInSlot(pickaxeSlot)) : 1.0F;
                if (bestPickaxeEfficiency > pickaxeEfficiency) {
                    shiftClick(mc, container.windowId, bestPickaxeSlot);
                    return;
                }

                int shovelSlot = ItemUtil.findInventorySlot("shovel", 0, true);
                float shovelEfficiency = shovelSlot != -1 ? ItemUtil.getToolEfficiency(mc.thePlayer.inventory.getStackInSlot(shovelSlot)) : 1.0F;
                if (bestShovelEfficiency > shovelEfficiency) {
                    shiftClick(mc, container.windowId, bestShovelSlot);
                    return;
                }

                int axeSlot = ItemUtil.findInventorySlot("axe", 0, true);
                float efficiency = axeSlot != -1 ? ItemUtil.getToolEfficiency(mc.thePlayer.inventory.getStackInSlot(axeSlot)) : 1.0F;
                if (bestAxeEfficiency > efficiency) {
                    shiftClick(mc, container.windowId, bestAxeSlot);
                    return;
                }

                int bowSlot = ItemUtil.findBowInventorySlot(0, true);
                double bowDamage = bowSlot != -1 ? ItemUtil.getBowAttackBonus(mc.thePlayer.inventory.getStackInSlot(bowSlot)) : 0.0;
                if (bestBowDamage > bowDamage) {
                    shiftClick(mc, container.windowId, bestBow);
                    return;
                }
            }

            for (int i = 0; i < inventory.getSizeInventory(); i++) {
                if (container.getSlot(i).getHasStack()) {
                    ItemStack stack = container.getSlot(i).getStack();
                    if (!skipTrash.isEnabled() || !ItemUtil.isNotSpecialItem(stack) || isMoreArmor(stack) || isMoreSword(stack)) {
                        shiftClick(mc, container.windowId, i);
                        return;
                    }
                }
            }

            if (autoClose.isEnabled()) {
                mc.thePlayer.closeScreen();
            }
        }
    }

    private void shiftClick(Minecraft mc, int windowId, int slotId) {
        mc.playerController.windowClick(windowId, slotId, 0, 1, mc.thePlayer);
        int minD = minDelay.getValue();
        int maxD = maxDelay.getValue();
        if (minD > maxD) minD = maxD;
        clickDelay = minD == maxD ? minD : minD + random.nextInt(maxD - minD + 1);
    }

    private boolean isValidGameMode(Minecraft mc) {
        if (mc.playerController == null) return false;
        GameType type = mc.playerController.getCurrentGameType();
        return type == GameType.SURVIVAL || type == GameType.ADVENTURE;
    }

    private boolean isMoreArmor(ItemStack itemStack) {
        if (itemStack == null || !moreArmor.isEnabled() || !(itemStack.getItem() instanceof ItemArmor)) return false;
        ItemArmor.ArmorMaterial material = ((ItemArmor) itemStack.getItem()).getArmorMaterial();
        if (material == ItemArmor.ArmorMaterial.DIAMOND) return true;
        return material == ItemArmor.ArmorMaterial.IRON && itemStack.isItemEnchanted();
    }

    private boolean isMoreSword(ItemStack itemStack) {
        if (itemStack == null || !moreSword.isEnabled() || !(itemStack.getItem() instanceof ItemSword)) return false;
        String matName = ((ItemSword) itemStack.getItem()).getToolMaterialName();
        if ("EMERALD".equals(matName)) return true;
        if (EnchantmentHelper.getEnchantmentLevel(Enchantment.fireAspect.effectId, itemStack) != 0) return true;
        return "IRON".equals(matName) && itemStack.isItemEnchanted();
    }

    private void resetState() {
        clickDelay = 0;
        oDelay = 0;
        inChest = false;
    }
}
