package com.lionclient.feature.module.impl;

import com.lionclient.feature.module.Category;
import com.lionclient.feature.module.Module;
import com.lionclient.feature.setting.EnumSetting;
import com.lionclient.feature.setting.NumberSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.inventory.GuiInventory;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemPotion;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.input.Keyboard;

public final class RefillModule extends Module {

    public enum RefillMode {
        SOUP("Soup"),
        POT("Pot");

        private final String name;
        RefillMode(String name) { this.name = name; }
        @Override public String toString() { return name; }
    }

    private final EnumSetting<RefillMode> mode = new EnumSetting<>("Mode", RefillMode.values(), RefillMode.POT);
    private final NumberSetting delay = new NumberSetting("Delay", 0, 20, 1, 1);

    private long lastRefillTime = 0L;

    public RefillModule() {
        super("Refill", "Automatically refills hotbar with soups or potions when inventory is open.", Category.PLAYER, Keyboard.KEY_NONE);
        addSetting(mode);
        addSetting(delay);
    }

    @Override
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START || !isEnabled()) return;

        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null || mc.theWorld == null) return;

        if (mc.currentScreen instanceof GuiInventory) {
            Item targetItem = mode.getValue() == RefillMode.SOUP ? Items.mushroom_stew : ItemPotion.getItemById(373);
            refill(mc, targetItem);
        }
    }

    private void refill(Minecraft mc, Item targetItem) {
        if (!isHotbarFull(mc) && System.currentTimeMillis() - lastRefillTime >= (long) (delay.getValue() * 50)) {
            for (int i = 9; i < 36; ++i) {
                ItemStack itemstack = mc.thePlayer.inventoryContainer.getSlot(i).getStack();
                if (itemstack != null && itemstack.getItem() == targetItem) {
                    mc.playerController.windowClick(0, i, 0, 1, mc.thePlayer);
                    lastRefillTime = System.currentTimeMillis();
                    break;
                }
            }
        }
    }

    public static boolean isHotbarFull(Minecraft mc) {
        if (mc.thePlayer == null || mc.thePlayer.inventory == null) return true;
        for (int i = 0; i < 9; ++i) {
            ItemStack itemstack = mc.thePlayer.inventory.mainInventory[i];
            if (itemstack == null) {
                return false;
            }
        }
        return true;
    }

    @Override
    public String getHudInfo() {
        return mode.getValue().toString();
    }
}
