package com.minigameplayground.zone;

import com.minigameplayground.MiniGamePlaygroundMod;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Resolves the per-save storage location for playground zones and snapshots.
 *
 * <p>Zone definitions and snapshots belong to one world save. Keeping them under
 * {@code <world>/minigameplayground/} prevents a snapshot from one save from being
 * reset into another save. The global config file only stores server-wide limits.</p>
 *
 * <p>Global zone/snapshot files from older versions are intentionally ignored. They
 * are not scanned or migrated at startup; copy any wanted files into the save
 * directory manually.</p>
 */
public final class ZoneStorage {
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
}
