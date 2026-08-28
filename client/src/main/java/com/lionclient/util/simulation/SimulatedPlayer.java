/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.google.common.base.Predicate
 *  com.google.common.collect.Lists
 *  org.apache.commons.lang3.tuple.Pair
 */
package com.lionclient.util.simulation;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.block.Block;
import net.minecraft.block.BlockFence;
import net.minecraft.block.BlockFenceGate;
import net.minecraft.block.BlockLiquid;
import net.minecraft.block.BlockSlime;
import net.minecraft.block.BlockSoulSand;
import net.minecraft.block.BlockWall;
import net.minecraft.block.BlockWeb;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.EnchantmentProtection;
import net.minecraft.entity.Entity;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.attributes.BaseAttributeMap;
import net.minecraft.entity.ai.attributes.IAttribute;
import net.minecraft.entity.ai.attributes.IAttributeInstance;
import net.minecraft.entity.ai.attributes.ServersideAttributeMap;
import net.minecraft.entity.item.EntityBoat;
import net.minecraft.entity.item.EntityMinecart;
import net.minecraft.entity.player.PlayerCapabilities;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EntitySelectors;
import net.minecraft.util.FoodStats;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovementInput;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraft.world.border.WorldBorder;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.IChunkProvider;


public class SimulatedPlayer
{
    protected static final Minecraft mc = Minecraft.getMinecraft();
    protected static EntityPlayerSP getPlayer() { return mc.thePlayer; }

    private final EntityPlayerSP player;
    public AxisAlignedBB box;
    public final MovementInput movementInput;
    private int jumpTicks;
    public double motionZ;
    public double motionY;
    public double motionX;
    private boolean inWater;
    public boolean ground;
    private boolean isAirBorne;
    public float rotationYaw;
    private double posX;
    private double posY;
    private double posZ;
    private final PlayerCapabilities capabilities;
    private final Entity ridingEntity;
    private float jumpMovementFactor;
    private final World worldObj;
    public boolean isCollidedHorizontally;
    private boolean isCollidedVertically;
    private final WorldBorder worldBorder;
    private final IChunkProvider chunkProvider;
    private boolean isOutsideBorder;
    private final Entity riddenByEntity;
    private BaseAttributeMap attributeMap;
    private final boolean isSpectator;
    public float fallDistance;
    private final float stepHeight;
    private boolean isCollided;
    private int fire;
    private float distanceWalkedModified;
    private float distanceWalkedOnStepModified;
    private int nextStepDistance;
    public final float height;
    private final float width;
    private final int fireResistance;
    private boolean isInWeb;
    private boolean noClip;
    private boolean isSprinting;
    private final FoodStats foodStats;
    private float forward = 0.0f;
    private float moveStrafing = 0.0f;
    private boolean isJumping = false;
    public boolean safeWalk = false;
    private static final float SPEED_IN_AIR = 0.02f;

    public SimulatedPlayer(EntityPlayerSP player, AxisAlignedBB box, MovementInput movementInput, int jumpTicks, double motionZ, double motionY, double motionX, boolean inWater, boolean ground, boolean isAirBorne, float rotationYaw, double posX, double posY, double posZ, PlayerCapabilities capabilities, Entity ridingEntity, float jumpMovementFactor, World worldObj, boolean isCollidedHorizontally, boolean isCollidedVertically, WorldBorder worldBorder, IChunkProvider chunkProvider, boolean isOutsideBorder, Entity riddenByEntity, BaseAttributeMap attributeMap, boolean isSpectator, float fallDistance, float stepHeight, boolean isCollided, int fire, float distanceWalkedModified, float distanceWalkedOnStepModified, int nextStepDistance, float height, float width, int fireResistance, boolean isInWeb, boolean noClip, boolean isSprinting, FoodStats foodStats) {
        this.player = player;
        this.box = box;
        this.movementInput = movementInput;
        this.jumpTicks = jumpTicks;
        this.motionZ = motionZ;
        this.motionY = motionY;
        this.motionX = motionX;
        this.inWater = inWater;
        this.ground = ground;
        this.isAirBorne = isAirBorne;
        this.rotationYaw = rotationYaw;
        this.posX = posX;
        this.posY = posY;
        this.posZ = posZ;
        this.capabilities = capabilities;
        this.ridingEntity = ridingEntity;
        this.jumpMovementFactor = jumpMovementFactor;
        this.worldObj = worldObj;
        this.isCollidedHorizontally = isCollidedHorizontally;
        this.isCollidedVertically = isCollidedVertically;
        this.worldBorder = worldBorder;
        this.chunkProvider = chunkProvider;
        this.isOutsideBorder = isOutsideBorder;
        this.riddenByEntity = riddenByEntity;
        this.attributeMap = attributeMap;
        this.isSpectator = isSpectator;
        this.fallDistance = fallDistance;
        this.stepHeight = stepHeight;
        this.isCollided = isCollided;
        this.fire = fire;
        this.distanceWalkedModified = distanceWalkedModified;
        this.distanceWalkedOnStepModified = distanceWalkedOnStepModified;
        this.nextStepDistance = nextStepDistance;
        this.height = height;
        this.width = width;
        this.fireResistance = fireResistance;
        this.isInWeb = isInWeb;
        this.noClip = noClip;
        this.isSprinting = isSprinting;
        this.foodStats = foodStats;
    }

    public Vec3 getPos() {
        return new Vec3(this.posX, this.posY, this.posZ);
    }

    public static SimulatedPlayer fromClientPlayer(MovementInput input) {
        EntityPlayerSP player = SimulatedPlayer.getPlayer();
        PlayerCapabilities capabilities = SimulatedPlayer.createCapabilitiesCopy(player);
        FoodStats foodStats = SimulatedPlayer.createFoodStatsCopy(player);
        MovementInput movementInput = new MovementInput();
        movementInput.jump = input.jump;
        movementInput.moveForward = input.moveForward;
        movementInput.moveStrafe = input.moveStrafe;
        return new SimulatedPlayer(player, player.getEntityBoundingBox(), movementInput, 0, player.motionZ, player.motionY, player.motionX, player.isInWater(), player.onGround, player.isAirBorne, player.rotationYaw, player.posX, player.posY, player.posZ, capabilities, player.ridingEntity, player.jumpMovementFactor, player.worldObj, player.isCollidedHorizontally, player.isCollidedVertically, player.worldObj.getWorldBorder(), player.worldObj.getChunkProvider(), player.isOutsideBorder(), player.riddenByEntity, player.getAttributeMap(), player.isSpectator(), player.fallDistance, player.stepHeight, player.isCollided, 0, player.distanceWalkedModified, player.distanceWalkedOnStepModified, 0, player.height, player.width, 0, false, player.noClip, player.isSprinting(), foodStats);
    }
    private static FoodStats createFoodStatsCopy(EntityPlayerSP player) {
        NBTTagCompound foodStatsNBT = new NBTTagCompound();
        FoodStats foodStats = new FoodStats();
        player.getFoodStats().writeNBT(foodStatsNBT);
        foodStats.readNBT(foodStatsNBT);
        return foodStats;
    }

    private static PlayerCapabilities createCapabilitiesCopy(EntityPlayerSP player) {
        NBTTagCompound capabilitiesNBT = new NBTTagCompound();
        PlayerCapabilities capabilities = new PlayerCapabilities();
        player.capabilities.writeCapabilitiesToNBT(capabilitiesNBT);
        capabilities.readCapabilitiesFromNBT(capabilitiesNBT);
        return capabilities;
    }

    private boolean onEntityUpdate() {
        this.handleWaterMovement();
        if (this.worldObj.isRemote) {
            this.fire = 0;
        } else if (this.fire > 0) {
            --this.fire;
        }
        if (this.isInLava()) {
            this.setOnFireFromLava();
            this.fallDistance *= 0.5f;
        }
        return !(this.posY < -64.0);
    }

    public void tick() {
        if (!this.onEntityUpdate() || this.player.isRiding()) {
            return;
        }
        this.playerUpdate(false);
        this.clientPlayerLivingUpdate();
        this.playerUpdate(true);
    }

    private void clientPlayerLivingUpdate() {
        this.pushOutOfBlocks(this.posX - (double)this.width * 0.35, this.getEntityBoundingBox().minY + 0.5, this.posZ + (double)this.width * 0.35);
        this.pushOutOfBlocks(this.posX - (double)this.width * 0.35, this.getEntityBoundingBox().minY + 0.5, this.posZ - (double)this.width * 0.35);
        this.pushOutOfBlocks(this.posX + (double)this.width * 0.35, this.getEntityBoundingBox().minY + 0.5, this.posZ - (double)this.width * 0.35);
        this.pushOutOfBlocks(this.posX + (double)this.width * 0.35, this.getEntityBoundingBox().minY + 0.5, this.posZ + (double)this.width * 0.35);
        boolean flag3 = this.foodStats.getFoodLevel() > 6 || this.capabilities.allowFlying;
        float f = 0.8f;
        boolean shouldSprint = this.player.isSprinting();
        if (this.ground && this.movementInput.moveForward >= f && !this.isSprinting() && flag3 && !this.player.isUsingItem() && !this.isPotionActive(Potion.blindness) && shouldSprint) {
            this.setSprinting(true);
        }
        if (!this.isSprinting() && this.movementInput.moveForward >= f && flag3 && !this.player.isUsingItem() && !this.isPotionActive(Potion.blindness) && shouldSprint) {
            this.setSprinting(true);
        }
        if (this.movementInput.sneak) {
            this.setSprinting(false);
        }
        if (this.isSprinting() && (this.movementInput.moveForward < 0.8f || this.isCollidedHorizontally || !flag3)) {
            this.setSprinting(false);
        }
        if (this.capabilities.allowFlying && SimulatedPlayer.mc.playerController.isSpectatorMode() && !this.capabilities.isFlying) {
            this.capabilities.isFlying = true;
        }
        if (this.capabilities.isFlying) {
            if (this.movementInput.sneak) {
                this.motionY -= (double)(this.capabilities.getFlySpeed() * 3.0f);
            }
            if (this.movementInput.jump) {
                this.motionY += (double)(this.capabilities.getFlySpeed() * 3.0f);
            }
        }
        this.livingEntityUpdate();
    }

    private void playerUpdate(boolean post) {
        if (!post) {
            this.noClip = this.isSpectator;
            if (this.isSpectator) {
                this.ground = false;
            }
        } else {
            this.clampPositionFromEntityPlayer();
        }
    }

    private void livingEntityUpdate() {
        if (this.jumpTicks > 0) {
            --this.jumpTicks;
        }
        if (Math.abs(this.motionX) < 0.005) {
            this.motionX = 0.0;
        }
        if (Math.abs(this.motionY) < 0.005) {
            this.motionY = 0.0;
        }
        if (Math.abs(this.motionZ) < 0.005) {
            this.motionZ = 0.0;
        }
        if (this.isMovementBlocked()) {
            this.isJumping = false;
            this.moveStrafing = 0.0f;
            this.forward = 0.0f;
        } else if (this.isServerWorld()) {
            this.updateLivingEntityInput();
        }
        if (this.isJumping) {
            if (this.isInWater() || this.isInLava()) {
                this.updateAITick();
            } else if (this.ground && this.jumpTicks == 0) {
                this.jump();
            }
        } else {
            this.jumpTicks = 0;
        }
        this.moveStrafing *= 0.98f;
        this.forward *= 0.98f;
        this.playerSideMoveEntityWithHeading(this.moveStrafing, this.forward);
        this.jumpMovementFactor = 0.02f;
        if (this.isSprinting()) {
            this.jumpMovementFactor = (float)((double)this.jumpMovementFactor + 0.005999999865889549);
        }
        if (this.ground && this.capabilities.isFlying && !this.isSpectator) {
            this.capabilities.isFlying = false;
        }
    }

    private void clampPositionFromEntityPlayer() {
        double d3 = MathHelper.clamp_double(this.posX, -2.9999999E7, 2.9999999E7);
        double d4 = MathHelper.clamp_double(this.posZ, -2.9999999E7, 2.9999999E7);
        if (d3 != this.posX || d4 != this.posZ) {
            this.setPosition(d3, this.posY, d4);
        }
    }

    private void setPosition(double x, double y, double z) {
        this.posX = x;
        this.posY = y;
        this.posZ = z;
        float f = this.width / 2.0f;
        float f1 = this.height;
        this.setEntityBoundingBox(new AxisAlignedBB(x - (double)f, y, z - (double)f, x + (double)f, y + (double)f1, z + (double)f));
    }

    private void setSprinting(boolean state) {
        this.isSprinting = state;
    }

    private boolean pushOutOfBlocks(double x, double y, double z) {
        boolean inTranslucentBlock;
        if (this.noClip) {
            return false;
        }
        BlockPos blockPos = new BlockPos(x, y, z);
        double d0 = x - (double)blockPos.getX();
        double d1 = z - (double)blockPos.getZ();
        int entHeight = (int)Math.ceil(this.height);
        boolean bl = inTranslucentBlock = !this.isHeadspaceFree(blockPos, entHeight);
        if (inTranslucentBlock) {
            int i = -1;
            double d2 = 9999.0;
            if (this.isHeadspaceFree(blockPos.west(), entHeight) && d0 < d2) {
                d2 = d0;
                i = 0;
            }
            if (this.isHeadspaceFree(blockPos.east(), entHeight) && 1.0 - d0 < d2) {
                d2 = 1.0 - d0;
                i = 1;
            }
            if (this.isHeadspaceFree(blockPos.north(), entHeight) && d1 < d2) {
                d2 = d1;
                i = 4;
            }
            if (this.isHeadspaceFree(blockPos.south(), entHeight) && 1.0 - d1 < d2) {
                i = 5;
            }
            float f = 0.1f;
            if (i == 0) {
                this.motionX = -f;
            }
            if (i == 1) {
                this.motionX = f;
            }
            if (i == 4) {
                this.motionZ = -f;
            }
            if (i == 5) {
                this.motionZ = f;
            }
        }
        return false;
    }

    private boolean isHeadspaceFree(BlockPos pos, int height) {
        int y = 0;
        while (y < height) {
            if (!this.isOpenBlockSpace(pos.add(0, y, 0))) {
                return false;
            }
            ++y;
        }
        return true;
    }

    private boolean isOpenBlockSpace(BlockPos pos) {
        return this.getBlockState(pos).getBlock().isNormalCube();
    }

    private void playerSideMoveEntityWithHeading(float moveStrafing, float forward) {
        if (this.capabilities.isFlying && this.ridingEntity == null) {
            double d3 = this.motionY;
            float f = this.jumpMovementFactor;
            this.jumpMovementFactor = this.capabilities.getFlySpeed() * (float)(this.isSprinting() ? 2 : 1);
            this.livingEntitySideMoveEntityWithHeading(moveStrafing, forward);
            this.motionY = d3 * 0.6;
            this.jumpMovementFactor = f;
        } else {
            this.livingEntitySideMoveEntityWithHeading(moveStrafing, forward);
        }
    }

    private void livingEntitySideMoveEntityWithHeading(float strafing, float forwards) {
        if (this.isServerWorld()) {
            if (!this.isInWater() || this.capabilities.isFlying) {
                if (this.isInLava() && !this.capabilities.isFlying) {
                    double d0 = this.posY;
                    this.moveFlying(strafing, forwards, 0.02f);
                    this.moveEntity(this.motionX, this.motionY, this.motionZ);
                    this.motionX *= 0.5;
                    this.motionY *= 0.5;
                    this.motionZ *= 0.5;
                    this.motionY -= 0.02;
                    if (this.isCollidedHorizontally && this.isOffsetPositionInLiquid(this.motionX, this.motionY + (double)0.6f - this.posY + d0, this.motionZ)) {
                        this.motionY = 0.3f;
                    }
                } else {
                    float f4 = 0.91f;
                    if (this.ground) {
                        f4 = this.worldObj.getBlockState((BlockPos)new BlockPos((int)MathHelper.floor_double((double)this.posX), (int)(MathHelper.floor_double((double)this.getEntityBoundingBox().minY) - 1), (int)MathHelper.floor_double((double)this.posZ))).getBlock().slipperiness * 0.91f;
                    }
                    float f = 0.16277136f / (f4 * f4 * f4);
                    float f5 = this.ground ? this.getAIMoveSpeed() * f : this.jumpMovementFactor;
                    this.moveFlying(strafing, forwards, f5);
                    f4 = 0.91f;
                    if (this.ground) {
                        f4 = this.worldObj.getBlockState((BlockPos)new BlockPos((int)MathHelper.floor_double((double)this.posX), (int)(MathHelper.floor_double((double)this.getEntityBoundingBox().minY) - 1), (int)MathHelper.floor_double((double)this.posZ))).getBlock().slipperiness * 0.91f;
                    }
                    if (this.isOnLadder()) {
                        boolean flag;
                        float f6 = 0.15f;
                        this.motionX = MathHelper.clamp_double(this.motionX, -f6, f6);
                        this.motionZ = MathHelper.clamp_double(this.motionZ, -f6, f6);
                        this.fallDistance = 0.0f;
                        if (this.motionY < -0.15) {
                            this.motionY = -0.15;
                        }
                        if ((flag = this.isSneaking()) && this.motionY < 0.0) {
                            this.motionY = 0.0;
                        }
                    }
                    this.moveEntity(this.motionX, this.motionY, this.motionZ);
                    if (this.isCollidedHorizontally && this.isOnLadder()) {
                        this.motionY = 0.2;
                    }
                    this.motionY = !(!this.worldObj.isRemote || this.worldObj.isBlockLoaded(new BlockPos(this.posX, 0.0, this.posZ)) && this.worldObj.getChunkFromBlockCoords(new BlockPos(this.posX, 0.0, this.posZ)).isLoaded()) ? (this.posY > 0.0 ? -0.1 : 0.0) : (this.motionY -= 0.08);
                    this.motionY *= (double)0.98f;
                    this.motionX *= (double)f4;
                    this.motionZ *= (double)f4;
                }
            } else {
                double d0 = this.posY;
                float f5 = 0.8f;
                float f6 = 0.02f;
                float f3 = EnchantmentHelper.getDepthStriderModifier(this.player);
                if (f3 > 3.0f) {
                    f3 = 3.0f;
                }
                if (!this.ground) {
                    f3 *= 0.5f;
                }
                if (f3 > 0.0f) {
                    f5 += (0.54600006f - f5) * f3 / 3.0f;
                    f6 += (this.getAIMoveSpeed() - f6) * f3 / 3.0f;
                }
                this.moveFlying(strafing, forwards, f6);
                this.moveEntity(this.motionX, this.motionY, this.motionZ);
                this.motionX *= (double)f5;
                this.motionY *= (double)0.8f;
                this.motionZ *= (double)f5;
                this.motionY -= 0.02;
                if (this.isCollidedHorizontally && this.isOffsetPositionInLiquid(this.motionX, this.motionY + (double)0.6f - this.posY + d0, this.motionZ)) {
                    this.motionY = 0.3f;
                }
            }
        }
    }

    public void moveEntity(double xMotion, double yMotion, double zMotion) {
        double velocityX = xMotion;
        double velocityY = yMotion;
        double velocityZ = zMotion;
        if (this.noClip) {
            this.setEntityBoundingBox(this.getEntityBoundingBox().offset(velocityX, velocityY, velocityZ));
            this.resetPositionToBB();
        } else {
            Block block;
            boolean flag;
            double d0 = this.posX;
            double d1 = this.posY;
            double d2 = this.posZ;
            if (this.isInWeb) {
                this.isInWeb = false;
                velocityX *= 0.25;
                velocityY *= (double)0.05f;
                velocityZ *= 0.25;
                this.motionX = 0.0;
                this.motionY = 0.0;
                this.motionZ = 0.0;
            }
            double d3 = velocityX;
            double d4 = velocityY;
            double d5 = velocityZ;
            flag = this.ground && (this.isSneaking() || this.safeWalk);
            if (flag) {
                Double[] result = this.checkForCollision(this, velocityX, velocityZ);
                d3 = result[0];
                d5 = result[1];
            }
            List<AxisAlignedBB> list1 = this.worldObj.getCollidingBoundingBoxes(this.player, this.getEntityBoundingBox().addCoord(velocityX, velocityY, velocityZ));
            AxisAlignedBB axisalignedbb = this.getEntityBoundingBox();
            for (AxisAlignedBB axisalignedbb1 : list1) {
                velocityY = axisalignedbb1.calculateYOffset(this.getEntityBoundingBox(), velocityY);
            }
            this.setEntityBoundingBox(this.getEntityBoundingBox().offset(0.0, velocityY, 0.0));
            boolean flag1 = this.ground || d4 != velocityY && d4 < 0.0;
            for (AxisAlignedBB axisalignedbb2 : list1) {
                velocityX = axisalignedbb2.calculateXOffset(this.getEntityBoundingBox(), velocityX);
            }
            this.setEntityBoundingBox(this.getEntityBoundingBox().offset(velocityX, 0.0, 0.0));
            for (AxisAlignedBB axisalignedbb13 : list1) {
                velocityZ = axisalignedbb13.calculateZOffset(this.getEntityBoundingBox(), velocityZ);
            }
            this.setEntityBoundingBox(this.getEntityBoundingBox().offset(0.0, 0.0, velocityZ));
            if (this.stepHeight > 0.0f && flag1 && (d3 != velocityX || d5 != velocityZ)) {
                double d11 = velocityX;
                double d7 = velocityY;
                double d8 = velocityZ;
                AxisAlignedBB axisalignedbb3 = this.getEntityBoundingBox();
                this.setEntityBoundingBox(axisalignedbb);
                velocityY = this.stepHeight;
                List<AxisAlignedBB> list = this.worldObj.getCollidingBoundingBoxes(this.player, this.getEntityBoundingBox().addCoord(d3, velocityY, d5));
                AxisAlignedBB axisalignedbb4 = this.getEntityBoundingBox();
                AxisAlignedBB axisalignedbb5 = axisalignedbb4.addCoord(d3, 0.0, d5);
                double d9 = velocityY;
                for (AxisAlignedBB axisalignedbb6 : list) {
                    d9 = axisalignedbb6.calculateYOffset(axisalignedbb5, d9);
                }
                axisalignedbb4 = axisalignedbb4.offset(0.0, d9, 0.0);
                double d15 = d3;
                for (AxisAlignedBB axisalignedbb7 : list) {
                    d15 = axisalignedbb7.calculateXOffset(axisalignedbb4, d15);
                }
                axisalignedbb4 = axisalignedbb4.offset(d15, 0.0, 0.0);
                double d16 = d5;
                for (AxisAlignedBB axisalignedbb8 : list) {
                    d16 = axisalignedbb8.calculateZOffset(axisalignedbb4, d16);
                }
                axisalignedbb4 = axisalignedbb4.offset(0.0, 0.0, d16);
                AxisAlignedBB axisalignedbb14 = this.getEntityBoundingBox();
                double d17 = velocityY;
                for (AxisAlignedBB axisalignedbb9 : list) {
                    d17 = axisalignedbb9.calculateYOffset(axisalignedbb14, d17);
                }
                axisalignedbb14 = axisalignedbb14.offset(0.0, d17, 0.0);
                double d18 = d3;
                for (AxisAlignedBB axisalignedbb10 : list) {
                    d18 = axisalignedbb10.calculateXOffset(axisalignedbb14, d18);
                }
                axisalignedbb14 = axisalignedbb14.offset(d18, 0.0, 0.0);
                double d19 = d5;
                for (AxisAlignedBB axisalignedbb11 : list) {
                    d19 = axisalignedbb11.calculateZOffset(axisalignedbb14, d19);
                }
                axisalignedbb14 = axisalignedbb14.offset(0.0, 0.0, d19);
                double d20 = d15 * d15 + d16 * d16;
                double d10 = d18 * d18 + d19 * d19;
                if (d20 > d10) {
                    velocityX = d15;
                    velocityZ = d16;
                    velocityY = -d9;
                    this.setEntityBoundingBox(axisalignedbb4);
                } else {
                    velocityX = d18;
                    velocityZ = d19;
                    velocityY = -d17;
                    this.setEntityBoundingBox(axisalignedbb14);
                }
                for (AxisAlignedBB axisalignedbb12 : list) {
                    velocityY = axisalignedbb12.calculateYOffset(this.getEntityBoundingBox(), velocityY);
                }
                this.setEntityBoundingBox(this.getEntityBoundingBox().offset(0.0, velocityY, 0.0));
                if (d11 * d11 + d8 * d8 >= velocityX * velocityX + velocityZ * velocityZ) {
                    velocityX = d11;
                    velocityY = d7;
                    velocityZ = d8;
                    this.setEntityBoundingBox(axisalignedbb3);
                }
            }
            this.resetPositionToBB();
            this.isCollidedHorizontally = d3 != velocityX || d5 != velocityZ;
            this.isCollidedVertically = d4 != velocityY;
            this.ground = this.isCollidedVertically && d4 < 0.0;
            this.isCollided = this.isCollidedHorizontally || this.isCollidedVertically;
            int i = MathHelper.floor_double(this.posX);
            int j = MathHelper.floor_double(this.posY - (double)0.2f);
            int k = MathHelper.floor_double(this.posZ);
            BlockPos blockPos = new BlockPos(i, j, k);
            Block block1 = this.worldObj.getBlockState(blockPos).getBlock();
            if (block1.getMaterial() == Material.air && ((block = this.worldObj.getBlockState(blockPos.down()).getBlock()) instanceof BlockFence || block instanceof BlockWall || block instanceof BlockFenceGate)) {
                block1 = block;
            }
            this.updateFallState(velocityY, this.ground);
            if (d3 != velocityX) {
                this.motionX = 0.0;
            }
            if (d5 != velocityZ) {
                this.motionZ = 0.0;
            }
            if (d4 != velocityY) {
                this.onLanded(block1);
            }
            if (this.canTriggerWalking() && !flag && this.ridingEntity == null) {
                double d12 = this.posX - d0;
                double d13 = this.posY - d1;
                double d14 = this.posZ - d2;
                if (block1 != Blocks.ladder) {
                    d13 = 0.0;
                }
                if (block1 != null && this.ground) {
                    this.onEntityCollidedWithBlock(block1);
                }
                this.distanceWalkedModified = (float)((double)this.distanceWalkedModified + (double)MathHelper.sqrt_double(d12 * d12 + d14 * d14) * 0.6);
                this.distanceWalkedOnStepModified = (float)((double)this.distanceWalkedOnStepModified + (double)MathHelper.sqrt_double(d12 * d12 + d13 * d13 + d14 * d14) * 0.6);
                if (this.distanceWalkedOnStepModified > (float)this.nextStepDistance && block1.getMaterial() != Material.air) {
                    this.nextStepDistance = (int)this.distanceWalkedOnStepModified + 1;
                }
            }
            try {
                this.doBlockCollisions();
            }
            catch (Throwable var52) {
                var52.printStackTrace();
            }
            boolean flag2 = this.isWet();
            if (this.worldObj.isFlammableWithin(this.getEntityBoundingBox().contract(0.001, 0.001, 0.001))) {
                if (!flag2) {
                    ++this.fire;
                    if (this.fire == 0) {
                        this.setFire(8);
                    }
                }
            } else if (this.fire <= 0) {
                this.fire = -this.fireResistance;
            }
            if (flag2 && this.fire > 0) {
                this.fire = -this.fireResistance;
            }
        }
    }

    public AxisAlignedBB getEntityBoundingBox() {
        return this.box;
    }

    public void setEntityBoundingBox(AxisAlignedBB box) {
        this.box = box;
    }

    public void setOnFireFromLava() {
        this.setFire(15);
    }

    public void setFire(int seconds) {
        int i = seconds * 20;
        if (this.fire < (i = EnchantmentProtection.getFireTimeForEntity(this.player, i))) {
            this.fire = i;
        }
    }

    public boolean isWet() {
        return this.inWater || this.isRainingAt(new BlockPos(this.posX, this.posY, this.posZ)) || this.isRainingAt(new BlockPos(this.posX, this.posY + (double)this.height, this.posZ));
    }

    public void doBlockCollisions() {
        BlockPos blockpos = new BlockPos(this.getEntityBoundingBox().minX + 0.001, this.getEntityBoundingBox().minY + 0.001, this.getEntityBoundingBox().minZ + 0.001);
        BlockPos blockpos1 = new BlockPos(this.getEntityBoundingBox().maxX - 0.001, this.getEntityBoundingBox().maxY - 0.001, this.getEntityBoundingBox().maxZ - 0.001);
        if (this.isAreaLoaded(blockpos.getX(), blockpos.getY(), blockpos.getZ(), blockpos1.getX(), blockpos1.getY(), blockpos1.getZ(), true)) {
            int i = blockpos.getX();
            while (i <= blockpos1.getX()) {
                int j = blockpos.getY();
                while (j <= blockpos1.getY()) {
                    int k = blockpos.getZ();
                    while (k <= blockpos1.getZ()) {
                        BlockPos pos = new BlockPos(i, j, k);
                        IBlockState state = this.worldObj.getBlockState(pos);
                        try {
                            Block block = state.getBlock();
                            if (block instanceof BlockWeb) {
                                this.isInWeb = true;
                            } else if (block instanceof BlockSoulSand) {
                                this.motionX *= 0.4;
                                this.motionZ *= 0.4;
                            }
                        }
                        catch (Throwable var11) {
                            var11.printStackTrace();
                        }
                        ++k;
                    }
                    ++j;
                }
                ++i;
            }
        }
    }

    public void updateFallState(double motionY, boolean ground) {
        if (!this.isInWater()) {
            this.handleWaterMovement();
        }
        if (ground) {
            if (this.fallDistance > 0.0f) {
                this.fallDistance = 0.0f;
            }
        } else if (motionY < 0.0) {
            this.fallDistance = (float)((double)this.fallDistance - motionY);
        }
    }

    public boolean handleWaterMovement() {
        if (this.handleMaterialAcceleration(this.getEntityBoundingBox().expand(0.0, -0.4f, 0.0).contract(0.001, 0.001, 0.001), Material.water)) {
            this.fallDistance = 0.0f;
            this.inWater = true;
            this.fire = 0;
        } else {
            this.inWater = false;
        }
        return this.inWater;
    }

    public boolean handleMaterialAcceleration(AxisAlignedBB boundingBox, Material material) {
        int j1;
        int i = MathHelper.floor_double(boundingBox.minX);
        int j = MathHelper.floor_double(boundingBox.maxX + 1.0);
        int k = MathHelper.floor_double(boundingBox.minY);
        int l = MathHelper.floor_double(boundingBox.maxY + 1.0);
        int i1 = MathHelper.floor_double(boundingBox.minZ);
        if (!this.isAreaLoaded(i, k, i1, j, l, j1 = MathHelper.floor_double(boundingBox.maxZ + 1.0), true)) {
            return false;
        }
        boolean flag = false;
        Vec3 vec3 = new Vec3(0.0, 0.0, 0.0);
        BlockPos.MutableBlockPos blockPos = new BlockPos.MutableBlockPos();
        int k1 = i;
        while (k1 < j) {
            int l1 = k;
            while (l1 < l) {
                int i2 = i1;
                while (i2 < j1) {
                    double d0;
                    Block block;
                    blockPos.set(k1, l1, i2);
                    IBlockState state = this.getBlockState(blockPos);
                    if (state != null && (block = state.getBlock()) != null && block.getMaterial() == material && (double)l >= (d0 = (double)((float)(l1 + 1) - BlockLiquid.getLiquidHeightPercent(state.getValue(BlockLiquid.LEVEL))))) {
                        flag = true;
                        vec3 = block.modifyAcceleration(this.worldObj, blockPos, this.player, vec3);
                    }
                    ++i2;
                }
                ++l1;
            }
            ++k1;
        }
        if (vec3.lengthVector() > 0.0 && this.isPushedByWater()) {
            vec3 = vec3.normalize();
            double d1 = 0.014;
            this.motionX += vec3.xCoord * d1;
            this.motionY += vec3.yCoord * d1;
            this.motionZ += vec3.zCoord * d1;
        }
        return flag;
    }

    public boolean isAreaLoaded(int minX, int minY, int minZ, int maxX, int maxY, int maxZ, boolean idfk) {
        if (maxY >= 0 && minY < 256) {
            minZ >>= 4;
            maxX >>= 4;
            maxZ >>= 4;
            int i = minX >>= 4;
            while (i <= maxX) {
                int j = minZ;
                while (j <= maxZ) {
                    if (!this.isChunkLoaded(i, j, idfk)) {
                        return false;
                    }
                    ++j;
                }
                ++i;
            }
            return true;
        }
        return false;
    }

    public void onEntityCollidedWithBlock(Block block) {
        if (block instanceof BlockSlime && Math.abs(this.motionY) < 0.1 && !this.isSneaking()) {
            double motion = 0.4 + Math.abs(this.motionY) * 0.2;
            this.motionX *= motion;
            this.motionZ *= motion;
        }
    }

    public boolean canTriggerWalking() {
        return !this.capabilities.isFlying;
    }

    public boolean isOnLadder() {
        int k;
        int j;
        int i = MathHelper.floor_double(this.posX);
        Block block = this.worldObj.getBlockState(new BlockPos(i, j = MathHelper.floor_double(this.getEntityBoundingBox().minY), k = MathHelper.floor_double(this.posZ))).getBlock();
        return (block == Blocks.ladder || block == Blocks.vine) && !this.isSpectator;
    }

    public void moveFlying(float strafe, float forward, float friction) {
        float newStrafe = strafe;
        float newForward = forward;
        float f = newStrafe * newStrafe + newForward * newForward;
        if (f >= 1.0E-4f) {
            if ((f = MathHelper.sqrt_float(f)) < 1.0f) {
                f = 1.0f;
            }
            f = friction / f;
            float f1 = MathHelper.sin(this.rotationYaw * (float)Math.PI / 180.0f);
            float f2 = MathHelper.cos(this.rotationYaw * (float)Math.PI / 180.0f);
            this.motionX += (double)((newStrafe *= f) * f2 - (newForward *= f) * f1);
            this.motionZ += (double)(newForward * f2 + newStrafe * f1);
        }
    }

    public void jump() {
        this.motionY = this.getJumpUpwardsMotion();
        if (this.isPotionActive(Potion.jump)) {
            this.motionY += (double)((float)(this.getActivePotionEffect(Potion.jump).getAmplifier() + 1) * 0.1f);
        }
        if (this.isSprinting()) {
            float f = this.rotationYaw * ((float)Math.PI / 180);
            this.motionX -= (double)(MathHelper.sin(f) * 0.2f);
            this.motionZ += (double)(MathHelper.cos(f) * 0.2f);
        }
        this.isAirBorne = true;
    }

    public boolean isSprinting() {
        return this.isSprinting;
    }

    public boolean isPotionActive(Potion potion) {
        return this.player.getActivePotionEffect(potion) != null;
    }

    public PotionEffect getActivePotionEffect(Potion potion) {
        return this.player.getActivePotionEffect(potion);
    }

    public float getJumpUpwardsMotion() {
        return 0.42f;
    }

    public boolean isInWater() {
        return this.inWater;
    }

    public void updateLivingEntityInput() {
        this.forward = this.movementInput.moveForward;
        this.moveStrafing = this.movementInput.moveStrafe;
        this.isJumping = this.movementInput.jump;
    }

    public boolean isServerWorld() {
        return true;
    }

    public boolean isMovementBlocked() {
        return this.player.getHealth() <= 0.0f || this.player.isPlayerSleeping();
    }

    public boolean isInLava() {
        return this.worldObj.isMaterialInBB(this.getEntityBoundingBox().expand(-0.1f, -0.4f, -0.1f), Material.lava);
    }

    public void updateAITick() {
        this.motionY += (double)0.04f;
    }

    public boolean isOffsetPositionInLiquid(double x, double y, double z) {
        AxisAlignedBB box = this.getEntityBoundingBox().offset(x, y, z);
        return this.isLiquidPresentInAABB(box);
    }

    public boolean isLiquidPresentInAABB(AxisAlignedBB box) {
        return this.worldObj.getCollidingBoundingBoxes(this.player, box).isEmpty() && !this.worldObj.isAnyLiquid(box);
    }

    public List<AxisAlignedBB> getCollidingBoundingBoxes(AxisAlignedBB box) {
        ArrayList<AxisAlignedBB> list = new ArrayList<>();
        int i = MathHelper.floor_double(box.minX);
        int j = MathHelper.floor_double(box.maxX + 1.0);
        int k = MathHelper.floor_double(box.minY);
        int l = MathHelper.floor_double(box.maxY + 1.0);
        int i1 = MathHelper.floor_double(box.minZ);
        int j1 = MathHelper.floor_double(box.maxZ + 1.0);
        WorldBorder worldBorder = this.getWorldBorder();
        boolean flag = this.isOutsideBorder;
        boolean flag1 = this.isInsideBorder(worldBorder, flag);
        IBlockState iblockstate = Blocks.stone.getDefaultState();
        BlockPos.MutableBlockPos blockPos = new BlockPos.MutableBlockPos();
        int k1 = i;
        while (k1 < j) {
            int l1 = i1;
            while (l1 < j1) {
                if (this.isBlockLoaded(blockPos.set(k1, 64, l1))) {
                    int i2 = k - 1;
                    while (i2 < l) {
                        blockPos.set(k1, i2, l1);
                        if (flag && flag1) {
                            this.isOutsideBorder = false;
                        } else if (!flag && !flag1) {
                            this.isOutsideBorder = true;
                        }
                        IBlockState state = iblockstate;
                        if (worldBorder.contains(blockPos) || !flag1) {
                            state = this.getBlockState(blockPos);
                        }
                        state.getBlock().addCollisionBoxesToList(this.worldObj, blockPos, state, box, list, this.player);
                        ++i2;
                    }
                }
                ++l1;
            }
            ++k1;
        }
        double d0 = 0.25;
        List<Entity> entities = this.getEntitiesWithinAABBExcludingEntity(this.player, box.expand(d0, d0, d0));
        for (Entity entity : entities) {
            if (this.riddenByEntity == entity || this.ridingEntity == entity) continue;
            AxisAlignedBB boundingBox = entity.getCollisionBoundingBox();
            if (boundingBox != null && boundingBox.intersectsWith(box)) {
                list.add(boundingBox);
            }
            if ((boundingBox = this.getCollisionBox(this.player, entity)) == null || !boundingBox.intersectsWith(box)) continue;
            list.add(boundingBox);
        }
        return list;
    }

    public IBlockState getBlockState(BlockPos blockPos) {
        return this.worldObj.getBlockState(blockPos);
    }

    private Chunk getChunkFromBlockCoords(BlockPos blockPos) {
        return this.getChunkFromChunkCoords(blockPos.getX() >> 4, blockPos.getZ() >> 4);
    }

    private Chunk getChunkFromChunkCoords(int x, int z) {
        return this.chunkProvider.provideChunk(x, z);
    }

    private boolean isValid(BlockPos pos) {
        return pos.getX() >= -30000000 && pos.getZ() >= -30000000 && pos.getX() < 30000000 && pos.getZ() < 30000000 && pos.getY() >= 0 && pos.getY() < 256;
    }

    private WorldBorder getWorldBorder() {
        return this.worldBorder;
    }

    private boolean isInsideBorder(WorldBorder border, boolean insideBorder) {
        double d0 = border.minX();
        double d1 = border.minZ();
        double d2 = border.maxX();
        double d3 = border.maxZ();
        if (insideBorder) {
            d0 += 1.0;
            d1 += 1.0;
            d2 -= 1.0;
            d3 -= 1.0;
        } else {
            d0 -= 1.0;
            d1 -= 1.0;
            d2 += 1.0;
            d3 += 1.0;
        }
        return this.posX > d0 && this.posX < d2 && this.posZ > d1 && this.posZ < d3;
    }

    private boolean isBlockLoaded(BlockPos pos) {
        return this.isBlockLoaded(pos, true);
    }

    private boolean isBlockLoaded(BlockPos pos, boolean check2) {
        return this.isValid(pos) && this.isChunkLoaded(pos.getX() >> 4, pos.getZ() >> 4, check2);
    }

    private boolean isChunkLoaded(int x, int z, boolean flag) {
        return this.chunkProvider.chunkExists(x, z) && (flag || !this.chunkProvider.provideChunk(x, z).isEmpty());
    }

    public List<Entity> getEntitiesWithinAABBExcludingEntity(Entity entity, AxisAlignedBB box) {
        return this.worldObj.getEntitiesWithinAABBExcludingEntity(entity, box);
    }

    private AxisAlignedBB getCollisionBox(Entity player, Entity entity) {
        if (entity instanceof EntityBoat) {
            return entity.getEntityBoundingBox();
        }
        if (entity instanceof EntityMinecart) {
            return player.getCollisionBox(entity);
        }
        return null;
    }

    private float getAIMoveSpeed() {
        return (float)this.getEntityAttribute(SharedMonsterAttributes.movementSpeed).getAttributeValue();
    }

    private IAttributeInstance getEntityAttribute(IAttribute iAttribute) {
        return this.getAttributeMap().getAttributeInstance(iAttribute);
    }

    private BaseAttributeMap getAttributeMap() {
        if (this.attributeMap == null) {
            this.attributeMap = new ServersideAttributeMap();
        }
        return this.attributeMap;
    }

    private void resetPositionToBB() {
        this.posX = (this.getEntityBoundingBox().minX + this.getEntityBoundingBox().maxX) / 2.0;
        this.posY = this.getEntityBoundingBox().minY;
        this.posZ = (this.getEntityBoundingBox().minZ + this.getEntityBoundingBox().maxZ) / 2.0;
    }

    private void onLanded(Block block) {
        if (block instanceof BlockSlime) {
            if (this.isSneaking()) {
                this.motionY = 0.0;
            } else if (this.motionY < 0.0) {
                this.motionY = -this.motionY;
            }
        } else {
            this.motionY = 0.0;
        }
    }

    public boolean isSneaking() {
        return this.movementInput.sneak && !this.player.isPlayerSleeping();
    }

    private boolean isRainingAt(BlockPos pos) {
        if ((double)this.worldObj.getRainStrength(1.0f) <= 0.2) {
            return false;
        }
        if (!this.canSeeSky(pos)) {
            return false;
        }
        if (this.worldObj.getPrecipitationHeight(pos).getY() > pos.getY()) {
            return false;
        }
        BiomeGenBase base = this.worldObj.getBiomeGenForCoords(pos);
        if (base.getEnableSnow()) {
            return false;
        }
        if (this.worldObj.canSnowAt(pos, false)) {
            return false;
        }
        return base.canRain();
    }

    private boolean canSeeSky(BlockPos pos) {
        return this.getChunkFromBlockCoords(pos).canSeeSky(pos);
    }

    private boolean isPushedByWater() {
        return !this.capabilities.isFlying;
    }

    public Double[] checkForCollision(SimulatedPlayer simPlayer, double velocityX, double velocityZ) {
        EntityPlayerSP player = SimulatedPlayer.getPlayer();
        World worldObj = player.worldObj;
        double d3 = velocityX;
        double d5 = velocityZ;
        double d6 = 0.05;
        while (velocityX != 0.0 && worldObj.getCollidingBoundingBoxes(player, simPlayer.box.offset(velocityX, -1.0, 0.0)).isEmpty()) {
            velocityX = velocityX < d6 && velocityX >= -d6 ? 0.0 : (velocityX > 0.0 ? (velocityX -= d6) : (velocityX += d6));
            d3 = velocityX;
        }
        while (velocityZ != 0.0 && worldObj.getCollidingBoundingBoxes(player, simPlayer.box.offset(0.0, -1.0, velocityZ)).isEmpty()) {
            velocityZ = velocityZ < d6 && velocityZ >= -d6 ? 0.0 : (velocityZ > 0.0 ? (velocityZ -= d6) : (velocityZ += d6));
            d5 = velocityZ;
        }
        while (velocityX != 0.0 && velocityZ != 0.0 && worldObj.getCollidingBoundingBoxes(player, simPlayer.box.offset(velocityX, -1.0, velocityZ)).isEmpty()) {
            velocityX = velocityX < d6 && velocityX >= -d6 ? 0.0 : (velocityX > 0.0 ? (velocityX -= d6) : (velocityX += d6));
            d3 = velocityX;
            velocityZ = velocityZ < d6 && velocityZ >= -d6 ? 0.0 : (velocityZ > 0.0 ? (velocityZ -= d6) : (velocityZ += d6));
            return new Double[]{d3, d5};
        }
        return new Double[]{d3, d5};
    }

    public float getEyeHeight() {
        return this.height * 0.85f;
    }
}
