package com.minigameplayground.mixin;

import com.minigameplayground.zone.PlaygroundZone;
import com.minigameplayground.zone.ZoneManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Applies the per-zone {@code keepInventory} option.
 *
 * <p>A zone can force inventory and experience retention even when the world's
 * {@code keepInventory} game rule is off. When the option is off, vanilla's game rule
 * still decides, so existing worlds are not changed merely by installing the mod.</p>
 */
@Mixin(Player.class)
public abstract class PlayerDeathLootMixin {
    @Inject(method = "dropEquipment", at = @At("HEAD"), cancellable = true)
    private void minigameplayground$keepInventory(CallbackInfo ci) {
        if ((Object) this instanceof ServerPlayer player && keepsInventory(player)) {
            ci.cancel();
        }
    }

    @Inject(method = "getExperienceReward", at = @At("HEAD"), cancellable = true)
    private void minigameplayground$keepExperience(CallbackInfoReturnable<Integer> cir) {
        if ((Object) this instanceof ServerPlayer player && keepsInventory(player)) {
            cir.setReturnValue(0);
        }
    }

    private static boolean keepsInventory(ServerPlayer player) {
        for (PlaygroundZone zone : ZoneManager.getZonesAt(player)) {
            if (zone.isKeepInventory()) {
                return true;
            }
        }
        return false;
    }
}
