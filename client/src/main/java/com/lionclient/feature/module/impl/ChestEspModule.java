package com.lionclient.feature.module.impl;

import com.lionclient.feature.module.Category;
import com.lionclient.feature.module.Module;
import com.lionclient.feature.setting.BooleanSetting;
import net.minecraft.block.Block;
import net.minecraft.block.BlockChest;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityChest;
import net.minecraft.tileentity.TileEntityEnderChest;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.Vec3;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;

public final class ChestEspModule extends Module {

    private final BooleanSetting regularChests = new BooleanSetting("Chests", true);
    private final BooleanSetting enderChests = new BooleanSetting("Ender Chests", true);
    private final BooleanSetting trappedChests = new BooleanSetting("Trapped Chests", true);
    private final BooleanSetting box = new BooleanSetting("Box", true);
    private final BooleanSetting filled = new BooleanSetting("Filled", true);
    private final BooleanSetting tracers = new BooleanSetting("Tracers", false);
    private net.minecraft.client.renderer.culling.Frustum camera;

    public ChestEspModule() {
        super("ChestESP", "Highlights chests and ender chests through walls.", Category.RENDER, Keyboard.KEY_NONE);
        addSetting(regularChests);
        addSetting(enderChests);
        addSetting(trappedChests);
        addSetting(box);
        addSetting(filled);
        addSetting(tracers);
    }

    @Override
    public void onRenderWorld(RenderWorldLastEvent event) {
        if (!isEnabled()) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.theWorld == null || mc.thePlayer == null || mc.getRenderManager() == null) return;

        double viewerX = mc.getRenderManager().viewerPosX;
        double viewerY = mc.getRenderManager().viewerPosY;
        double viewerZ = mc.getRenderManager().viewerPosZ;

        if (camera == null) camera = new net.minecraft.client.renderer.culling.Frustum();
        camera.setPosition(viewerX, viewerY, viewerZ);

        GlStateManager.pushMatrix();
        GlStateManager.disableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, 1, 0);
        GlStateManager.disableDepth();
        GlStateManager.depthMask(false);
        GlStateManager.disableLighting();

        net.minecraft.client.renderer.Tessellator tessellator = net.minecraft.client.renderer.Tessellator.getInstance();
        net.minecraft.client.renderer.WorldRenderer worldrenderer = tessellator.getWorldRenderer();

        // Pass 1: Filled Quads
        if (filled.isEnabled()) {
            worldrenderer.begin(GL11.GL_QUADS, net.minecraft.client.renderer.vertex.DefaultVertexFormats.POSITION_COLOR);
            for (Object obj : mc.theWorld.loadedTileEntityList) {
                if (!(obj instanceof TileEntityChest) && !(obj instanceof TileEntityEnderChest)) continue;
                TileEntity chest = (TileEntity) obj;
                Block block = mc.theWorld.getBlockState(chest.getPos()).getBlock();
                boolean isEnder = chest instanceof TileEntityEnderChest;
                boolean isTrapped = !isEnder && block instanceof BlockChest && block.canProvidePower();

                if (isEnder && !enderChests.isEnabled()) continue;
                if (isTrapped && !trappedChests.isEnabled()) continue;
                if (!isEnder && !isTrapped && !regularChests.isEnabled()) continue;

                double minX = 0.0625, minZ = 0.0625, maxX = 0.9375, maxZ = 0.9375;
                if (block instanceof BlockChest) {
                    EnumFacing facing = mc.theWorld.getBlockState(chest.getPos()).getValue(BlockChest.FACING);
                    switch (facing) {
                        case NORTH:
                            if (mc.theWorld.getBlockState(chest.getPos().east()).getBlock() == block) continue;
                            else if (mc.theWorld.getBlockState(chest.getPos().west()).getBlock() == block) minX -= 1.0;
                            break;
                        case SOUTH:
                            if (mc.theWorld.getBlockState(chest.getPos().west()).getBlock() == block) continue;
                            else if (mc.theWorld.getBlockState(chest.getPos().east()).getBlock() == block) maxX += 1.0;
                            break;
                        case WEST:
                            if (mc.theWorld.getBlockState(chest.getPos().north()).getBlock() == block) continue;
                            else if (mc.theWorld.getBlockState(chest.getPos().south()).getBlock() == block) maxZ += 1.0;
                            break;
                        case EAST:
                            if (mc.theWorld.getBlockState(chest.getPos().south()).getBlock() == block) continue;
                            else if (mc.theWorld.getBlockState(chest.getPos().north()).getBlock() == block) minZ -= 1.0;
                            break;
                        default: break;
                    }
                }

                double posX = chest.getPos().getX() - viewerX;
                double posY = chest.getPos().getY() - viewerY;
                double posZ = chest.getPos().getZ() - viewerZ;

                AxisAlignedBB bb = new AxisAlignedBB(posX + minX, posY, posZ + minZ, posX + maxX, posY + 0.875, posZ + maxZ);
                if (!camera.isBoundingBoxInFrustum(bb.offset(viewerX, viewerY, viewerZ))) continue;

                float r = 1.0f, g = 0.66f, b = 0.0f;
                if (isTrapped) { r = 1.0f; g = 0.17f; b = 0.0f; }
                else if (isEnder) { r = 0.54f; g = 0.17f; b = 0.88f; }

                quadBatched(worldrenderer, bb, r, g, b, 0.22f);
            }
            tessellator.draw();
        }

        // Pass 2: Outlined Boxes
        if (box.isEnabled()) {
            GL11.glLineWidth(1.5f);
            worldrenderer.begin(GL11.GL_LINES, net.minecraft.client.renderer.vertex.DefaultVertexFormats.POSITION_COLOR);
            for (Object obj : mc.theWorld.loadedTileEntityList) {
                if (!(obj instanceof TileEntityChest) && !(obj instanceof TileEntityEnderChest)) continue;
                TileEntity chest = (TileEntity) obj;
                Block block = mc.theWorld.getBlockState(chest.getPos()).getBlock();
                boolean isEnder = chest instanceof TileEntityEnderChest;
                boolean isTrapped = !isEnder && block instanceof BlockChest && block.canProvidePower();

                if (isEnder && !enderChests.isEnabled()) continue;
                if (isTrapped && !trappedChests.isEnabled()) continue;
                if (!isEnder && !isTrapped && !regularChests.isEnabled()) continue;

                double minX = 0.0625, minZ = 0.0625, maxX = 0.9375, maxZ = 0.9375;
                if (block instanceof BlockChest) {
                    EnumFacing facing = mc.theWorld.getBlockState(chest.getPos()).getValue(BlockChest.FACING);
                    switch (facing) {
                        case NORTH:
                            if (mc.theWorld.getBlockState(chest.getPos().east()).getBlock() == block) continue;
                            else if (mc.theWorld.getBlockState(chest.getPos().west()).getBlock() == block) minX -= 1.0;
                            break;
                        case SOUTH:
                            if (mc.theWorld.getBlockState(chest.getPos().west()).getBlock() == block) continue;
                            else if (mc.theWorld.getBlockState(chest.getPos().east()).getBlock() == block) maxX += 1.0;
                            break;
                        case WEST:
                            if (mc.theWorld.getBlockState(chest.getPos().north()).getBlock() == block) continue;
                            else if (mc.theWorld.getBlockState(chest.getPos().south()).getBlock() == block) maxZ += 1.0;
                            break;
                        case EAST:
                            if (mc.theWorld.getBlockState(chest.getPos().south()).getBlock() == block) continue;
                            else if (mc.theWorld.getBlockState(chest.getPos().north()).getBlock() == block) minZ -= 1.0;
                            break;
                        default: break;
                    }
                }

                double posX = chest.getPos().getX() - viewerX;
                double posY = chest.getPos().getY() - viewerY;
                double posZ = chest.getPos().getZ() - viewerZ;

                AxisAlignedBB bb = new AxisAlignedBB(posX + minX, posY, posZ + minZ, posX + maxX, posY + 0.875, posZ + maxZ);
                if (!camera.isBoundingBoxInFrustum(bb.offset(viewerX, viewerY, viewerZ))) continue;

                float r = 1.0f, g = 0.66f, b = 0.0f;
                if (isTrapped) { r = 1.0f; g = 0.17f; b = 0.0f; }
                else if (isEnder) { r = 0.54f; g = 0.17f; b = 0.88f; }

                boxBatched(worldrenderer, bb, r, g, b, 0.85f);
            }
            tessellator.draw();
        }

        // Pass 3: Tracers
        if (tracers.isEnabled()) {
            GL11.glLineWidth(1.2f);
            Vec3 eyes = mc.thePlayer.getPositionEyes(event.partialTicks).subtract(viewerX, viewerY, viewerZ);
            worldrenderer.begin(GL11.GL_LINES, net.minecraft.client.renderer.vertex.DefaultVertexFormats.POSITION_COLOR);
            for (Object obj : mc.theWorld.loadedTileEntityList) {
                if (!(obj instanceof TileEntityChest) && !(obj instanceof TileEntityEnderChest)) continue;
                TileEntity chest = (TileEntity) obj;
                Block block = mc.theWorld.getBlockState(chest.getPos()).getBlock();
                boolean isEnder = chest instanceof TileEntityEnderChest;
                boolean isTrapped = !isEnder && block instanceof BlockChest && block.canProvidePower();

                if (isEnder && !enderChests.isEnabled()) continue;
                if (isTrapped && !trappedChests.isEnabled()) continue;
                if (!isEnder && !isTrapped && !regularChests.isEnabled()) continue;

                double minX = 0.0625, minZ = 0.0625, maxX = 0.9375, maxZ = 0.9375;
                double posX = chest.getPos().getX() - viewerX;
                double posY = chest.getPos().getY() - viewerY;
                double posZ = chest.getPos().getZ() - viewerZ;
                AxisAlignedBB bb = new AxisAlignedBB(posX + minX, posY, posZ + minZ, posX + maxX, posY + 0.875, posZ + maxZ);
                if (!camera.isBoundingBoxInFrustum(bb.offset(viewerX, viewerY, viewerZ))) continue;

                float r = 1.0f, g = 0.66f, b = 0.0f;
                if (isTrapped) { r = 1.0f; g = 0.17f; b = 0.0f; }
                else if (isEnder) { r = 0.54f; g = 0.17f; b = 0.88f; }

                double chestCenterX = posX + (minX + maxX) / 2.0;
                double chestCenterY = posY + 0.4375;
                double chestCenterZ = posZ + (minZ + maxZ) / 2.0;

                worldrenderer.pos(eyes.xCoord, eyes.yCoord, eyes.zCoord).color(r, g, b, 0.6f).endVertex();
                worldrenderer.pos(chestCenterX, chestCenterY, chestCenterZ).color(r, g, b, 0.6f).endVertex();
            }
            tessellator.draw();
        }

        GlStateManager.enableDepth();
        GlStateManager.depthMask(true);
        GlStateManager.enableTexture2D();
        GlStateManager.disableBlend();
        GlStateManager.popMatrix();
    }

    private void boxBatched(net.minecraft.client.renderer.WorldRenderer wr, AxisAlignedBB bb, float r, float g, float b, float a) {
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
}
