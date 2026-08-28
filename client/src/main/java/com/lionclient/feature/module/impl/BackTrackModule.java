package com.lionclient.feature.module.impl;

import com.lionclient.feature.module.Category;
import com.lionclient.feature.module.Module;
import com.lionclient.feature.setting.BooleanSetting;
import com.lionclient.feature.setting.NumberSetting;
import com.lionclient.feature.setting.DecimalSetting;
import com.lionclient.feature.setting.EnumSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.S14PacketEntity;
import net.minecraft.network.play.server.S18PacketEntityTeleport;
import net.minecraft.util.AxisAlignedBB;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;

public class BackTrackModule extends Module {

    public enum TargetMode {
        ATTACK("Attack"),
        RANGE("Range");

        private final String name;
        TargetMode(String name) { this.name = name; }
        @Override public String toString() { return name; }
    }

    private final EnumSetting<TargetMode> targetMode = new EnumSetting<>("Target Mode", TargetMode.values(), TargetMode.ATTACK);
    private final NumberSetting delayMs = new NumberSetting("Delay", 0, 500, 10, 90);
    private final DecimalSetting maxRange = new DecimalSetting("Max Range", 3.0, 8.0, 0.1, 6.0);
    private final NumberSetting nextBacktrackDelay = new NumberSetting("Next Delay", 0, 2000, 50, 100);
    private final NumberSetting trackingBuffer = new NumberSetting("Tracking Buffer", 0, 2000, 50, 500);
    private final BooleanSetting pauseOnHurtTime = new BooleanSetting("Pause On HurtTime", false);
    private final NumberSetting hurtTimeValue = new NumberSetting("HurtTime", 0, 10, 1, 3);
    private final BooleanSetting render = new BooleanSetting("Render", true);

    private EntityLivingBase target;
    private double targetX, targetY, targetZ;
    private double realX, realY, realZ;
    private boolean tracking = false;
    
    private long trackingBufferMs;
    private long lastClearTime;

    public BackTrackModule() {
        super("BackTrack", "Delays enemy position packets to extend reach.", Category.COMBAT, Keyboard.KEY_NONE);
        addSetting(targetMode);
        addSetting(delayMs);
        addSetting(maxRange);
        addSetting(nextBacktrackDelay);
        addSetting(trackingBuffer);
        addSetting(pauseOnHurtTime);
        addSetting(hurtTimeValue);
        addSetting(render);
    }

    @Override
    protected void onEnable() {
        target = null;
        tracking = false;
        trackingBufferMs = 0;
        lastClearTime = 0;
    }

    @Override
    protected void onDisable() {
        clear();
    }

    @Override
    public int getInboundPacketDelay(Packet<?> packet) {
        if (!isEnabled() || !tracking || target == null) return 0;

        Minecraft mc = Minecraft.getMinecraft();
        if (!shouldBacktrack(mc, target)) {
            return 0;
        }

        if (packet instanceof S14PacketEntity) {
            S14PacketEntity s14 = (S14PacketEntity) packet;
            if (s14.getEntity(mc.theWorld) == target) {
                return (int) delayMs.getValue();
            }
        } else if (packet instanceof S18PacketEntityTeleport) {
            S18PacketEntityTeleport s18 = (S18PacketEntityTeleport) packet;
            if (s18.getEntityId() == target.getEntityId()) {
                return (int) delayMs.getValue();
            }
        }
        return 0;
    }

    @Override
    public boolean isPacketDelayActive() {
        return isEnabled() && tracking && target != null;
    }

    @Override
    public void onInboundPacketQueued(Packet<?> packet) {
        if (!isEnabled() || !tracking || target == null) return;

        Minecraft mc = Minecraft.getMinecraft();
        if (packet instanceof S14PacketEntity) {
            S14PacketEntity s14 = (S14PacketEntity) packet;
            if (s14.getEntity(mc.theWorld) == target) {
                realX += s14.func_149062_c() / 32.0;
                realY += s14.func_149061_d() / 32.0;
                realZ += s14.func_149064_e() / 32.0;
                
                checkDistanceSafety(mc);
            }
        } else if (packet instanceof S18PacketEntityTeleport) {
            S18PacketEntityTeleport s18 = (S18PacketEntityTeleport) packet;
            if (s18.getEntityId() == target.getEntityId()) {
                realX = s18.getX() / 32.0;
                realY = s18.getY() / 32.0;
                realZ = s18.getZ() / 32.0;
                
                checkDistanceSafety(mc);
            }
        }
    }

    @Override
    public void onInboundPacketReleased(Packet<?> packet) {
        if (!isEnabled() || !tracking || target == null) return;

        Minecraft mc = Minecraft.getMinecraft();
        if (packet instanceof S14PacketEntity) {
            S14PacketEntity s14 = (S14PacketEntity) packet;
            if (s14.getEntity(mc.theWorld) == target) {
                targetX += s14.func_149062_c() / 32.0;
                targetY += s14.func_149061_d() / 32.0;
                targetZ += s14.func_149064_e() / 32.0;
            }
        } else if (packet instanceof S18PacketEntityTeleport) {
            S18PacketEntityTeleport s18 = (S18PacketEntityTeleport) packet;
            if (s18.getEntityId() == target.getEntityId()) {
                targetX = s18.getX() / 32.0;
                targetY = s18.getY() / 32.0;
                targetZ = s18.getZ() / 32.0;
            }
        }
    }

    @Override
    public void onOutboundPacket(Packet<?> packet) {
        if (!isEnabled()) return;
        if (targetMode.getValue() != TargetMode.ATTACK) return;
        if (!(packet instanceof net.minecraft.network.play.client.C02PacketUseEntity)) return;

        net.minecraft.network.play.client.C02PacketUseEntity wrapper = (net.minecraft.network.play.client.C02PacketUseEntity) packet;
        if (wrapper.getAction() != net.minecraft.network.play.client.C02PacketUseEntity.Action.ATTACK) return;

        Minecraft mc = Minecraft.getMinecraft();
        Entity entity = wrapper.getEntityFromWorld(mc.theWorld);
        if (!(entity instanceof EntityLivingBase) || !com.lionclient.util.TargetFilter.isValidTarget(entity)) return;

        processTarget((EntityLivingBase) entity);
    }

    @Override
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;

        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null || mc.theWorld == null || mc.thePlayer.isDead) {
            clear();
            return;
        }

        if (targetMode.getValue() == TargetMode.RANGE) {
            EntityLivingBase enemy = findEnemy(mc);
            if (enemy == null) {
                clear();
                return;
            }
            processTarget(enemy);
        }

        if (target == null || !tracking) return;

        if (!shouldBacktrack(mc, target)) {
            clear();
        }
    }

    private void processTarget(EntityLivingBase enemy) {
        Minecraft mc = Minecraft.getMinecraft();
        if (!shouldBacktrack(mc, enemy)) {
            return;
        }

        if (enemy != target) {
            clear();
            target = enemy;
            targetX = enemy.posX;
            targetY = enemy.posY;
            targetZ = enemy.posZ;
            realX = enemy.posX;
            realY = enemy.posY;
            realZ = enemy.posZ;
            tracking = true;
        }
    }

    private boolean shouldBacktrack(Minecraft mc, EntityLivingBase targetEntity) {
        if (targetEntity == null || !targetEntity.isEntityAlive()) return false;

        double dist = mc.thePlayer.getDistanceToEntity(targetEntity);
        boolean inRange = dist <= maxRange.getValue();

        if (inRange) {
            trackingBufferMs = System.currentTimeMillis();
        }

        boolean isTrackingBuffered = inRange || (System.currentTimeMillis() - trackingBufferMs <= trackingBuffer.getValue());
        boolean isOnCooldown = System.currentTimeMillis() - lastClearTime < nextBacktrackDelay.getValue();
        boolean isHurtTimePaused = pauseOnHurtTime.isEnabled() && targetEntity.hurtTime >= hurtTimeValue.getValue();

        return isTrackingBuffered && !isOnCooldown && !isHurtTimePaused;
    }

    private void checkDistanceSafety(Minecraft mc) {
        if (target == null) return;

        double realDistSq = mc.thePlayer.getDistanceSq(realX, realY, realZ);
        double laggedDistSq = mc.thePlayer.getDistanceSq(target.posX, target.posY, target.posZ);

        if (realDistSq < laggedDistSq) {
            clear();
        }
    }

    private EntityLivingBase findEnemy(Minecraft mc) {
        EntityLivingBase nearest = null;
        double nearestDist = maxRange.getValue();

        for (Object o : mc.theWorld.loadedEntityList) {
            if (!(o instanceof EntityLivingBase)) continue;
            EntityLivingBase e = (EntityLivingBase) o;
            if (e == mc.thePlayer) continue;
            if (!e.isEntityAlive() || e.getHealth() <= 0.0f) continue;
            if (e instanceof EntityPlayer) {
                double dist = mc.thePlayer.getDistanceToEntity(e);
                if (dist <= nearestDist) {
                    nearestDist = dist;
                    nearest = e;
                }
            }
        }
        return nearest;
    }

    public void clear() {
        Minecraft mc = Minecraft.getMinecraft();
        com.lionclient.LionClient client = com.lionclient.LionClient.getInstance();
        if (client != null && client.getPacketStallHandler() != null) {
            client.getPacketStallHandler().flush();
        }

        if (target != null) {
            lastClearTime = System.currentTimeMillis();
        }

        target = null;
        tracking = false;
    }

    @Override
    public void onRenderWorld(RenderWorldLastEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.theWorld == null || mc.thePlayer == null) {
            clear();
            return;
        }
        if (!render.isEnabled() || !tracking || target == null || target.isDead || target.worldObj != mc.theWorld) return;

        RenderManager rm = mc.getRenderManager();

        double x = targetX - rm.viewerPosX;
        double y = targetY - rm.viewerPosY;
        double z = targetZ - rm.viewerPosZ;

        double w = target.width / 2.0 + 0.05;
        double h = target.height + 0.1;

        int color = 0xFF5CA8;
        float r = ((color >> 16) & 255) / 255.0f;
        float g = ((color >> 8) & 255) / 255.0f;
        float b = (color & 255) / 255.0f;
        float a = 0.5f;

        AxisAlignedBB bb = new AxisAlignedBB(x - w, y, z - w, x + w, y + h, z + w);

        GlStateManager.pushMatrix();
        GlStateManager.disableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, 1, 0);
        GlStateManager.disableDepth();
        GlStateManager.depthMask(false);

        GlStateManager.color(r, g, b, a);
        net.minecraft.client.renderer.Tessellator tessellator = net.minecraft.client.renderer.Tessellator.getInstance();
        net.minecraft.client.renderer.WorldRenderer worldrenderer = tessellator.getWorldRenderer();
        worldrenderer.begin(GL11.GL_QUADS, net.minecraft.client.renderer.vertex.DefaultVertexFormats.POSITION);
        worldrenderer.pos(bb.minX, bb.minY, bb.minZ).endVertex();
        worldrenderer.pos(bb.maxX, bb.minY, bb.minZ).endVertex();
        worldrenderer.pos(bb.maxX, bb.minY, bb.maxZ).endVertex();
        worldrenderer.pos(bb.minX, bb.minY, bb.maxZ).endVertex();
        worldrenderer.pos(bb.minX, bb.maxY, bb.minZ).endVertex();
        worldrenderer.pos(bb.minX, bb.maxY, bb.maxZ).endVertex();
        worldrenderer.pos(bb.maxX, bb.maxY, bb.maxZ).endVertex();
        worldrenderer.pos(bb.maxX, bb.maxY, bb.minZ).endVertex();
        worldrenderer.pos(bb.minX, bb.minY, bb.minZ).endVertex();
        worldrenderer.pos(bb.minX, bb.maxY, bb.minZ).endVertex();
        worldrenderer.pos(bb.maxX, bb.maxY, bb.minZ).endVertex();
        worldrenderer.pos(bb.maxX, bb.minY, bb.minZ).endVertex();
        worldrenderer.pos(bb.maxX, bb.minY, bb.maxZ).endVertex();
        worldrenderer.pos(bb.maxX, bb.maxY, bb.maxZ).endVertex();
        worldrenderer.pos(bb.minX, bb.maxY, bb.maxZ).endVertex();
        worldrenderer.pos(bb.minX, bb.minY, bb.maxZ).endVertex();
        tessellator.draw();

        GlStateManager.enableDepth();
        GlStateManager.depthMask(true);
        GlStateManager.disableBlend();
        GlStateManager.enableTexture2D();
        GlStateManager.popMatrix();
    }

    @Override
    public String getHudInfo() {
        return target != null ? "T:" + target.getName() : "";
    }
}