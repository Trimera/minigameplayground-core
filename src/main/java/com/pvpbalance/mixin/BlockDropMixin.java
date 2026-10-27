package com.pvpbalance.mixin;

import com.pvpbalance.zone.ZoneManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Collections;
import java.util.List;

/**
 * Suppresses block-generated item drops inside PVP zones.
 *
 * <p>This targets {@code Block.dropResources} (the 1.20.1 Mojang-mapped equivalent of
 * Yarn's {@code Block.dropStacks}) and the lower-level {@code popResource} entry points.
 * Player item dropping ({@code Player.drop}) does not go through these methods, so
 * pressing Q or using item-drop skills is unaffected.</p>
 */
@Mixin(Block.class)
public abstract class BlockDropMixin {
    /**
     * Create's drills, saws and other mechanical actors collect drops through
     * {@code Block.getDrops} instead of {@code dropResources}. Returning an empty
     * list here closes that bypass without changing the block-breaking behaviour.
     */
    @Inject(
            method = "getDrops(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/entity/BlockEntity;)Ljava/util/List;",
            at = @At("HEAD"),
            cancellable = true
    )
    private static void pvpbalance$cancelGetDrops(BlockState state, ServerLevel level, BlockPos pos,
                                                  BlockEntity blockEntity,
                                                  CallbackInfoReturnable<List<ItemStack>> cir) {
        if (ZoneManager.isInAnyPvpZone(level, pos)) {
            cir.setReturnValue(Collections.emptyList());
        }
    }

    @Inject(
            method = "getDrops(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/entity/BlockEntity;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/item/ItemStack;)Ljava/util/List;",
            at = @At("HEAD"),
            cancellable = true
    )
    private static void pvpbalance$cancelGetDropsWithTool(BlockState state, ServerLevel level, BlockPos pos,
                                                         BlockEntity blockEntity, Entity entity, ItemStack tool,
                                                         CallbackInfoReturnable<List<ItemStack>> cir) {
        if (ZoneManager.isInAnyPvpZone(level, pos)) {
            cir.setReturnValue(Collections.emptyList());
        }
    }

    @Inject(
            method = "dropResources(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private static void pvpbalance$cancelDropResources(BlockState state, Level level, BlockPos pos,
                                                       CallbackInfo ci) {
        if (ZoneManager.isInAnyPvpZone(level, pos)) {
            ci.cancel();
        }
    }

    @Inject(
            method = "dropResources(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/LevelAccessor;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/entity/BlockEntity;)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private static void pvpbalance$cancelDropResourcesWithBlockEntity(BlockState state, LevelAccessor level,
                                                                     BlockPos pos, BlockEntity blockEntity,
                                                                     CallbackInfo ci) {
        if (level instanceof Level world && ZoneManager.isInAnyPvpZone(world, pos)) {
            ci.cancel();
        }
    }

    @Inject(
            method = "dropResources(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/entity/BlockEntity;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/item/ItemStack;)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private static void pvpbalance$cancelPlayerDestroyDrops(BlockState state, Level level, BlockPos pos,
                                                           BlockEntity blockEntity, Entity entity, ItemStack tool,
                                                           CallbackInfo ci) {
        if (ZoneManager.isInAnyPvpZone(level, pos)) {
            ci.cancel();
        }
    }

    @Inject(
            method = "popResource(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/item/ItemStack;)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private static void pvpbalance$cancelPopResource(Level level, BlockPos pos, ItemStack stack, CallbackInfo ci) {
        if (ZoneManager.isInAnyPvpZone(level, pos)) {
            ci.cancel();
        }
    }

    @Inject(
            method = "popResourceFromFace(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/Direction;Lnet/minecraft/world/item/ItemStack;)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private static void pvpbalance$cancelPopResourceFromFace(Level level, BlockPos pos, Direction direction,
                                                            ItemStack stack, CallbackInfo ci) {
        if (ZoneManager.isInAnyPvpZone(level, pos)) {
            ci.cancel();
        }
    }
}
