package com.minigameplayground.mixin;

import com.minigameplayground.MiniGamePlaygroundConfig;
import com.minigameplayground.zone.ZoneManager;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Suppresses item drops created by mobs inside playground zones.
 *
 * <p>Loot tables, equipment and mob-specific drops all eventually call
 * {@code Entity.spawnAtLocation(ItemStack, float)}. Blocking that single entry point
 * avoids spawning large numbers of item entities during mob-kill games. Experience
 * orbs and player item drops are unaffected; block drops are handled separately by
 * {@link BlockDropMixin}.</p>
 */
@Mixin(Entity.class)
public abstract class MobDropMixin {
    @Inject(
            method = "spawnAtLocation(Lnet/minecraft/world/item/ItemStack;F)Lnet/minecraft/world/entity/item/ItemEntity;",
            at = @At("HEAD"),
            cancellable = true
    )
    private void minigameplayground$suppressMobDrops(ItemStack stack, float offsetY,
                                                      CallbackInfoReturnable<ItemEntity> cir) {
        if (MiniGamePlaygroundConfig.isMobDropsAllowed()) {
            return;
        }
        Entity self = (Entity) (Object) this;
        if (!(self instanceof Mob)) {
            return;
        }
        Level level = self.level();
        if (level != null && !level.isClientSide()
                && ZoneManager.isInAnyPlaygroundZone(level, self.blockPosition())) {
            cir.setReturnValue(null);
        }
    }
}
