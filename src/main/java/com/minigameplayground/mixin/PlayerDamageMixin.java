package com.minigameplayground.mixin;

import com.minigameplayground.damage.DamageFallbackContext;
import com.minigameplayground.damage.DamageProcessor;
import com.minigameplayground.zone.PlaygroundZone;
import com.minigameplayground.zone.ZoneManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Fallback for damage-balance hooks that another mod can cancel.
 *
 * <p>Some mods replace the return value of {@code getDamageAfterMagicAbsorb} from a
 * RETURN injection. In Mixin, {@code setReturnValue} also cancels the remaining return
 * callbacks, so {@link LivingEntityMixin}'s normal scaling hook may never run even though
 * the original damage is still applied. This mixin observes the final health change of
 * {@code Player.actuallyHurt} and applies the zone scaling there only when the normal
 * magic hook did not run.</p>
 */
@Mixin(Player.class)
public abstract class PlayerDamageMixin {
    @Inject(method = "actuallyHurt", at = @At("HEAD"))
    private void minigameplayground$beginDamageFallback(DamageSource source, float amount, CallbackInfo ci) {
        if ((Object) this instanceof ServerPlayer victim) {
            DamageFallbackContext.begin(victim, source, victim.getHealth());
        }
    }

    @Inject(method = "actuallyHurt", at = @At("RETURN"))
    private void minigameplayground$applyDamageFallback(DamageSource source, float amount, CallbackInfo ci) {
        if (!((Object) this instanceof ServerPlayer victim)) {
            return;
        }

        DamageFallbackContext context = DamageFallbackContext.current();
        if (context == null || !context.matches(victim, source)) {
            return;
        }

        try {
            if (context.magicApplied() || DamageProcessor.isUnstoppable(source)) {
                return;
            }

            float healthLost = context.healthBefore() - victim.getHealth();
            if (healthLost <= 0.0F) {
                return;
            }

            PlaygroundZone victimZone = ZoneManager.getZone(victim);
            if (victimZone == null || victimZone.isDamageDisabled()) {
                return;
            }

            float scaledLoss = DamageProcessor.processDamage(victim, source, healthLost);
            if (!Float.isFinite(scaledLoss) || scaledLoss == healthLost) {
                return;
            }

            float newHealth = Math.max(0.0F, context.healthBefore() - scaledLoss);
            victim.setHealth(newHealth);
            if (newHealth <= 0.0F && !victim.isDeadOrDying()) {
                victim.die(source);
            }
        } finally {
            DamageFallbackContext.clear(context);
        }
    }
}
