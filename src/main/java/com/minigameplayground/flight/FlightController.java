package com.minigameplayground.flight;

import com.minigameplayground.zone.PlaygroundZone;
import com.minigameplayground.zone.ZoneManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.Abilities;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Applies the per-zone creative-flight policy.
 *
 * <p>The controller runs at the end of {@link ServerPlayer#tick()}, after equipment,
 * potions, commands and map contraptions have had their chance to set
 * {@code abilities.mayfly}. It never tries to identify the source: in a zone that
 * disallows flight it simply forces the final ability state off. When the player leaves
 * (or enters an allowing zone) suppression is released and, if PlayerAbilityLib is
 * present, its sources are refreshed. No flight is ever granted here.</p>
 */
public final class FlightController {
    private static final Set<UUID> SUPPRESSED = new HashSet<>();

    private FlightController() {
    }

    public static void init() {
        PalCompat.registerFlightDenyCallback();
    }

    /**
     * Runs before the player's movement tick. Vanilla resets {@code fallDistance}
     * while {@code abilities.flying} is true, so flight must be cleared here as well
     * as at the end of the tick. Otherwise a flight source that re-grants the ability
     * every tick can keep resetting fall distance and suppress fall damage entirely.
     */
    public static void beforeTick(ServerPlayer player) {
        if (player.isCreative() || player.isSpectator()) {
            return;
        }
        if (!isFlightDenied(player)) {
            return;
        }

        SUPPRESSED.add(player.getUUID());
        suppressFlight(player);
    }

    public static void tick(ServerPlayer player) {
        if (player.isCreative() || player.isSpectator()) {
            release(player);
            return;
        }

        if (!isFlightDenied(player)) {
            release(player);
            return;
        }

        SUPPRESSED.add(player.getUUID());
        suppressFlight(player);
    }

    private static void suppressFlight(ServerPlayer player) {
        Abilities abilities = player.getAbilities();
        boolean changed = false;

        if (abilities.mayfly) {
            // Let PAL disable its own source first; only write the ability directly for
            // non-PAL flight sources.
            PalCompat.refreshFlight(player);
            if (abilities.mayfly) {
                abilities.mayfly = false;
                changed = true;
            }
        }
        if (abilities.flying) {
            abilities.flying = false;
            changed = true;
        }
        if (changed) {
            player.onUpdateAbilities();
        }
    }

    public static void onJoin(ServerPlayer player) {
        SUPPRESSED.remove(player.getUUID());
        if (!isFlightDenied(player)) {
            PalCompat.refreshFlight(player);
        }
    }

    public static void onRespawn(ServerPlayer player) {
        SUPPRESSED.remove(player.getUUID());
        if (!isFlightDenied(player)) {
            PalCompat.refreshFlight(player);
        }
    }

    public static void onDisconnect(ServerPlayer player) {
        SUPPRESSED.remove(player.getUUID());
    }

    private static void release(ServerPlayer player) {
        if (SUPPRESSED.remove(player.getUUID())) {
            PalCompat.refreshFlight(player);
        }
    }

    static boolean isFlightDenied(ServerPlayer player) {
        if (ZoneManager.size() == 0) {
            return false;
        }
        List<PlaygroundZone> zones = ZoneManager.getZonesAt(player);
        if (zones.isEmpty()) {
            return false;
        }
        for (PlaygroundZone zone : zones) {
            if (zone.isCreativeFlightAllowed()) {
                return false;
            }
        }
        return true;
    }
}
