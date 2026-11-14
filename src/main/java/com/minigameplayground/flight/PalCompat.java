package com.minigameplayground.flight;

import com.minigameplayground.MiniGamePlaygroundMod;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

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
    private static boolean callbackRegistered;
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

    /**
     * Blocks new PAL flight sources while the player is inside a flight-denied zone.
     *
     * <p>Directly writing {@code abilities.mayfly} fights PAL's own tracker and makes it
     * log a tamper warning every tick, then re-apply the source. The enable callback is
     * the supported way to make PAL itself treat the ability as disabled, so its tracker
     * and the vanilla ability stay consistent.</p>
     */
    static void registerFlightDenyCallback() {
        if (unavailable || callbackRegistered
                || !FabricLoader.getInstance().isModLoaded("playerabilitylib")) {
            return;
        }
        try {
            initialize();
            if (allowFlying == null) {
                return;
            }

            Class<?> callbackClass = Class.forName("io.github.ladysnake.pal.PlayerAbilityEnableCallback");
            // Event's implementation class is package-private; cast to the public
            // Fabric Event interface and let the JVM invoke its interface method.
            @SuppressWarnings({"rawtypes", "unchecked"})
            Event event = (Event) callbackClass.getField("EVENT").get(null);

            Object callback = Proxy.newProxyInstance(callbackClass.getClassLoader(),
                    new Class<?>[]{callbackClass},
                    (proxy, method, args) -> {
                        if (method.getName().equals("allow")) {
                            Object player = args[0];
                            Object ability = args[1];
                            if (ability == allowFlying
                                    && player instanceof ServerPlayer serverPlayer
                                    && !serverPlayer.isCreative()
                                    && !serverPlayer.isSpectator()
                                    && FlightController.isFlightDenied(serverPlayer)) {
                                return false;
                            }
                            return true;
                        }
                        return switch (method.getName()) {
                            case "toString" -> "MiniGamePlayground flight denier";
                            case "hashCode" -> System.identityHashCode(proxy);
                            case "equals" -> proxy == args[0];
                            default -> null;
                        };
                    });
            event.register(callback);
            callbackRegistered = true;
        } catch (Throwable throwable) {
            MiniGamePlaygroundMod.LOGGER.warn(
                    "PlayerAbilityLib flight enable callback is unavailable; PAL sources may fight the zone rule.",
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
