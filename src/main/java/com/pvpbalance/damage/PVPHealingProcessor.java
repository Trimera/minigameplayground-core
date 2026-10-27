package com.pvpbalance.damage;

import com.pvpbalance.zone.PvpZone;
import com.pvpbalance.zone.ZoneManager;
import net.minecraft.server.level.ServerPlayer;

/**
 * Scales healing received by players inside a PVP zone.
 *
 * <p>The multiplier is {@code maxHealth / restoreBaseline}: a player above the zone's
 * restore baseline heals faster, while a player below it heals slower. All vanilla
 * healing paths call {@code LivingEntity.heal}, including natural hunger/saturation
 * regeneration, the Regeneration effect and instant health effects.</p>
 */
public final class PVPHealingProcessor {
    private PVPHealingProcessor() {
    }

    public static float processHealing(ServerPlayer player, float originalHeal) {
        if (player == null || originalHeal <= 0.0F) {
            return originalHeal;
        }

        PvpZone zone = ZoneManager.getZone(player);
        if (zone == null) {
            return originalHeal;
        }
        if (zone.isHealDisabled()) {
            return 0.0F;
        }

        return PVPDamageProcessor.calculateScaledValue(
                originalHeal, player.getMaxHealth(), zone.getRestoreBaseline());
    }

    public static float calculateHealingMultiplier(float maxHealth, float restoreBaseline) {
        return PVPDamageProcessor.calculateMultiplier(maxHealth, restoreBaseline);
    }
}
