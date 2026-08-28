package com.lionclient.feature.module.impl;

import com.lionclient.feature.module.Category;
import com.lionclient.feature.module.Module;
import com.lionclient.feature.setting.BooleanSetting;
import com.lionclient.feature.setting.EnumSetting;
import com.lionclient.feature.setting.NumberSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemArmor;
import net.minecraft.item.ItemStack;
import net.minecraft.util.AxisAlignedBB;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;

public final class PlayerEspModule extends Module {
    private static final double MAX_RENDER_DISTANCE_SQ = 15.0D * 15.0D;

    private final EnumSetting<Mode> mode = new EnumSetting<Mode>("Mode", Mode.values(), Mode.MODERN);
    private final NumberSetting red = new NumberSetting("Red", 0, 255, 5, 255);
    private final NumberSetting green = new NumberSetting("Green", 0, 255, 5, 60);
    private final NumberSetting blue = new NumberSetting("Blue", 0, 255, 5, 60);
    private final BooleanSetting seeInvis = new BooleanSetting("See Invis", false);
    private final BooleanSetting colorTeams = new BooleanSetting("Color Teams", false);
    private net.minecraft.client.renderer.culling.Frustum camera;
    private final float[] colorScratch = new float[3];

    public PlayerEspModule() {
        super("PlayerESP", "Draws a box around other players trough walls.", Category.RENDER, Keyboard.KEY_NONE);
        red.setVisibility(new java.util.function.BooleanSupplier() {
            @Override
            public boolean getAsBoolean() {
                return mode.getValue() == Mode.CLASSIC;
            }
        });
        green.setVisibility(new java.util.function.BooleanSupplier() {
            @Override
            public boolean getAsBoolean() {
                return mode.getValue() == Mode.CLASSIC;
            }
        });
        blue.setVisibility(new java.util.function.BooleanSupplier() {
            @Override
            public boolean getAsBoolean() {
                return mode.getValue() == Mode.CLASSIC;
            }
        });
        addSetting(mode);
        addSetting(red);
        addSetting(green);
        addSetting(blue);
        addSetting(seeInvis);
        addSetting(colorTeams);
    }

    @Override
    public void onRenderWorld(RenderWorldLastEvent event) {
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft.theWorld == null || minecraft.thePlayer == null) {
            return;
        }
        if (camera == null) {
            camera = new net.minecraft.client.renderer.culling.Frustum();
        }

        float partialTicks = event.partialTicks;
        boolean modern = mode.getValue() == Mode.MODERN;

        GL11.glPushMatrix();
        try {
            GlStateManager.disableTexture2D();
            GlStateManager.enableBlend();
            GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, 1, 0);
            GlStateManager.disableDepth();
            GlStateManager.depthMask(false);
            GlStateManager.disableLighting();
            GlStateManager.disableCull();
            GL11.glLineWidth(1.8F);

            double viewerX = minecraft.getRenderManager().viewerPosX;
            double viewerY = minecraft.getRenderManager().viewerPosY;
            double viewerZ = minecraft.getRenderManager().viewerPosZ;

            camera.setPosition(viewerX, viewerY, viewerZ);

            net.minecraft.client.renderer.Tessellator tessellator = net.minecraft.client.renderer.Tessellator.getInstance();
            net.minecraft.client.renderer.WorldRenderer worldrenderer = tessellator.getWorldRenderer();
            java.util.List<EntityPlayer> players = minecraft.theWorld.playerEntities;

            // Batch Pass 1: Filled Quads
            worldrenderer.begin(GL11.GL_QUADS, net.minecraft.client.renderer.vertex.DefaultVertexFormats.POSITION_COLOR);
            for (int i = 0; i < players.size(); i++) {
                EntityPlayer player = players.get(i);
                if (player == minecraft.thePlayer || (!seeInvis.isEnabled() && player.isInvisible()) || AntiBotModule.shouldIgnore(player)) {
                    continue;
                }

                double dx = player.lastTickPosX - viewerX;
                double dy = player.lastTickPosY - viewerY;
                double dz = player.lastTickPosZ - viewerZ;
                if (dx * dx + dy * dy + dz * dz > MAX_RENDER_DISTANCE_SQ) {
                    continue;
                }

                double x = player.lastTickPosX + (player.posX - player.lastTickPosX) * partialTicks - viewerX;
                double y = player.lastTickPosY + (player.posY - player.lastTickPosY) * partialTicks - viewerY;
                double z = player.lastTickPosZ + (player.posZ - player.lastTickPosZ) * partialTicks - viewerZ;

                AxisAlignedBB bb = player.getEntityBoundingBox();
                AxisAlignedBB renderBox = new AxisAlignedBB(
                    bb.minX - player.posX + x,
                    bb.minY - player.posY + y,
                    bb.minZ - player.posZ + z,
                    bb.maxX - player.posX + x,
                    bb.maxY - player.posY + y,
                    bb.maxZ - player.posZ + z
                ).expand(0.05D, 0.1D, 0.05D);

                if (!camera.isBoundingBoxInFrustum(bb)) {
                    continue;
                }

                boolean teamColor = colorTeams.isEnabled() && writeArmorColor(player, colorScratch);
                if (!teamColor) {
                    if (modern) {
                        writeModernColor(player, colorScratch);
                    } else {
                        writeClassicColor(colorScratch);
                    }
                }
                boolean fill = teamColor || modern;
                
                if (fill) {
                    quadBatched(worldrenderer, renderBox, colorScratch[0], colorScratch[1], colorScratch[2], 0.12F);
                }
            }
            tessellator.draw();

            // Batch Pass 2: Outlined Lines
            worldrenderer.begin(GL11.GL_LINES, net.minecraft.client.renderer.vertex.DefaultVertexFormats.POSITION_COLOR);
            for (int i = 0; i < players.size(); i++) {
                EntityPlayer player = players.get(i);
                if (player == minecraft.thePlayer || (!seeInvis.isEnabled() && player.isInvisible()) || AntiBotModule.shouldIgnore(player)) {
                    continue;
                }

                double dx = player.lastTickPosX - viewerX;
                double dy = player.lastTickPosY - viewerY;
                double dz = player.lastTickPosZ - viewerZ;
                if (dx * dx + dy * dy + dz * dz > MAX_RENDER_DISTANCE_SQ) {
                    continue;
                }

                double x = player.lastTickPosX + (player.posX - player.lastTickPosX) * partialTicks - viewerX;
                double y = player.lastTickPosY + (player.posY - player.lastTickPosY) * partialTicks - viewerY;
                double z = player.lastTickPosZ + (player.posZ - player.lastTickPosZ) * partialTicks - viewerZ;

                AxisAlignedBB bb = player.getEntityBoundingBox();
                AxisAlignedBB renderBox = new AxisAlignedBB(
                    bb.minX - player.posX + x,
                    bb.minY - player.posY + y,
                    bb.minZ - player.posZ + z,
                    bb.maxX - player.posX + x,
                    bb.maxY - player.posY + y,
                    bb.maxZ - player.posZ + z
                ).expand(0.05D, 0.1D, 0.05D);

                if (!camera.isBoundingBoxInFrustum(bb)) {
                    continue;
                }

                boolean teamColor = colorTeams.isEnabled() && writeArmorColor(player, colorScratch);
                if (!teamColor) {
                    if (modern) {
                        writeModernColor(player, colorScratch);
                    } else {
                        writeClassicColor(colorScratch);
                    }
                }
                boolean fill = teamColor || modern;
                
                vertexBatched(worldrenderer, renderBox, colorScratch[0], colorScratch[1], colorScratch[2], fill ? 0.95F : 1.0F);
            }
            tessellator.draw();
        } finally {
            GL11.glLineWidth(1.0F);
            GlStateManager.enableCull();
            GlStateManager.disableLighting();
            GlStateManager.depthMask(true);
            GlStateManager.enableDepth();
            GlStateManager.disableBlend();
            GlStateManager.enableTexture2D();
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
            GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
            GL11.glPopMatrix();
        }
    }

    private void vertexBatched(net.minecraft.client.renderer.WorldRenderer wr, AxisAlignedBB bb, float r, float g, float b, float a) {
        line(wr, bb.minX, bb.minY, bb.minZ, bb.maxX, bb.minY, bb.minZ, r, g, b, a);
        line(wr, bb.maxX, bb.minY, bb.minZ, bb.maxX, bb.minY, bb.maxZ, r, g, b, a);
        line(wr, bb.maxX, bb.minY, bb.maxZ, bb.minX, bb.minY, bb.maxZ, r, g, b, a);
        line(wr, bb.minX, bb.minY, bb.maxZ, bb.minX, bb.minY, bb.minZ, r, g, b, a);

        line(wr, bb.minX, bb.maxY, bb.minZ, bb.maxX, bb.maxY, bb.minZ, r, g, b, a);
        line(wr, bb.maxX, bb.maxY, bb.minZ, bb.maxX, bb.maxY, bb.maxZ, r, g, b, a);
        line(wr, bb.maxX, bb.maxY, bb.maxZ, bb.minX, bb.maxY, bb.maxZ, r, g, b, a);
        line(wr, bb.minX, bb.maxY, bb.maxZ, bb.minX, bb.maxY, bb.minZ, r, g, b, a);

        line(wr, bb.minX, bb.minY, bb.minZ, bb.minX, bb.maxY, bb.minZ, r, g, b, a);
        line(wr, bb.maxX, bb.minY, bb.minZ, bb.maxX, bb.maxY, bb.minZ, r, g, b, a);
        line(wr, bb.maxX, bb.minY, bb.maxZ, bb.maxX, bb.maxY, bb.maxZ, r, g, b, a);
        line(wr, bb.minX, bb.minY, bb.maxZ, bb.minX, bb.maxY, bb.maxZ, r, g, b, a);
    }

    private void quadBatched(net.minecraft.client.renderer.WorldRenderer wr, AxisAlignedBB bb, float r, float g, float b, float a) {
        face(wr, bb.minX, bb.minY, bb.minZ, bb.maxX, bb.minY, bb.minZ, bb.maxX, bb.maxY, bb.minZ, bb.minX, bb.maxY, bb.minZ, r, g, b, a);
        face(wr, bb.minX, bb.minY, bb.maxZ, bb.maxX, bb.minY, bb.maxZ, bb.maxX, bb.maxY, bb.maxZ, bb.minX, bb.maxY, bb.maxZ, r, g, b, a);
        face(wr, bb.minX, bb.minY, bb.minZ, bb.minX, bb.minY, bb.maxZ, bb.minX, bb.maxY, bb.maxZ, bb.minX, bb.maxY, bb.minZ, r, g, b, a);
        face(wr, bb.maxX, bb.minY, bb.minZ, bb.maxX, bb.minY, bb.maxZ, bb.maxX, bb.maxY, bb.maxZ, bb.maxX, bb.maxY, bb.minZ, r, g, b, a);
        face(wr, bb.minX, bb.maxY, bb.minZ, bb.maxX, bb.maxY, bb.minZ, bb.maxX, bb.maxY, bb.maxZ, bb.minX, bb.maxY, bb.maxZ, r, g, b, a);
        face(wr, bb.minX, bb.minY, bb.minZ, bb.maxX, bb.minY, bb.minZ, bb.maxX, bb.minY, bb.maxZ, bb.minX, bb.minY, bb.maxZ, r, g, b, a);
    }

    private void line(net.minecraft.client.renderer.WorldRenderer wr, double x1, double y1, double z1, double x2, double y2, double z2, float r, float g, float b, float a) {
        wr.pos(x1, y1, z1).color(r, g, b, a).endVertex();
        wr.pos(x2, y2, z2).color(r, g, b, a).endVertex();
    }

    private void face(net.minecraft.client.renderer.WorldRenderer wr, double x1, double y1, double z1, double x2, double y2, double z2, double x3, double y3, double z3, double x4, double y4, double z4, float r, float g, float b, float a) {
        wr.pos(x1, y1, z1).color(r, g, b, a).endVertex();
        wr.pos(x2, y2, z2).color(r, g, b, a).endVertex();
        wr.pos(x3, y3, z3).color(r, g, b, a).endVertex();
        wr.pos(x4, y4, z4).color(r, g, b, a).endVertex();
    }

    private boolean writeArmorColor(EntityPlayer player, float[] out) {
        ItemStack[] armor = player.inventory.armorInventory;
        if (armor == null) {
            return false;
        }

        for (int i = 0; i < armor.length; i++) {
            ItemStack stack = armor[i];
            if (stack == null || !(stack.getItem() instanceof ItemArmor)) {
                continue;
            }
            ItemArmor itemArmor = (ItemArmor) stack.getItem();
            if (itemArmor.getArmorMaterial() != ItemArmor.ArmorMaterial.LEATHER || !itemArmor.hasColor(stack)) {
                continue;
            }
            int color = itemArmor.getColor(stack);
            out[0] = ((color >> 16) & 255) / 255.0F;
            out[1] = ((color >> 8) & 255) / 255.0F;
            out[2] = (color & 255) / 255.0F;
            return true;
        }
        return false;
    }

    private void writeClassicColor(float[] out) {
        out[0] = red.getValue() / 255.0F;
        out[1] = green.getValue() / 255.0F;
        out[2] = blue.getValue() / 255.0F;
    }

    private void writeModernColor(EntityPlayer player, float[] out) {
        double time = System.currentTimeMillis() / 340.0D;
        float wave = (float) ((Math.sin(time + (player.getEntityId() * 0.35D)) + 1.0D) * 0.5D);
        int color = ClickGuiModule.blendColor(ClickGuiModule.getLightAccentColor(), ClickGuiModule.getDarkAccentColor(), wave);
        out[0] = ((color >>> 16) & 255) / 255.0F;
        out[1] = ((color >>> 8) & 255) / 255.0F;
        out[2] = (color & 255) / 255.0F;
    }

    private enum Mode {
        MODERN("Modern"),
        CLASSIC("Classic");

        private final String label;

        Mode(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }
}
