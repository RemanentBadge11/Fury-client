package com.lionclient.feature.module.impl;

import com.lionclient.feature.module.Category;
import com.lionclient.feature.module.Module;
import com.lionclient.feature.setting.BooleanSetting;
import com.lionclient.feature.setting.NumberSetting;
import com.lionclient.util.ItemUtil;
import com.lionclient.util.KeyBindUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.util.MovingObjectPosition.MovingObjectType;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.input.Keyboard;

public final class AutoToolModule extends Module {

    private final NumberSetting switchDelay = new NumberSetting("Delay", 0, 5, 1, 0);
    private final BooleanSetting switchBack = new BooleanSetting("Switch Back", true);
    private final BooleanSetting sneakOnly = new BooleanSetting("Sneak Only", true);

    private int currentToolSlot = -1;
    private int previousSlot = -1;
    private int tickDelayCounter = 0;

    public AutoToolModule() {
        super("AutoTool", "Automatically switches to the best tool when breaking blocks.", Category.PLAYER, Keyboard.KEY_NONE);
        addSetting(switchDelay);
        addSetting(switchBack);
        addSetting(sneakOnly);
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
        if (mc.thePlayer == null || mc.theWorld == null) {
            resetState();
            return;
        }

        if (currentToolSlot != -1 && currentToolSlot != mc.thePlayer.inventory.currentItem) {
            currentToolSlot = -1;
            previousSlot = -1;
        }

        if (mc.objectMouseOver != null
                && mc.objectMouseOver.typeOfHit == MovingObjectType.BLOCK
                && mc.gameSettings.keyBindAttack.isKeyDown()
                && !mc.thePlayer.isUsingItem()
                && !isAttackingAura(mc)) {

            boolean canSneakTrigger = !sneakOnly.isEnabled() || KeyBindUtil.isKeyDown(mc.gameSettings.keyBindSneak.getKeyCode());

            if (tickDelayCounter >= switchDelay.getValue() && canSneakTrigger) {
                int bestSlot = ItemUtil.findBestToolSlot(
                    mc.thePlayer.inventory.currentItem,
                    mc.theWorld.getBlockState(mc.objectMouseOver.getBlockPos()).getBlock()
                );

                if (mc.thePlayer.inventory.currentItem != bestSlot) {
                    if (previousSlot == -1) {
                        previousSlot = mc.thePlayer.inventory.currentItem;
                    }
                    mc.thePlayer.inventory.currentItem = currentToolSlot = bestSlot;
                }
            }
            tickDelayCounter++;
        } else {
            if (switchBack.isEnabled() && previousSlot != -1) {
                mc.thePlayer.inventory.currentItem = previousSlot;
            }
            resetState();
        }
    }

    private boolean isAttackingAura(Minecraft mc) {
        try {
            com.lionclient.LionClient client = com.lionclient.LionClient.getInstance();
            if (client != null && client.getModuleManager() != null) {
                KillAuraModule killAura = client.getModuleManager().getModule(KillAuraModule.class);
                if (killAura != null && killAura.isEnabled() && killAura.getTarget() != null) {
                    return true;
                }
                SilentAuraModule silentAura = client.getModuleManager().getModule(SilentAuraModule.class);
                if (silentAura != null && silentAura.isEnabled() && silentAura.getTarget() != null) {
                    return true;
                }
            }
        } catch (Throwable ignored) {}
        return false;
    }

    private void resetState() {
        currentToolSlot = -1;
        previousSlot = -1;
        tickDelayCounter = 0;
    }
}
