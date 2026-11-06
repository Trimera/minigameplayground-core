package com.minigameplayground;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/**
 * General mod settings, loaded from {@code config/minigameplayground.json}.
 */
public final class MiniGamePlaygroundConfig {
    public static final long DEFAULT_MAX_ZONE_VOLUME = 500_000L;
    public static final long HARD_MAX_ZONE_VOLUME = 20_000_000L;
    public static final long WARN_ZONE_VOLUME = 1_000_000L;

    private static final Path CONFIG_FILE =
            FabricLoader.getInstance().getConfigDir().resolve("minigameplayground.json");
    private static final Path LEGACY_CONFIG_FILE =
            FabricLoader.getInstance().getConfigDir().resolve("pvpbalance.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static volatile long maxZoneVolume = DEFAULT_MAX_ZONE_VOLUME;
    private static volatile boolean allowMobDrops;

    private MiniGamePlaygroundConfig() {
    }

    public static synchronized void load() {
        maxZoneVolume = DEFAULT_MAX_ZONE_VOLUME;
        allowMobDrops = false;

        Path source = Files.exists(CONFIG_FILE) ? CONFIG_FILE
                : Files.exists(LEGACY_CONFIG_FILE) ? LEGACY_CONFIG_FILE
                : null;
        if (source == null) {
            write();
            MiniGamePlaygroundMod.LOGGER.info("Created default config at {} (maxZoneVolume={}, allowMobDrops={}).",
                    CONFIG_FILE, maxZoneVolume, allowMobDrops);
            return;
        }

        try (Reader reader = Files.newBufferedReader(source, StandardCharsets.UTF_8)) {
            ConfigData data = GSON.fromJson(reader, ConfigData.class);
            long configured = data == null ? DEFAULT_MAX_ZONE_VOLUME : data.maxZoneVolume;
            allowMobDrops = data != null && data.allowMobDrops;

            if (configured <= 0L) {
                MiniGamePlaygroundMod.LOGGER.warn(
                        "Invalid maxZoneVolume {}; using default {}.",
                        configured, DEFAULT_MAX_ZONE_VOLUME);
                configured = DEFAULT_MAX_ZONE_VOLUME;
            } else if (configured > HARD_MAX_ZONE_VOLUME) {
                MiniGamePlaygroundMod.LOGGER.warn(
                        "maxZoneVolume {} exceeds hard maximum {}; clamping.",
                        configured, HARD_MAX_ZONE_VOLUME);
                configured = HARD_MAX_ZONE_VOLUME;
            }

            maxZoneVolume = configured;
            MiniGamePlaygroundMod.LOGGER.info("Loaded config from {} (maxZoneVolume={}, allowMobDrops={}).",
                    source, maxZoneVolume, allowMobDrops);

            if (source.equals(LEGACY_CONFIG_FILE)) {
                write();
                MiniGamePlaygroundMod.LOGGER.info(
                        "Migrated legacy config from {} to {}.", LEGACY_CONFIG_FILE, CONFIG_FILE);
            }

            if (maxZoneVolume > WARN_ZONE_VOLUME) {
                MiniGamePlaygroundMod.LOGGER.warn(
                        "maxZoneVolume is {} blocks; large snapshots may cause memory usage and tick lag.",
                        maxZoneVolume);
            }
        } catch (Exception exception) {
            MiniGamePlaygroundMod.LOGGER.error("Failed to load config from {}. Using defaults.",
                    source, exception);
        }
    }

    public static long getMaxZoneVolume() {
        return maxZoneVolume;
    }

    /** Whether mobs may create item drops inside playground zones. Default: false. */
    public static boolean isMobDropsAllowed() {
        return allowMobDrops;
    }

    public static boolean isWithinLimit(long volume) {
        return volume > 0L && volume <= maxZoneVolume;
    }

    private static void write() {
        ConfigData data = new ConfigData();
        data.maxZoneVolume = maxZoneVolume;
        data.allowMobDrops = allowMobDrops;
        try {
            Path parent = CONFIG_FILE.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(CONFIG_FILE, GSON.toJson(data), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
        } catch (Exception exception) {
            MiniGamePlaygroundMod.LOGGER.error("Failed to write config at {}.", CONFIG_FILE, exception);
        }
    }

    private static final class ConfigData {
        private long maxZoneVolume = DEFAULT_MAX_ZONE_VOLUME;
        private boolean allowMobDrops;
    }
}
