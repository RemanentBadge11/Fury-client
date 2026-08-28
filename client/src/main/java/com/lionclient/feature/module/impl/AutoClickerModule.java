package com.lionclient.feature.module.impl;

import com.lionclient.feature.module.Category;
import com.lionclient.feature.module.Module;
import com.lionclient.feature.setting.BooleanSetting;
import com.lionclient.feature.setting.EnumSetting;
import com.lionclient.feature.setting.NumberSetting;
import com.lionclient.util.ClickPattern;
import com.lionclient.util.ClickerInventoryBridge;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.inventory.GuiChest;
import net.minecraft.client.gui.inventory.GuiInventory;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.item.ItemAxe;
import net.minecraft.item.ItemSword;
import net.minecraft.util.MovingObjectPosition;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.ReflectionHelper;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

public final class AutoClickerModule extends Module {

    private final Method guiClickMethod;
    private final Field leftClickCounterField;

    private final EnumSetting<Randomization> randomization =
            new EnumSetting<Randomization>("Randomization", Randomization.values(), Randomization.EXTRA);
    private final NumberSetting randomStrength = new NumberSetting("Random Strength", 0, 100, 1, 35);
    private final NumberSetting minCps = new NumberSetting("Min CPS", 1, 20, 1, 14);
    private final NumberSetting maxCps = new NumberSetting("Max CPS", 1, 20, 1, 18);

    private final BooleanSetting breakBlocks = new BooleanSetting("Break Blocks", true);
    private final BooleanSetting weaponOnly = new BooleanSetting("Weapon Only", false);
    private final BooleanSetting inventoryFill = new BooleanSetting("Inventory Fill", false);

    private final BooleanSetting swordsWeapon = new BooleanSetting("Swords", true);
    private final BooleanSetting axesWeapon = new BooleanSetting("Axes", true);
    private final BooleanSetting sticksWeapon = new BooleanSetting("Sticks", true);
    private final BooleanSetting fistsWeapon = new BooleanSetting("Fists", true);
    private final BooleanSetting restWeapon = new BooleanSetting("Rest", true);

    private final ClickPattern clickPattern = ClickPattern.create();
    private long lastReset;

    public AutoClickerModule() {
        super("LeftClicker", "Automatically clicks while holding attack.", Category.COMBAT, Keyboard.KEY_NONE);
        guiClickMethod = findGuiClickMethod();
        leftClickCounterField = findLeftClickCounterField();

        randomStrength.setVisibility(() -> randomization.getValue() != Randomization.OFF);

        addSetting(randomization);
        addSetting(randomStrength);
        addSetting(minCps);
        addSetting(maxCps);
        addSetting(breakBlocks);
        addSetting(weaponOnly);
        addSetting(inventoryFill);
        addSetting(swordsWeapon);
        addSetting(axesWeapon);
        addSetting(sticksWeapon);
        addSetting(fistsWeapon);
        addSetting(restWeapon);
    }

    @Override
    protected void onEnable() {
        clickPattern.reconfigure(
            minCps.getValue(),
            maxCps.getValue(),
            randomStrength.getValue(),
            mapTechnique()
        );
        lastReset = System.currentTimeMillis();
    }

    @Override
    protected void onDisable() {
        KeyBinding.setKeyBindState(Minecraft.getMinecraft().gameSettings.keyBindAttack.getKeyCode(), false);
    }

    @Override
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null || mc.theWorld == null) return;

        normalizeRanges();

        int attackKey = mc.gameSettings.keyBindAttack.getKeyCode();
        if (!com.lionclient.util.KeyBindUtil.isKeyDown(attackKey)) return;

        if (mc.currentScreen != null || !mc.inGameHasFocus) {
            doInventoryClick(mc);
            return;
        }

        if (canClickOnEntity(mc)) {
            handleClickPattern(mc);
        }
    }

    private void handleClickPattern(Minecraft mc) {
        if (clickPattern.check()) {
            clickPattern.reconfigure(
                minCps.getValue(),
                maxCps.getValue(),
                randomStrength.getValue(),
                mapTechnique()
            );
            lastReset = System.currentTimeMillis();
        }

        if (clickPattern.nextAttack()) {
            sendAttack(mc);
        }
    }

    private void sendAttack(Minecraft mc) {
        if (leftClickCounterField != null && !mc.thePlayer.capabilities.isCreativeMode) {
            try { leftClickCounterField.setInt(mc, 0); } catch (IllegalAccessException ignored) {}
        }
        int key = mc.gameSettings.keyBindAttack.getKeyCode();
        KeyBinding.onTick(key);
        CPSModule.addLeftClick();
    }

    private boolean canClickOnEntity(Minecraft mc) {
        // If pointing directly at an entity, we ALWAYS click
        if (mc.objectMouseOver != null && mc.objectMouseOver.typeOfHit == MovingObjectPosition.MovingObjectType.ENTITY) {
            if (weaponOnly.isEnabled() && !isHoldingWeapon(mc)) return false;
            return true;
        }

        // If pointing at a block, check if breakBlocks is active
        if (mc.objectMouseOver != null && mc.objectMouseOver.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK) {
            if (breakBlocks.isEnabled()) {
                // If there is an entity within range, prioritize attacking the entity
                if (mc.pointedEntity != null) {
                    if (weaponOnly.isEnabled() && !isHoldingWeapon(mc)) return false;
                    return true;
                }
                int key = mc.gameSettings.keyBindAttack.getKeyCode();
                KeyBinding.setKeyBindState(key, true);
                return false;
            }
        }

        if (weaponOnly.isEnabled() && !isHoldingWeapon(mc)) return false;

        return true;
    }

    private boolean isTargetingBlock() {
        Minecraft mc = Minecraft.getMinecraft();
        return mc.objectMouseOver != null
            && mc.objectMouseOver.getBlockPos() != null
            && !mc.theWorld.isAirBlock(mc.objectMouseOver.getBlockPos());
    }

    private boolean isHoldingWeapon(Minecraft mc) {
        net.minecraft.item.ItemStack stack = mc.thePlayer.getHeldItem();
        if (stack == null) return fistsWeapon.isEnabled();
        net.minecraft.item.Item item = stack.getItem();
        if (item instanceof ItemSword) return swordsWeapon.isEnabled();
        if (item instanceof ItemAxe) return axesWeapon.isEnabled();
        if (item == net.minecraft.init.Items.stick) return sticksWeapon.isEnabled();
        return restWeapon.isEnabled();
    }

    private void doInventoryClick(Minecraft mc) {
        if (!inventoryFill.isEnabled()) return;
        if (!(mc.currentScreen instanceof GuiInventory) && !(mc.currentScreen instanceof GuiChest)) return;

        boolean shiftDown = Keyboard.isKeyDown(Keyboard.KEY_RSHIFT) || Keyboard.isKeyDown(Keyboard.KEY_LSHIFT);
        if (!Mouse.isButtonDown(0) || !shiftDown) return;

        long now = System.currentTimeMillis();
        double avgCps = (minCps.getValue() + maxCps.getValue()) / 2.0;
        long delay = Math.round(1000.0 / avgCps);
        if (now - lastReset < delay) return;

        lastReset = now;
        ClickerInventoryBridge.doInventoryClick(mc.currentScreen, guiClickMethod, delay);
    }

    private void normalizeRanges() {
        if (maxCps.getValue() < minCps.getValue()) {
            maxCps.setManualValue(minCps.getValue());
        }
    }

    private ClickPattern.PatternTechnique mapTechnique() {
        switch (randomization.getValue()) {
            case OFF: return ClickPattern.PatternTechnique.OFF;
            case NORMAL: return ClickPattern.PatternTechnique.NORMAL;
            case EXTRA: return ClickPattern.PatternTechnique.EXTRA;
            case EXTRA_PLUS: return ClickPattern.PatternTechnique.EXTRA_PLUS;
            default: return ClickPattern.PatternTechnique.EXTRA;
        }
    }

    private Method findGuiClickMethod() {
        try {
            Method method = ReflectionHelper.findMethod(
                GuiScreen.class,
                null,
                new String[]{"func_73864_a", "mouseClicked"},
                Integer.TYPE, Integer.TYPE, Integer.TYPE
            );
            if (method != null) method.setAccessible(true);
            return method;
        } catch (Exception ignored) {
            return null;
        }
    }

    private Field findLeftClickCounterField() {
        try {
            Field field = ReflectionHelper.findField(Minecraft.class, "field_71429_W", "leftClickCounter");
            field.setAccessible(true);
            return field;
        } catch (Exception ignored) {
            return null;
        }
    }

    public enum Randomization {
        OFF("Off"),
        NORMAL("Normal"),
        EXTRA("Extra"),
        EXTRA_PLUS("Extra+");

        private final String name;
        Randomization(String name) { this.name = name; }
        @Override public String toString() { return name; }
    }
}