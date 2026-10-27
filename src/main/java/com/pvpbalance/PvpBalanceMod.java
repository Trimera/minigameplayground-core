package com.pvpbalance;

import com.pvpbalance.command.PvpCommand;
import com.pvpbalance.zone.ZoneManager;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Server-side PVP damage balancer.
 *
 * <p>The mod never touches the {@code MAX_HEALTH} attribute. It only scales incoming
 * player-vs-player damage based on the victim's final max health and the standard
 * health configured for the PVP zone.</p>
 */
public final class PvpBalanceMod implements ModInitializer {
    public static final String MOD_ID = "pvpbalance";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                PvpCommand.register(dispatcher));

        // Zones are loaded once per server start and kept in memory. The damage path
        // never touches the disk.
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            PvpBalanceConfig.load();
            ZoneManager.load();
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> ZoneManager.saveIfDirty());

        LOGGER.info("PVP Balance initialized (server-side damage scaling).");
    }
}
