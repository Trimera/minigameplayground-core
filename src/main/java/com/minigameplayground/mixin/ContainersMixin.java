package com.minigameplayground.mixin;

import com.minigameplayground.zone.ZoneManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.world.Container;
import net.minecraft.world.Containers;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Prevents container contents (chests, barrels, etc.) from spilling out when their
 * block is removed inside a playground zone.
 */
@Mixin(Containers.class)
public abstract class ContainersMixin {
    @Inject(
            method = "dropContents(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/Container;)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private static void minigameplayground$cancelContainerDrops(Level level, BlockPos pos, Container container,
                                                       CallbackInfo ci) {
        if (ZoneManager.isInAnyPlaygroundZone(level, pos)) {
            ci.cancel();
        }
    }

    @Inject(
            method = "dropContents(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/NonNullList;)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private static void minigameplayground$cancelItemListDrops(Level level, BlockPos pos, NonNullList<?> items,
                                                      CallbackInfo ci) {
        if (ZoneManager.isInAnyPlaygroundZone(level, pos)) {
            ci.cancel();
        }
    }
}
