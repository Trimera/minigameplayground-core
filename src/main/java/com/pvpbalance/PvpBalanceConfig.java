package com.pvpbalance;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/**
 * General mod settings, loaded from {@code config/pvpbalance.json}.
 */
public final class PvpBalanceConfig {
    public static final long DEFAULT_MAX_ZONE_VOLUME = 500_000L;
    public static final long HARD_MAX_ZONE_VOLUME = 20_000_000L;
    public static final long WARN_ZONE_VOLUME = 1_000_000L;

    private static final Path CONFIG_FILE =
            FabricLoader.getInstance().getConfigDir().resolve("pvpbalance.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static volatile long maxZoneVolume = DEFAULT_MAX_ZONE_VOLUME;

    private PvpBalanceConfig() {
    }

    public static synchronized void load() {
        maxZoneVolume = DEFAULT_MAX_ZONE_VOLUME;

        if (!Files.exists(CONFIG_FILE)) {
            writeDefault();
            PvpBalanceMod.LOGGER.info("Created default config at {} (maxZoneVolume={}).",
                    CONFIG_FILE, maxZoneVolume);
            return;
        }

        try (Reader reader = Files.newBufferedReader(CONFIG_FILE, StandardCharsets.UTF_8)) {
            ConfigData data = GSON.fromJson(reader, ConfigData.class);
            long configured = data == null ? DEFAULT_MAX_ZONE_VOLUME : data.maxZoneVolume;

            if (configured <= 0L) {
                PvpBalanceMod.LOGGER.warn(
                        "Invalid maxZoneVolume {}; using default {}.",
                        configured, DEFAULT_MAX_ZONE_VOLUME);
                configured = DEFAULT_MAX_ZONE_VOLUME;
            } else if (configured > HARD_MAX_ZONE_VOLUME) {
                PvpBalanceMod.LOGGER.warn(
                        "maxZoneVolume {} exceeds hard maximum {}; clamping.",
                        configured, HARD_MAX_ZONE_VOLUME);
                configured = HARD_MAX_ZONE_VOLUME;
            }

            maxZoneVolume = configured;
            PvpBalanceMod.LOGGER.info("Loaded config from {} (maxZoneVolume={}).",
                    CONFIG_FILE, maxZoneVolume);

            if (maxZoneVolume > WARN_ZONE_VOLUME) {
                PvpBalanceMod.LOGGER.warn(
                        "maxZoneVolume is {} blocks; large snapshots may cause memory usage and tick lag.",
                        maxZoneVolume);
            }
        } catch (Exception exception) {
            PvpBalanceMod.LOGGER.error("Failed to load config from {}. Using defaults.",
                    CONFIG_FILE, exception);
        }
    }

    public static long getMaxZoneVolume() {
        return maxZoneVolume;
    }

    public static boolean isWithinLimit(long volume) {
        return volume > 0L && volume <= maxZoneVolume;
    }

    private static void writeDefault() {
        ConfigData data = new ConfigData();
        data.maxZoneVolume = DEFAULT_MAX_ZONE_VOLUME;
        try {
            Path parent = CONFIG_FILE.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(CONFIG_FILE, GSON.toJson(data), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
        } catch (Exception exception) {
            PvpBalanceMod.LOGGER.error("Failed to create default config at {}.", CONFIG_FILE, exception);
        }
    }

    private static final class ConfigData {
        private long maxZoneVolume = DEFAULT_MAX_ZONE_VOLUME;
    }
}
