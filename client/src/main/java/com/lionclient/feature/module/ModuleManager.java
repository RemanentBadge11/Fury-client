package com.lionclient.feature.module;

import com.lionclient.config.ConfigManager;
import com.lionclient.feature.module.impl.AimAssistModule;
import com.lionclient.feature.module.impl.AutoBlockModule;
import com.lionclient.feature.module.impl.BridgeAssistModule;
import com.lionclient.feature.module.impl.AutoClickerModule;
import com.lionclient.feature.module.impl.AntiBotModule;
import com.lionclient.feature.module.impl.AntiFireballModule;
import com.lionclient.feature.module.impl.BackTrackModule;
import com.lionclient.feature.module.impl.BedPlatesModule;
import com.lionclient.feature.module.impl.BedwarsModule;
import com.lionclient.feature.module.impl.ChamsModule;
import com.lionclient.feature.module.impl.ArmorHudModule;
import com.lionclient.feature.module.impl.ClickGuiModule;
import com.lionclient.feature.module.impl.ConfigModule;
import com.lionclient.feature.module.impl.CPSModule;
import com.lionclient.feature.module.impl.FreeLookModule;
import com.lionclient.feature.module.impl.HudModule;
import com.lionclient.feature.module.impl.KillAuraModule;
import com.lionclient.feature.module.impl.ClutchModule;
import com.lionclient.feature.module.impl.KnockbackDelayModule;
import com.lionclient.feature.module.impl.LegitScaffoldModule;
import com.lionclient.feature.module.impl.NametagsModule;
import com.lionclient.feature.module.impl.PlayerEspModule;
import com.lionclient.feature.module.impl.ReachModule;
import com.lionclient.feature.module.impl.RightClickerModule;
import com.lionclient.feature.module.impl.RotationIntegrityMonitorModule;
import com.lionclient.feature.module.impl.SprintModule;
import com.lionclient.feature.module.impl.StasisModule;
import com.lionclient.feature.module.impl.LagRangeModule;
import com.lionclient.feature.module.impl.TrajectoriesModule;
import com.lionclient.feature.module.impl.SafeWalkModule;
import com.lionclient.feature.module.impl.RefillModule;
import com.lionclient.feature.module.impl.WTapModule;
import com.lionclient.feature.module.impl.NoHurtCamModule;
import com.lionclient.feature.module.impl.AnimationsModule;
import com.lionclient.feature.module.impl.BreakProgressModule;
import com.lionclient.feature.module.impl.ChestEspModule;
import com.lionclient.feature.module.impl.ChestStealerModule;
import com.lionclient.feature.module.impl.AutoToolModule;
import com.lionclient.feature.module.impl.BedEspModule;
import com.lionclient.feature.module.impl.FullbrightModule;
import com.lionclient.feature.module.impl.HitParticleEffectsModule;
import com.lionclient.feature.module.impl.IndicatorsModule;
import com.lionclient.feature.module.impl.TimerModule;
import com.lionclient.feature.module.impl.VelocityModule;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.minecraft.network.Packet;
import net.minecraftforge.client.event.MouseEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import com.lionclient.feature.module.impl.TargetsModule;

import com.lionclient.feature.module.impl.SilentAuraModule;

public final class ModuleManager {
    private final List<Module> modules = new ArrayList<Module>();
    private final Map<Category, List<Module>> modulesByCategory = new EnumMap<Category, List<Module>>(Category.class);
    private final ConfigManager configManager;
    private final ConfigModule configModule;

    public ModuleManager() {
        for (Category category : Category.values()) {
            modulesByCategory.put(category, new ArrayList<Module>());
        }

        register(new TargetsModule());
        register(new SprintModule());
        register(new StasisModule());
        register(new BedPlatesModule());
        register(new LegitScaffoldModule());
        register(new AutoClickerModule());
        register(new RightClickerModule());
        register(new BridgeAssistModule());
        register(new ReachModule());
        register(new AntiBotModule());
        register(new AntiFireballModule());
        register(new AimAssistModule());
        register(new RotationIntegrityMonitorModule());
        register(new KillAuraModule());
        register(new SilentAuraModule());
        register(new BackTrackModule());
        register(new KnockbackDelayModule());
        register(new LagRangeModule());
        if (lion.client.hook.LauncherDetection.detect().kind
                != lion.client.hook.LauncherDetection.Kind.BADLION) {
            register(new AutoBlockModule());
            register(ClutchModule.getInstance());
        }
        register(new ClickGuiModule());
        register(new PlayerEspModule());
        register(new ChamsModule());
        register(new ArmorHudModule());
        register(new NametagsModule());
        register(new HudModule());
        register(new CPSModule());
        register(new TrajectoriesModule());
        register(new FreeLookModule());
        register(new BedwarsModule());
        register(new SafeWalkModule());
        register(new RefillModule());
        register(new WTapModule());
        register(new NoHurtCamModule());
        register(new AnimationsModule());
        register(new BreakProgressModule());
        register(new ChestEspModule());
        register(new ChestStealerModule());
        register(new AutoToolModule());
        register(new BedEspModule());
        register(new FullbrightModule());
        register(new HitParticleEffectsModule());
        register(new IndicatorsModule());
        register(new TimerModule());
        register(new VelocityModule());
        configManager = new ConfigManager(this);
        configModule = new ConfigModule(configManager);
        register(configModule);
        configManager.initialize();
    }

    private void register(Module module) {
        modules.add(module);
        modulesByCategory.get(module.getCategory()).add(module);
    }

    public List<Module> getModules() {
        return Collections.unmodifiableList(modules);
    }

    public List<Module> getModules(Category category) {
        return Collections.unmodifiableList(modulesByCategory.get(category));
    }

    public List<Module> getVisibleModules(Category category) {
        List<Module> source = modulesByCategory.get(category);
        List<Module> visible = new ArrayList<Module>(source.size());
        for (Module module : source) {
            if (module.isVisible()) {
                visible.add(module);
            }
        }
        return Collections.unmodifiableList(visible);
    }

    public <T extends Module> T getModule(Class<T> moduleClass) {
        for (int i = 0; i < modules.size(); i++) {
            Module module = modules.get(i);
            if (moduleClass.isInstance(module)) {
                return moduleClass.cast(module);
            }
        }
        return null;
    }

    public Module getModule(String name) {
        if (name == null) return null;
        String cleanName = name.replace(" ", "").replace("-", "").toLowerCase(java.util.Locale.ROOT);
        for (int i = 0; i < modules.size(); i++) {
            Module module = modules.get(i);
            String mClean = module.getName().replace(" ", "").replace("-", "").toLowerCase(java.util.Locale.ROOT);
            if (mClean.equals(cleanName)) {
                return module;
            }
        }
        return null;
    }

    public void onClientTick() {
        for (int i = 0; i < modules.size(); i++) {
            Module module = modules.get(i);
            if (module.isEnabled()) {
                module.onClientTick();
            }
        }
    }

    public void onClientTick(TickEvent.ClientTickEvent event) {
        for (int i = 0; i < modules.size(); i++) {
            Module module = modules.get(i);
            if (module.isEnabled()) {
                module.onClientTick(event);
            }
        }
    }

    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        for (int i = 0; i < modules.size(); i++) {
            Module module = modules.get(i);
            if (module.isEnabled()) {
                module.onPlayerTick(event);
            }
        }
    }

    public void onPrePlayerInput(com.lionclient.event.PrePlayerInputEvent event) {
        for (int i = 0; i < modules.size(); i++) {
            Module module = modules.get(i);
            if (module.isEnabled()) {
                module.onPrePlayerInput(event);
            }
        }
    }

    public void onMouseEvent(MouseEvent event) {
        for (int i = 0; i < modules.size(); i++) {
            Module module = modules.get(i);
            if (module.isEnabled()) {
                module.onMouseEvent(event);
            }
        }
    }

    public void onPlayerJump(LivingEvent.LivingJumpEvent event) {
        for (int i = 0; i < modules.size(); i++) {
            Module module = modules.get(i);
            if (module.isEnabled()) {
                module.onPlayerJump(event);
            }
        }
    }

    public void onRenderTick(TickEvent.RenderTickEvent event) {
        for (int i = 0; i < modules.size(); i++) {
            Module module = modules.get(i);
            if (module.isEnabled()) {
                module.onRenderTick(event);
            }
        }
    }

    public void onRenderWorld(RenderWorldLastEvent event) {
        for (int i = 0; i < modules.size(); i++) {
            Module module = modules.get(i);
            if (module.isEnabled()) {
                module.onRenderWorld(event);
            }
        }
    }

    public void onRenderOverlay(RenderGameOverlayEvent.Text event) {
        for (int i = 0; i < modules.size(); i++) {
            Module module = modules.get(i);
            if (module.isEnabled()) {
                module.onRenderOverlay(event);
            }
        }
    }

    public int getOutboundPacketDelay(Packet<?> packet) {
        int delay = 0;
        for (int i = 0; i < modules.size(); i++) {
            Module module = modules.get(i);
            if (!module.isEnabled()) {
                continue;
            }

            delay = Math.max(delay, module.getOutboundPacketDelay(packet));
        }
        return delay;
    }

    public void onOutboundPacket(Packet<?> packet) {
        for (int i = 0; i < modules.size(); i++) {
            Module module = modules.get(i);
            if (module.isEnabled()) {
                module.onOutboundPacket(packet);
            }
        }
    }

    public void onOutboundPacketQueued(Packet<?> packet) {
        for (int i = 0; i < modules.size(); i++) {
            Module module = modules.get(i);
            if (module.isEnabled()) {
                module.onOutboundPacketQueued(packet);
            }
        }
    }

    public void onInboundPacket(Packet<?> packet) {
        for (int i = 0; i < modules.size(); i++) {
            Module module = modules.get(i);
            if (module.isEnabled()) {
                module.onInboundPacket(packet);
            }
        }
    }

    public void onInboundPacketQueued(Packet<?> packet) {
        for (int i = 0; i < modules.size(); i++) {
            Module module = modules.get(i);
            module.onInboundPacketQueued(packet);
        }
    }

    public void onInboundPacketReleased(Packet<?> packet) {
        for (int i = 0; i < modules.size(); i++) {
            Module module = modules.get(i);
            module.onInboundPacketReleased(packet);
        }
    }

    public int getInboundPacketDelay(Packet<?> packet) {
        int delay = 0;
        for (Module module : modules) {
            if (!module.isEnabled()) {
                continue;
            }

            delay = Math.max(delay, module.getInboundPacketDelay(packet));
        }
        return delay;
    }

    public boolean isPacketDelayActive() {
        for (Module module : modules) {
            if (module.isEnabled() && module.isPacketDelayActive()) {
                return true;
            }
        }
        return false;
    }

    public boolean isOutboundPacketDelayActive() {
        for (Module module : modules) {
            if (module.isEnabled() && module.isOutboundPacketDelayActive()) {
                return true;
            }
        }
        return false;
    }

    public boolean isInboundPacketDelayActive() {
        for (Module module : modules) {
            if (module.isEnabled() && module.isInboundPacketDelayActive()) {
                return true;
            }
        }
        return false;
    }

    public boolean consumeFlushRequest() {
        for (Module module : modules) {
            if (module.consumeFlushRequest()) {
                return true;
            }
        }
        return false;
    }

    public boolean consumeOutboundFlushRequest() {
        for (Module module : modules) {
            if (module.consumeOutboundFlushRequest()) {
                return true;
            }
        }
        return false;
    }

    public boolean consumeInboundFlushRequest() {
        for (Module module : modules) {
            if (module.consumeInboundFlushRequest()) {
                return true;
            }
        }
        return false;
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public void refreshConfigModule() {
        configModule.rebuildSettings();
    }
}
