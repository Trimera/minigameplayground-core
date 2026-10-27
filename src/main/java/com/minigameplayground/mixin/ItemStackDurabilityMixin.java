package com.minigameplayground.mixin;

import com.minigameplayground.zone.PlaygroundZone;
import com.minigameplayground.zone.ZoneManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.function.Consumer;

/**
 * Prevents durability loss for players standing inside a playground zone whose zone setting
 * requests it (the default for newly created zones).
 *
 * <p>This covers weapon attacks, armor damage from hits, shields, bows and tools
 * because all of them eventually call {@code ItemStack.hurtAndBreak} or
 * {@code ItemStack.hurt}. The direct hurt entry point is also guarded for mods that
 * use it themselves.</p>
 */
@Mixin(ItemStack.class)
public abstract class ItemStackDurabilityMixin {
    @Inject(
            method = "hurtAndBreak(ILnet/minecraft/world/entity/LivingEntity;Ljava/util/function/Consumer;)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void minigameplayground$keepItemDurability(int amount, LivingEntity entity, Consumer<?> onBreak,
                                               CallbackInfo ci) {
        if (entity instanceof ServerPlayer player && shouldKeepDurability(player)) {
            ci.cancel();
        }
    }

    @Inject(
            method = "hurt(ILnet/minecraft/util/RandomSource;Lnet/minecraft/server/level/ServerPlayer;)Z",
            at = @At("HEAD"),
            cancellable = true
    )
    private void minigameplayground$keepItemDurabilityDirect(int amount, RandomSource random, ServerPlayer player,
                                                     CallbackInfoReturnable<Boolean> cir) {
        if (shouldKeepDurability(player)) {
            cir.setReturnValue(false);
        }
    }

    private static boolean shouldKeepDurability(ServerPlayer player) {
        if (player == null) {
            return false;
        }
        PlaygroundZone zone = ZoneManager.getZone(player);
        return zone != null && zone.isKeepDurability();
    }
}
