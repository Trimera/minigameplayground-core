package com.minigameplayground.zone;

import com.minigameplayground.MiniGamePlaygroundMod;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;

/**
 * Resolves the per-save storage location for playground zones and snapshots.
 *
 * <p>Zone definitions and block/entity snapshots belong to one world save. Keeping
 * them under {@code <world>/minigameplayground/} prevents a snapshot from one save
 * from being reset into another save. The global config file only stores server-wide
 * limits.</p>
 */
public final class ZoneStorage {
    private static final Path CONFIG_DIR = FabricLoader.getInstance().getConfigDir();
    private static final Path LEGACY_ZONES = CONFIG_DIR.resolve("minigameplayground-zones.json");
    private static final Path LEGACY_ZONES_OLD = CONFIG_DIR.resolve("pvpbalance-zones.json");
    private static final Path MIGRATION_MARKER =
            CONFIG_DIR.resolve("minigameplayground-legacy-zones-migrated.txt");

    private static Path root;
    private static Path zonesFile;
    private static Path snapshotsDir;

    private ZoneStorage() {
    }

    public static synchronized void initialize(MinecraftServer server) {
        Path world = server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
        root = world.resolve("minigameplayground");
        zonesFile = root.resolve("zones.json");
        snapshotsDir = root.resolve("snapshots");
        try {
            Files.createDirectories(snapshotsDir);
        } catch (Exception exception) {
            MiniGamePlaygroundMod.LOGGER.error(
                    "Failed to create per-save snapshot directory {}.", snapshotsDir, exception);
        }
        migrateLegacyZonesOnce(world);
    }

    public static boolean isInitialized() {
        return root != null;
    }

    public static Path root() {
        checkInitialized();
        return root;
    }

    public static Path zonesFile() {
        checkInitialized();
        return zonesFile;
    }

    public static Path snapshotsDir() {
        checkInitialized();
        return snapshotsDir;
    }

    private static void checkInitialized() {
        if (root == null) {
            throw new IllegalStateException("ZoneStorage has not been initialized for a server yet.");
        }
    }

    /**
     * Imports the old global zone definitions once, into the first save started after
     * the update. Legacy snapshots are intentionally not copied: they were shared by
     * every save and could contain blocks from a different world.
     */
    private static void migrateLegacyZonesOnce(Path world) {
        if (Files.isRegularFile(MIGRATION_MARKER)) {
            return;
        }
        Path legacy = Files.isRegularFile(LEGACY_ZONES) ? LEGACY_ZONES
                : Files.isRegularFile(LEGACY_ZONES_OLD) ? LEGACY_ZONES_OLD : null;
        try {
            if (legacy != null && !Files.exists(zonesFile)) {
                Files.createDirectories(root);
                Files.copy(legacy, zonesFile, StandardCopyOption.REPLACE_EXISTING);
                MiniGamePlaygroundMod.LOGGER.warn(
                        "Imported legacy zone definitions from {} into save '{}'. "
                                + "Legacy snapshots were not copied; run /mgp snapshot <id> "
                                + "inside each zone to create per-save snapshots.",
                        legacy, world);
            }
            Path parent = MIGRATION_MARKER.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(MIGRATION_MARKER,
                    "Legacy zones migrated to: " + world + System.lineSeparator()
                            + "This marker prevents importing the same global zones into other saves."
                            + System.lineSeparator(),
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE);
        } catch (Exception exception) {
            MiniGamePlaygroundMod.LOGGER.warn(
                    "Failed to migrate legacy playground zones into save '{}'.", world, exception);
        }
    }
}
