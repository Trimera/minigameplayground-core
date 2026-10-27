package com.minigameplayground;

import com.minigameplayground.command.PlaygroundCommand;
import com.minigameplayground.zone.ZoneManager;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
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
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                PlaygroundCommand.register(dispatcher));

        // Zones are loaded once per server start and kept in memory. The damage path
        // never touches the disk.
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            MiniGamePlaygroundConfig.load();
            ZoneManager.load();
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> ZoneManager.saveIfDirty());

        LOGGER.info("MiniGamePlayground initialized (server-side damage scaling).");
    }
}
