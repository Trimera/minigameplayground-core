package com.minigameplayground.mixin;

import com.minigameplayground.damage.DamageFallbackContext;
import com.minigameplayground.damage.DamageProcessor;
import com.minigameplayground.damage.HealingProcessor;
import com.minigameplayground.zone.PlaygroundZone;
import com.minigameplayground.zone.ZoneManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Scales incoming damage and healing for players inside playground zones.
 *
 * <p>Damage scaling runs after vanilla armor, toughness, resistance and protection
 * enchantments, immediately before absorption. Hurt cooldown has already been applied
 * by {@code hurt()} when {@code actuallyHurt()} reaches this point.</p>
 *
 * <p>If scaling rounds a hit down to zero, the vanilla hurt call has already started,
 * so the hurt animation and vanilla cooldown are preserved. Damage/healing disable
 * flags cancel the call at HEAD instead.</p>
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {
    @Inject(method = "hurt", at = @At("HEAD"), cancellable = true)
    private void minigameplayground$disableDamage(DamageSource source, float amount,
                                          CallbackInfoReturnable<Boolean> cir) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (!(self instanceof ServerPlayer victim)) {
            return;
        }
        PlaygroundZone zone = ZoneManager.getZone(victim);
        if (zone == null || !zone.isDamageDisabled() || DamageProcessor.isUnstoppable(source)) {
            return;
        }
        cir.setReturnValue(false);
    }

    @Inject(method = "getDamageAfterMagicAbsorb", at = @At("RETURN"), cancellable = true)
    private void minigameplayground$applyBalance(DamageSource source, float amount,
                                            CallbackInfoReturnable<Float> cir) {
        if (DamageProcessor.isUnstoppable(source)) {
            return;
        }

        LivingEntity self = (LivingEntity) (Object) this;
        if (!(self instanceof ServerPlayer victim)) {
            return;
        }

        float before = cir.getReturnValue();
        float scaled = DamageProcessor.processDamage(victim, source, before);
        if (scaled != before) {
            cir.setReturnValue(scaled);
        }
        DamageFallbackContext.markMagicApplied(victim, source);
    }

    @Inject(method = "heal", at = @At("HEAD"), cancellable = true)
    private void minigameplayground$disableHealing(float amount, CallbackInfo ci) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (!(self instanceof ServerPlayer player)) {
            return;
        }
        PlaygroundZone zone = ZoneManager.getZone(player);
        if (zone != null && zone.isHealDisabled()) {
            ci.cancel();
        }
    }

    @ModifyVariable(method = "heal", at = @At("HEAD"), argsOnly = true, index = 1)
    private float minigameplayground$applyHealingBalance(float amount) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (self instanceof ServerPlayer player) {
            return HealingProcessor.processHealing(player, amount);
        }
        return amount;
    }
}
