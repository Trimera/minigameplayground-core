package com.minigameplayground.damage;

import com.minigameplayground.zone.PlaygroundZone;
import com.minigameplayground.zone.ZoneManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;

/**
 * Central damage calculation for playground zones.
 *
 * <p>Only the victim's final {@code getMaxHealth()} value and the zone's configured
 * damage baseline are used. Attribute modifiers from level/health mods are therefore
 * respected without trying to reconstruct a vanilla base value.</p>
 *
 * <p>The final value is calculated with {@code float} precision and rounded to two
 * decimal places. A raw hit that reaches the zone baseline is capped at the victim's
 * max health; anything below the baseline is scaled proportionally.</p>
 *
 * <p>This calculation runs after vanilla hurt cooldown, armor, toughness, resistance
 * and protection enchantments, immediately before absorption. The baseline is therefore
 * the mitigated damage the player would actually take before absorption, not the raw
 * value at the start of {@code hurt()}. Absorption still applies afterwards.</p>
 */
public final class DamageProcessor {
    public static final float DEFAULT_STANDARD_HEALTH = PlaygroundZone.DEFAULT_DAMAGE_BASELINE;

    private DamageProcessor() {
    }

    public static float processDamage(ServerPlayer victim, ServerPlayer attacker, float mitigatedDamage) {
        if (victim == null || attacker == null || victim == attacker) {
            return mitigatedDamage;
        }
        PlaygroundZone zone = ZoneManager.getCommonZone(victim, attacker);
        return processDamage(victim, zone, mitigatedDamage);
    }

    /**
     * Resolves the zone mode from the victim and, for {@code pvp} zones, also from the
     * attacking player. In {@code all} mode the victim's zone is sufficient, so
     * non-player, environmental, self-inflicted and ownerless damage is balanced too.
     */
    public static float processDamage(ServerPlayer victim, DamageSource source, float mitigatedDamage) {
        if (victim == null || source == null || isUnstoppable(source)) {
            return mitigatedDamage;
        }

        PlaygroundZone victimZone = ZoneManager.getZone(victim);
        if (victimZone == null) {
            return mitigatedDamage;
        }
        if (victimZone.isAllDamageMode()) {
            return processDamage(victim, victimZone, mitigatedDamage);
        }

        Entity attackerEntity = source.getEntity();
        if (!(attackerEntity instanceof ServerPlayer attacker) || attacker == victim) {
            return mitigatedDamage;
        }
        PlaygroundZone common = ZoneManager.getCommonZone(victim, attacker);
        return processDamage(victim, common, mitigatedDamage);
    }

    private static float processDamage(ServerPlayer victim, PlaygroundZone zone, float mitigatedDamage) {
        if (mitigatedDamage <= 0.0F || zone == null) {
            return mitigatedDamage;
        }
        if (zone.isDamageDisabled()) {
            return 0.0F;
        }
        return calculateScaledValue(mitigatedDamage, victim.getMaxHealth(), zone.getStandardHealth());
    }

    /**
     * Scales a damage or healing amount using the shared baseline rules.
     *
     * <ul>
     *     <li>Raw value at or above the baseline is capped at {@code maxHealth}.</li>
     *     <li>Raw value below the baseline is scaled by {@code maxHealth / baseline}.</li>
     *     <li>The result is rounded to two decimal places.</li>
     *     <li>A result that rounds to zero is returned as zero.</li>
     * </ul>
     */
    public static float calculateScaledValue(float original, float maxHealth, float baseline) {
        if (Float.isNaN(original)) {
            return 0.0F;
        }
        if (original <= 0.0F) {
            return original;
        }
        if (!Float.isFinite(maxHealth) || maxHealth <= 0.0F
                || !Float.isFinite(baseline) || baseline <= 0.0F) {
            return original;
        }

        // A raw value that reaches the baseline is already enough to cap out after scaling.
        if (!Float.isFinite(original) || original >= baseline) {
            return round2(maxHealth);
        }

        float result = (original / baseline) * maxHealth;
        if (!Float.isFinite(result) || result >= maxHealth) {
            return round2(maxHealth);
        }
        result = round2(result);
        return result > 0.0F ? result : 0.0F;
    }

    public static float round2(float value) {
        if (!Float.isFinite(value)) {
            return value;
        }
        float scaled = value * 100.0F;
        if (!Float.isFinite(scaled) || scaled >= Integer.MAX_VALUE) {
            return value;
        }
        return Math.round(scaled) / 100.0F;
    }

    /**
     * Void damage and command-style kill damage bypass the safe-zone damage disable.
     * Vanilla {@code /kill} removes the entity directly and does not call this path,
     * but {@code generic_kill} is covered for mods/commands that do.
     */
    public static boolean isUnstoppable(DamageSource source) {
        return source != null
                && (source.is(DamageTypes.FELL_OUT_OF_WORLD) || source.is(DamageTypes.GENERIC_KILL));
    }

    /**
     * @return the raw multiplier, or {@link Float#NaN} for invalid health values
     */
    public static float calculateMultiplier(float maxHealth, float baseline) {
        if (!Float.isFinite(maxHealth) || maxHealth <= 0.0F
                || !Float.isFinite(baseline) || baseline <= 0.0F) {
            return Float.NaN;
        }
        return maxHealth / baseline;
    }
}
