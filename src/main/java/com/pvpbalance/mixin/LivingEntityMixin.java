package com.pvpbalance.mixin;

import com.pvpbalance.damage.PVPDamageProcessor;
import com.pvpbalance.damage.PVPHealingProcessor;
import com.pvpbalance.zone.PvpZone;
import com.pvpbalance.zone.ZoneManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Scales incoming damage and healing for players inside PVP zones.
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
    private void pvpbalance$disableDamage(DamageSource source, float amount,
                                          CallbackInfoReturnable<Boolean> cir) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (!(self instanceof ServerPlayer victim)) {
            return;
        }
        PvpZone zone = ZoneManager.getZone(victim);
        if (zone == null || !zone.isDamageDisabled() || PVPDamageProcessor.isUnstoppable(source)) {
            return;
        }
        cir.setReturnValue(false);
    }

    @Inject(method = "getDamageAfterMagicAbsorb", at = @At("RETURN"), cancellable = true)
    private void pvpbalance$applyPvpBalance(DamageSource source, float amount,
                                            CallbackInfoReturnable<Float> cir) {
        if (PVPDamageProcessor.isUnstoppable(source)) {
            return;
        }

        LivingEntity self = (LivingEntity) (Object) this;
        if (!(self instanceof ServerPlayer victim)) {
            return;
        }

        Entity attackerEntity = source.getEntity();
        if (!(attackerEntity instanceof ServerPlayer attacker)) {
            return;
        }

        cir.setReturnValue(PVPDamageProcessor.processDamage(victim, attacker, cir.getReturnValue()));
    }

    @Inject(method = "heal", at = @At("HEAD"), cancellable = true)
    private void pvpbalance$disableHealing(float amount, CallbackInfo ci) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (!(self instanceof ServerPlayer player)) {
            return;
        }
        PvpZone zone = ZoneManager.getZone(player);
        if (zone != null && zone.isHealDisabled()) {
            ci.cancel();
        }
    }

    @ModifyVariable(method = "heal", at = @At("HEAD"), argsOnly = true, index = 1)
    private float pvpbalance$applyPvpHealingBalance(float amount) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (self instanceof ServerPlayer player) {
            return PVPHealingProcessor.processHealing(player, amount);
        }
        return amount;
    }
}
