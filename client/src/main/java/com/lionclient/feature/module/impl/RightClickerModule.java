package com.lionclient.feature.module.impl;

import com.lionclient.feature.module.Category;
import com.lionclient.feature.module.Module;
import com.lionclient.feature.setting.BooleanSetting;
import com.lionclient.feature.setting.DecimalSetting;
import com.lionclient.util.MouseButtonHelper;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

public final class RightClickerModule extends Module {

    private final DecimalSetting minCps = new DecimalSetting("Min CPS", 0.0, 20.0, 0.25, 15.0);
    private final DecimalSetting maxCps = new DecimalSetting("Max CPS", 0.0, 20.0, 0.25, 15.0);
    private final BooleanSetting onlyBlocks = new BooleanSetting("Only Blocks", true);
    private final BooleanSetting noObsidian = new BooleanSetting("No Obsidian", false);

    private final ExtraPattern pattern = new ExtraPattern();
    
    private final Set<Item> blacklistedItems = new HashSet<Item>();
    private final Set<Block> blacklistedBlocks = new HashSet<Block>();

    public RightClickerModule() {
        super("RightClicker", "Automatically right-clicks with Sakura's logic", Category.COMBAT, Keyboard.KEY_NONE);
        
        addSetting(minCps);
        addSetting(maxCps);
        addSetting(onlyBlocks);
        addSetting(noObsidian);

        blacklistedItems.add(Items.compass);
        blacklistedItems.add(Items.clock);
        blacklistedItems.add(Items.ender_pearl);
        blacklistedItems.add(Items.fishing_rod);
        blacklistedItems.add(Items.stone_sword);
        blacklistedItems.add(Items.diamond_sword);
        blacklistedItems.add(Items.golden_sword);
        blacklistedItems.add(Items.iron_sword);
        blacklistedItems.add(Items.wooden_sword);
        blacklistedItems.add(Items.nether_star);
        blacklistedItems.add(Items.emerald);
        blacklistedItems.add(Items.cake);
        blacklistedItems.add(Items.skull);

        blacklistedBlocks.add(Blocks.obsidian);
    }

    @Override
    protected void onEnable() {
        super.onEnable();
        this.pattern.setup((int) randomDouble(this.minCps.getValue(), this.maxCps.getValue()));
    }

    @Override
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        Minecraft mc = Minecraft.getMinecraft();
        
        // Correct value in guiUpdate equivalent
        if (minCps.getValue() > maxCps.getValue()) {
            minCps.setValue(maxCps.getValue());
        }

        if (mc.thePlayer != null && mc.currentScreen == null && Mouse.isButtonDown(1)) {
            Item heldItem = mc.thePlayer.getHeldItem() != null ? mc.thePlayer.getHeldItem().getItem() : null;
            Block heldBlock = heldItem instanceof ItemBlock ? ((ItemBlock) heldItem).getBlock() : null;

            if (heldItem != null && blacklistedItems.contains(heldItem)) {
                return;
            }
            if (this.onlyBlocks.isEnabled() && heldBlock == null) {
                return;
            }
            if (this.noObsidian.isEnabled() && heldBlock != null && blacklistedBlocks.contains(heldBlock)) {
                return;
            }
            if (this.pattern.check()) {
                this.pattern.setup((int) randomDouble(this.minCps.getValue(), this.maxCps.getValue()));
            }
            if (this.pattern.nextAttack()) {
                int key = mc.gameSettings.keyBindUseItem.getKeyCode();
                KeyBinding.onTick(key);
                MouseButtonHelper.setButton(1, true);
                CPSModule.addRightClick();
            }
        }
    }

    private double randomDouble(double min, double max) {
        if (min == max) {
            return min;
        }
        if (min > max) {
            double temp = min;
            min = max;
            max = temp;
        }
        return min + Math.random() * (max - min);
    }

    private static class ExtraPattern {
        protected ArrayList<Boolean> attackList;
        protected int index = 0;

        public void setup(int aps) {
            this.index = 0;
            this.attackList = new ArrayList<Boolean>();
            int i = 0;
            while (i < 20) {
                this.attackList.add(i, i < aps);
                ++i;
            }
            Collections.shuffle(this.attackList);
        }

        public boolean check() {
            return this.attackList == null || this.index >= this.attackList.size();
        }

        public boolean nextAttack() {
            if (this.attackList == null || this.index >= this.attackList.size()) return false;
            boolean b = this.attackList.get(this.index);
            ++this.index;
            return b;
        }
    }
}