package com.minigameplayground;

import com.minigameplayground.command.PlaygroundCommand;
import com.minigameplayground.flight.FlightController;
import com.minigameplayground.zone.ZoneManager;
import com.minigameplayground.zone.ZoneStorage;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Server-side player combat balancer.
 *
 * <p>The mod never touches the {@code MAX_HEALTH} attribute. It only scales incoming
 * player-vs-player damage based on the victim's final max health and the standard
 * health configured for the playground zone.</p>
 */
public final class MiniGamePlaygroundMod implements ModInitializer {
    public static final String MOD_ID = "minigameplayground";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        FlightController.init();
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                PlaygroundCommand.register(dispatcher));

        // Zones are loaded once per server start and kept in memory. The damage path
        // never touches the disk.
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            MiniGamePlaygroundConfig.load();
            ZoneStorage.initialize(server);
            ZoneManager.load();
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> ZoneManager.saveIfDirty());
        ServerTickEvents.END_SERVER_TICK.register(server -> RestoreContext.tick());
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
                FlightController.onJoin(handler.player));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            if (handler.player != null) {
                FlightController.onDisconnect(handler.player);
            }
        });
        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) ->
                FlightController.onRespawn(newPlayer));

        LOGGER.info("MiniGamePlayground initialized (server-side damage scaling).");
    }
}
