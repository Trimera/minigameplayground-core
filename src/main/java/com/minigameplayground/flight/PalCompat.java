package com.minigameplayground.flight;

import com.minigameplayground.MiniGamePlaygroundMod;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import java.lang.reflect.Method;

/**
 * Optional PlayerAbilityLib bridge.
 *
 * <p>PAL is bundled by Winged and is also used by other ability mods. Refreshing the
 * ALLOW_FLYING tracker after a zone suppression makes those mods reapply their current
 * source immediately. If the source was removed or expired, PAL refreshes to false.</p>
 */
final class PalCompat {
    private static boolean initialized;
    private static boolean unavailable;
    private static Object allowFlying;
    private static Method getTracker;
    private static Method refresh;

    private PalCompat() {
    }

    static void refreshFlight(ServerPlayer player) {
        if (unavailable || !FabricLoader.getInstance().isModLoaded("playerabilitylib")) {
            return;
        }
        try {
            initialize();
            if (allowFlying == null || getTracker == null || refresh == null) {
                return;
            }
            Object tracker = getTracker.invoke(allowFlying, player);
            if (tracker != null) {
                refresh.invoke(tracker, true);
            }
        } catch (Throwable throwable) {
            unavailable = true;
            MiniGamePlaygroundMod.LOGGER.warn(
                    "PlayerAbilityLib flight refresh is unavailable; leaving ability sources untouched.",
                    throwable);
        }
    }

    private static void initialize() throws ReflectiveOperationException {
        if (initialized) {
            return;
        }
        Class<?> vanillaAbilities = Class.forName("io.github.ladysnake.pal.VanillaAbilities");
        allowFlying = vanillaAbilities.getField("ALLOW_FLYING").get(null);
        Class<?> playerAbility = Class.forName("io.github.ladysnake.pal.PlayerAbility");
        getTracker = playerAbility.getMethod("getTracker", Player.class);
        Class<?> abilityTracker = Class.forName("io.github.ladysnake.pal.AbilityTracker");
        refresh = abilityTracker.getMethod("refresh", boolean.class);
        initialized = true;
    }
}
