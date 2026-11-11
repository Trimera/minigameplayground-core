package com.minigameplayground.flight;

import com.minigameplayground.mixin.EntitySharedFlagAccessor;
import com.minigameplayground.zone.PlaygroundZone;
import com.minigameplayground.zone.ZoneManager;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

/**
 * Applies the per-zone elytra gliding rule.
 *
 * <p>If the player is in one or more zones, gliding is only allowed when at least one
 * containing zone allows it. Zones that disallow gliding force the shared fall-flying
 * flag off each tick, which also prevents the player from starting a new glide.</p>
 */
public final class ElytraController {
    private static final int FALL_FLYING_FLAG = 7;

    private ElytraController() {
    }

    public static void tick(ServerPlayer player) {
        if (player == null || player.level().isClientSide()) {
            return;
        }
        List<PlaygroundZone> zones = ZoneManager.getZonesAt(player);
        if (zones.isEmpty()) {
            return;
        }
        for (PlaygroundZone zone : zones) {
            if (zone.isElytraAllowed()) {
                return;
            }
        }
        if (player.isFallFlying()) {
            ((EntitySharedFlagAccessor) player)
                    .minigameplayground$setSharedFlag(FALL_FLYING_FLAG, false);
        }
    }
}
