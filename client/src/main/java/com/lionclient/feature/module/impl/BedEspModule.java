package com.lionclient.feature.module.impl;

import com.lionclient.feature.module.Category;
import com.lionclient.feature.module.Module;
import com.lionclient.feature.setting.BooleanSetting;
import com.lionclient.feature.setting.EnumSetting;
import com.lionclient.feature.setting.NumberSetting;
import net.minecraft.block.BlockBed;
import net.minecraft.block.BlockBed.EnumPartType;
import net.minecraft.block.BlockObsidian;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.List;

public final class BedEspModule extends Module {
    private static final int SCAN_INTERVAL_TICKS = 10;
    private static final int CHUNK_SCAN_RADIUS = 4;
    private static final EnumFacing[] OBSIDIAN_SIDES = {
            EnumFacing.UP,
            EnumFacing.NORTH,
            EnumFacing.EAST,
            EnumFacing.SOUTH,
            EnumFacing.WEST
    };

    public enum HeightMode {
        DEFAULT("Default"),
        FULL("Full");

        private final String name;
        HeightMode(String name) { this.name = name; }
        @Override public String toString() { return name; }
    }

    public enum ColorMode {
        CUSTOM("Custom"),
        HUD("HUD");

        private final String name;
        ColorMode(String name) { this.name = name; }
        @Override public String toString() { return name; }
    }

    private final EnumSetting<HeightMode> mode = new EnumSetting<>("Mode", HeightMode.values(), HeightMode.DEFAULT);
    private final EnumSetting<ColorMode> colorMode = new EnumSetting<>("Color", ColorMode.values(), ColorMode.CUSTOM);
    private final NumberSetting red = new NumberSetting("Red", 0, 255, 5, 255);
    private final NumberSetting green = new NumberSetting("Green", 0, 255, 5, 50);
    private final NumberSetting blue = new NumberSetting("Blue", 0, 255, 5, 50);
    private final NumberSetting opacity = new NumberSetting("Opacity", 0, 100, 5, 25);
    private final BooleanSetting outline = new BooleanSetting("Outline", true);
    private final BooleanSetting obsidian = new BooleanSetting("Obsidian", true);

    private final List<BlockPos> beds = new ArrayList<BlockPos>(64);
    private final List<BlockPos> scanResults = new ArrayList<BlockPos>(64);
    private net.minecraft.client.renderer.culling.Frustum camera;
    private int scanCooldownTicks;

    public BedEspModule() {
        super("BedESP", "Highlights beds and obsidian defenses through walls.", Category.RENDER, Keyboard.KEY_NONE);
        addSetting(mode);
        addSetting(colorMode);
        addSetting(red);
        addSetting(green);
        addSetting(blue);
        addSetting(opacity);
        addSetting(outline);
        addSetting(obsidian);
    }

    @Override
    protected void onEnable() {
        beds.clear();
        scanResults.clear();
        scanCooldownTicks = 0;
    }

    @Override
    protected void onDisable() {
        beds.clear();
        scanResults.clear();
        scanCooldownTicks = 0;
    }

    @Override
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START || !isEnabled()) return;

        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null || mc.theWorld == null) {
            beds.clear();
            scanResults.clear();
            scanCooldownTicks = 0;
            return;
        }

        if (scanCooldownTicks > 0) {
            scanCooldownTicks--;
            return;
        }
        scanCooldownTicks = SCAN_INTERVAL_TICKS;

        int playerChunkX = mc.thePlayer.chunkCoordX;
        int playerChunkZ = mc.thePlayer.chunkCoordZ;

        scanResults.clear();
        for (int cx = playerChunkX - CHUNK_SCAN_RADIUS; cx <= playerChunkX + CHUNK_SCAN_RADIUS; cx++) {
            for (int cz = playerChunkZ - CHUNK_SCAN_RADIUS; cz <= playerChunkZ + CHUNK_SCAN_RADIUS; cz++) {
                if (mc.theWorld.getChunkProvider().chunkExists(cx, cz)) {
                    Chunk chunk = mc.theWorld.getChunkFromChunkCoords(cx, cz);
                    int startX = cx << 4;
                    int startZ = cz << 4;

                    for (int x = 0; x < 16; x++) {
                        for (int z = 0; z < 16; z++) {
                            for (int y = 0; y < 256; y++) {
                                BlockPos pos = new BlockPos(startX + x, y, startZ + z);
                                IBlockState state = chunk.getBlockState(pos);
                                if (state.getBlock() instanceof BlockBed && state.getValue(BlockBed.PART) == EnumPartType.HEAD) {
                                    scanResults.add(pos);
                                }
                            }
                        }
                    }
                }
            }
        }

        beds.clear();
        beds.addAll(scanResults);
    }

    @Override
    public void onRenderWorld(RenderWorldLastEvent event) {
        if (!isEnabled()) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.theWorld == null || mc.thePlayer == null || mc.getRenderManager() == null) return;
        if (beds.isEmpty()) return;

        double viewerX = mc.getRenderManager().viewerPosX;
        double viewerY = mc.getRenderManager().viewerPosY;
        double viewerZ = mc.getRenderManager().viewerPosZ;

        float r = red.getValue() / 255.0f;
        float g = green.getValue() / 255.0f;
        float b = blue.getValue() / 255.0f;
        float alpha = opacity.getValue() / 100.0f;
        double bedHeight = mode.getValue() == HeightMode.FULL ? 1.0 : 0.5625;

        GlStateManager.pushMatrix();
        GlStateManager.disableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, 1, 0);
        GlStateManager.disableDepth();
        GlStateManager.depthMask(false);
        GlStateManager.disableLighting();

        if (camera == null) camera = new net.minecraft.client.renderer.culling.Frustum();
        camera.setPosition(viewerX, viewerY, viewerZ);

        net.minecraft.client.renderer.Tessellator tessellator = net.minecraft.client.renderer.Tessellator.getInstance();
        net.minecraft.client.renderer.WorldRenderer worldrenderer = tessellator.getWorldRenderer();

        for (int i = 0; i < beds.size(); i++) {
            BlockPos headPos = beds.get(i);
            IBlockState state = mc.theWorld.getBlockState(headPos);
            if (!(state.getBlock() instanceof BlockBed) || state.getValue(BlockBed.PART) != EnumPartType.HEAD) {
                beds.remove(i--);
                continue;
            }

            EnumFacing facing = state.getValue(BlockBed.FACING);
            BlockPos footPos = headPos.offset(facing.getOpposite());
            IBlockState footState = mc.theWorld.getBlockState(footPos);

            if (!(footState.getBlock() instanceof BlockBed) || footState.getValue(BlockBed.PART) != EnumPartType.FOOT) {
                continue;
            }

            double minX = Math.min(headPos.getX(), footPos.getX()) - viewerX;
            double minY = headPos.getY() - viewerY;
            double minZ = Math.min(headPos.getZ(), footPos.getZ()) - viewerZ;
            double maxX = Math.max(headPos.getX() + 1.0, footPos.getX() + 1.0) - viewerX;
            double maxY = headPos.getY() + bedHeight - viewerY;
            double maxZ = Math.max(headPos.getZ() + 1.0, footPos.getZ() + 1.0) - viewerZ;

            AxisAlignedBB bb = new AxisAlignedBB(minX, minY, minZ, maxX, maxY, maxZ);

            if (!camera.isBoundingBoxInFrustum(bb.offset(viewerX, viewerY, viewerZ))) {
                continue;
            }

            // Draw Obsidian Defense highlights if enabled
            if (obsidian.isEnabled()) {
                for (int sideIndex = 0; sideIndex < OBSIDIAN_SIDES.length; sideIndex++) {
                    EnumFacing side = OBSIDIAN_SIDES[sideIndex];
                    BlockPos headOffset = headPos.offset(side);
                    BlockPos footOffset = footPos.offset(side);
                    boolean hObsidian = mc.theWorld.getBlockState(headOffset).getBlock() instanceof BlockObsidian;
                    boolean fObsidian = mc.theWorld.getBlockState(footOffset).getBlock() instanceof BlockObsidian;

                    if (hObsidian && fObsidian) {
                        drawObsidianBox(worldrenderer, tessellator, viewerX, viewerY, viewerZ,
                            Math.min(headOffset.getX(), footOffset.getX()),
                            headOffset.getY(),
                            Math.min(headOffset.getZ(), footOffset.getZ()),
                            Math.max(headOffset.getX() + 1.0, footOffset.getX() + 1.0),
                            headOffset.getY() + 1.0,
                            Math.max(headOffset.getZ() + 1.0, footOffset.getZ() + 1.0)
                        );
                    } else if (hObsidian) {
                        drawSingleObsidianBlock(worldrenderer, tessellator, viewerX, viewerY, viewerZ, headOffset);
                    } else if (fObsidian) {
                        drawSingleObsidianBlock(worldrenderer, tessellator, viewerX, viewerY, viewerZ, footOffset);
                    }
                }
            }

            GlStateManager.color(r, g, b, alpha);
            drawFilledBox(worldrenderer, tessellator, bb);

            if (outline.isEnabled()) {
                GlStateManager.color(r, g, b, 0.9f);
                GL11.glLineWidth(1.5f);
                RenderGlobal.drawSelectionBoundingBox(bb);
            }
        }

        GlStateManager.enableDepth();
        GlStateManager.depthMask(true);
        GlStateManager.enableTexture2D();
        GlStateManager.disableBlend();
        GlStateManager.popMatrix();
    }

    private void drawObsidianBox(net.minecraft.client.renderer.WorldRenderer worldrenderer, net.minecraft.client.renderer.Tessellator tessellator, double vx, double vy, double vz, double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        AxisAlignedBB bb = new AxisAlignedBB(minX - vx, minY - vy, minZ - vz, maxX - vx, maxY - vy, maxZ - vz);
        // Purple color for Obsidian (0.66, 0.0, 0.66)
        GlStateManager.color(0.66f, 0.0f, 0.66f, 0.35f);
        drawFilledBox(worldrenderer, tessellator, bb);
        if (outline.isEnabled()) {
            GlStateManager.color(0.66f, 0.0f, 0.66f, 0.9f);
            GL11.glLineWidth(1.5f);
            RenderGlobal.drawSelectionBoundingBox(bb);
        }
    }

    private void drawSingleObsidianBlock(net.minecraft.client.renderer.WorldRenderer worldrenderer, net.minecraft.client.renderer.Tessellator tessellator, double vx, double vy, double vz, BlockPos pos) {
        drawObsidianBox(worldrenderer, tessellator, vx, vy, vz, pos.getX(), pos.getY(), pos.getZ(), pos.getX() + 1.0, pos.getY() + 1.0, pos.getZ() + 1.0);
    }

    private void drawFilledBox(net.minecraft.client.renderer.WorldRenderer worldrenderer, net.minecraft.client.renderer.Tessellator tessellator, AxisAlignedBB bb) {
        worldrenderer.begin(GL11.GL_QUADS, net.minecraft.client.renderer.vertex.DefaultVertexFormats.POSITION);
        // Bottom
        worldrenderer.pos(bb.minX, bb.minY, bb.minZ).endVertex();
        worldrenderer.pos(bb.maxX, bb.minY, bb.minZ).endVertex();
        worldrenderer.pos(bb.maxX, bb.minY, bb.maxZ).endVertex();
        worldrenderer.pos(bb.minX, bb.minY, bb.maxZ).endVertex();
        // Top
        worldrenderer.pos(bb.minX, bb.maxY, bb.minZ).endVertex();
        worldrenderer.pos(bb.minX, bb.maxY, bb.maxZ).endVertex();
        worldrenderer.pos(bb.maxX, bb.maxY, bb.maxZ).endVertex();
        worldrenderer.pos(bb.maxX, bb.maxY, bb.minZ).endVertex();
        // North
        worldrenderer.pos(bb.minX, bb.minY, bb.minZ).endVertex();
        worldrenderer.pos(bb.minX, bb.maxY, bb.minZ).endVertex();
        worldrenderer.pos(bb.maxX, bb.maxY, bb.minZ).endVertex();
        worldrenderer.pos(bb.maxX, bb.minY, bb.minZ).endVertex();
        // South
        worldrenderer.pos(bb.maxX, bb.minY, bb.maxZ).endVertex();
        worldrenderer.pos(bb.maxX, bb.maxY, bb.maxZ).endVertex();
        worldrenderer.pos(bb.minX, bb.maxY, bb.maxZ).endVertex();
        worldrenderer.pos(bb.minX, bb.minY, bb.maxZ).endVertex();
        // West
        worldrenderer.pos(bb.minX, bb.minY, bb.maxZ).endVertex();
        worldrenderer.pos(bb.minX, bb.maxY, bb.maxZ).endVertex();
        worldrenderer.pos(bb.minX, bb.maxY, bb.minZ).endVertex();
        worldrenderer.pos(bb.minX, bb.minY, bb.minZ).endVertex();
        // East
        worldrenderer.pos(bb.maxX, bb.minY, bb.minZ).endVertex();
        worldrenderer.pos(bb.maxX, bb.maxY, bb.minZ).endVertex();
        worldrenderer.pos(bb.maxX, bb.maxY, bb.maxZ).endVertex();
        worldrenderer.pos(bb.maxX, bb.minY, bb.maxZ).endVertex();
        tessellator.draw();
    }
}
