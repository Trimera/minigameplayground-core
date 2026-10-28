package com.minigameplayground.damage;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;

/**
 * Per-attack state used to recover when another mod cancels the vanilla
 * {@code getDamageAfterMagicAbsorb} call or its return callbacks.
 *
 * <p>Some mods call {@code CallbackInfoReturnable.setReturnValue(...)} from a RETURN
 * injection. That method also cancels later callbacks, so the normal damage-balance
 * hook can be silently skipped. The context records whether our hook actually ran
 * during a player's {@code actuallyHurt} call and lets the fallback apply the zone
 * scaling to the final health loss afterwards.</p>
 */
public final class DamageFallbackContext {
    private static final ThreadLocal<DamageFallbackContext> CURRENT = new ThreadLocal<>();

    private final ServerPlayer victim;
    private final DamageSource source;
    private final float healthBefore;
    private boolean magicApplied;

    private DamageFallbackContext(ServerPlayer victim, DamageSource source, float healthBefore) {
        this.victim = victim;
        this.source = source;
        this.healthBefore = healthBefore;
    }

    public static void begin(ServerPlayer victim, DamageSource source, float healthBefore) {
        CURRENT.set(new DamageFallbackContext(victim, source, healthBefore));
    }

    public static DamageFallbackContext current() {
        return CURRENT.get();
    }

    public boolean matches(ServerPlayer victim, DamageSource source) {
        return this.victim == victim && this.source == source;
    }

    public float healthBefore() {
        return this.healthBefore;
    }

    public boolean magicApplied() {
        return this.magicApplied;
    }

    public static void markMagicApplied(ServerPlayer victim, DamageSource source) {
        DamageFallbackContext context = CURRENT.get();
        if (context != null && context.matches(victim, source)) {
            context.magicApplied = true;
        }
    }

    public static void clear(DamageFallbackContext context) {
        if (CURRENT.get() == context) {
            CURRENT.remove();
        }
    }
}
