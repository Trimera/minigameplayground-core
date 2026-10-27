package com.pvpbalance.zone;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.pvpbalance.PvpBalanceMod;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * In-memory registry of PVP zones, persisted to {@code config/pvpbalance-zones.json}.
 *
 * <p>The damage path only performs a small AABB lookup in this registry. Disk I/O is
 * limited to server startup, shutdown and administrative commands.</p>
 */
public final class ZoneManager {
    private static final Path CONFIG_FILE =
            FabricLoader.getInstance().getConfigDir().resolve("pvpbalance-zones.json");
    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .disableHtmlEscaping()
            .create();

    private static final Map<String, PvpZone> ZONES = new LinkedHashMap<>();
    private static boolean dirty;

    private ZoneManager() {
    }

    public static synchronized void load() {
        ZONES.clear();
        dirty = false;

        if (!Files.exists(CONFIG_FILE)) {
            PvpBalanceMod.LOGGER.info("No PVP zone config found at {}; starting with no zones.", CONFIG_FILE);
            return;
        }

        try (Reader reader = Files.newBufferedReader(CONFIG_FILE, StandardCharsets.UTF_8)) {
            ConfigRoot root = GSON.fromJson(reader, ConfigRoot.class);
            if (root != null && root.zones != null) {
                for (ZoneData data : root.zones) {
                    if (data == null || !isValidId(data.id)) {
                        PvpBalanceMod.LOGGER.warn("Skipping invalid PVP zone entry (missing/invalid id).");
                        continue;
                    }
                    if (!isValidHealth(data.standardHealth)
                            || !Double.isFinite(data.minX) || !Double.isFinite(data.minY)
                            || !Double.isFinite(data.minZ) || !Double.isFinite(data.maxX)
                            || !Double.isFinite(data.maxY) || !Double.isFinite(data.maxZ)) {
                        PvpBalanceMod.LOGGER.warn("Skipping invalid PVP zone '{}' (bad bounds or damage baseline).", data.id);
                        continue;
                    }

                    float restoreBaseline = PvpZone.DEFAULT_RESTORE_BASELINE;
                    if (data.restoreBaseline != null) {
                        if (isValidHealth(data.restoreBaseline)) {
                            restoreBaseline = data.restoreBaseline;
                        } else {
                            PvpBalanceMod.LOGGER.warn(
                                    "Zone '{}' has an invalid restore baseline; using default {}.",
                                    data.id, PvpZone.DEFAULT_RESTORE_BASELINE);
                        }
                    }
                    boolean keepDurability = data.keepDurability == null || data.keepDurability;
                    boolean damageDisabled = data.damageDisabled != null && data.damageDisabled;
                    boolean healDisabled = data.healDisabled != null && data.healDisabled;

                    String dimension = PvpZone.DEFAULT_DIMENSION;
                    if (data.dimension != null && !data.dimension.isBlank()) {
                        if (ResourceLocation.tryParse(data.dimension) != null) {
                            dimension = data.dimension;
                        } else {
                            PvpBalanceMod.LOGGER.warn(
                                    "Zone '{}' has an invalid dimension '{}'; using default {}.",
                                    data.id, data.dimension, PvpZone.DEFAULT_DIMENSION);
                        }
                    }

                    PvpZone zone = new PvpZone(data.id, dimension,
                            data.minX, data.minY, data.minZ,
                            data.maxX, data.maxY, data.maxZ, data.standardHealth,
                            restoreBaseline, keepDurability, damageDisabled, healDisabled);
                    if (ZONES.put(zone.getId(), zone) != null) {
                        PvpBalanceMod.LOGGER.warn("Duplicate PVP zone id '{}'; the later entry wins.", zone.getId());
                    }
                }
            }
            PvpBalanceMod.LOGGER.info("Loaded {} PVP zone(s) from {}.", ZONES.size(), CONFIG_FILE);
        } catch (Exception exception) {
            ZONES.clear();
            PvpBalanceMod.LOGGER.error("Failed to load PVP zone config from {}.", CONFIG_FILE, exception);
        }
    }

    public static synchronized boolean save() {
        ConfigRoot root = new ConfigRoot();
        for (PvpZone zone : ZONES.values()) {
            root.zones.add(ZoneData.from(zone));
        }

        try {
            Path parent = CONFIG_FILE.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }

            Path tempFile = CONFIG_FILE.resolveSibling(CONFIG_FILE.getFileName() + ".tmp");
            Files.writeString(tempFile, GSON.toJson(root), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);

            try {
                Files.move(tempFile, CONFIG_FILE, StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(tempFile, CONFIG_FILE, StandardCopyOption.REPLACE_EXISTING);
            }

            dirty = false;
            PvpBalanceMod.LOGGER.info("Saved {} PVP zone(s) to {}.", ZONES.size(), CONFIG_FILE);
            return true;
        } catch (Exception exception) {
            PvpBalanceMod.LOGGER.error("Failed to save PVP zone config to {}.", CONFIG_FILE, exception);
            return false;
        }
    }

    public static synchronized void saveIfDirty() {
        if (dirty) {
            save();
        }
    }

    /**
     * Adds a zone, replacing an existing zone with the same id.
     *
     * @return {@code true} when an existing zone was replaced
     */
    public static synchronized boolean upsert(PvpZone zone) {
        boolean replaced = ZONES.put(zone.getId(), zone) != null;
        dirty = true;
        save();
        return replaced;
    }

    public static synchronized boolean remove(String id) {
        if (ZONES.remove(id) == null) {
            return false;
        }
        dirty = true;
        save();
        return true;
    }

    public static synchronized boolean setStandardHealth(String id, float standardHealth) {
        PvpZone old = ZONES.get(id);
        if (old == null) {
            return false;
        }
        ZONES.put(id, old.withStandardHealth(standardHealth).withDamageDisabled(false));
        dirty = true;
        save();
        return true;
    }

    public static synchronized boolean setRestoreBaseline(String id, float restoreBaseline) {
        PvpZone old = ZONES.get(id);
        if (old == null) {
            return false;
        }
        ZONES.put(id, old.withRestoreBaseline(restoreBaseline).withHealDisabled(false));
        dirty = true;
        save();
        return true;
    }

    public static synchronized boolean setKeepDurability(String id, boolean keepDurability) {
        PvpZone old = ZONES.get(id);
        if (old == null) {
            return false;
        }
        ZONES.put(id, old.withKeepDurability(keepDurability));
        dirty = true;
        save();
        return true;
    }

    public static synchronized boolean setDamageDisabled(String id, boolean disabled) {
        PvpZone old = ZONES.get(id);
        if (old == null) {
            return false;
        }
        ZONES.put(id, old.withDamageDisabled(disabled));
        dirty = true;
        save();
        return true;
    }

    public static synchronized boolean setHealDisabled(String id, boolean disabled) {
        PvpZone old = ZONES.get(id);
        if (old == null) {
            return false;
        }
        ZONES.put(id, old.withHealDisabled(disabled));
        dirty = true;
        save();
        return true;
    }

    public static synchronized PvpZone getZone(String id) {
        return ZONES.get(id);
    }

    public static synchronized PvpZone getZone(ServerPlayer player) {
        for (PvpZone zone : ZONES.values()) {
            if (zone.contains(player)) {
                return zone;
            }
        }
        return null;
    }

    /**
     * Returns the first zone that contains both players, or {@code null} when they are
     * not in a shared PVP zone. This also behaves correctly for overlapping zones.
     */
    public static synchronized PvpZone getCommonZone(ServerPlayer first, ServerPlayer second) {
        for (PvpZone zone : ZONES.values()) {
            if (zone.contains(first) && zone.contains(second)) {
                return zone;
            }
        }
        return null;
    }

    /**
     * Used by the block-drop mixins. Only block positions inside a configured zone are
     * considered; entity drops outside zones are untouched.
     */
    public static synchronized boolean isInAnyPvpZone(Level level, BlockPos pos) {
        if (level == null || level.isClientSide()) {
            return false;
        }
        for (PvpZone zone : ZONES.values()) {
            if (zone.containsBlock(level, pos)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns every zone in the given dimension whose block bounds overlap the
     * supplied world-space bounds. Used to mark assembled contraptions.
     */
    public static synchronized List<PvpZone> getZonesIntersecting(Level level, AABB bounds) {
        if (level == null || level.isClientSide() || bounds == null) {
            return List.of();
        }
        List<PvpZone> result = new ArrayList<>();
        for (PvpZone zone : ZONES.values()) {
            if (zone.isDimension(level) && zone.intersectsBlockBounds(bounds)) {
                result.add(zone);
            }
        }
        return result;
    }

    public static synchronized List<PvpZone> getZones() {
        return List.copyOf(ZONES.values());
    }

    public static synchronized Set<String> getZoneIds() {
        return Collections.unmodifiableSet(new LinkedHashSet<>(ZONES.keySet()));
    }

    public static synchronized int size() {
        return ZONES.size();
    }

    public static boolean isValidId(String id) {
        if (id == null) {
            return false;
        }
        String trimmed = id.trim();
        if (trimmed.isEmpty() || trimmed.length() > 64) {
            return false;
        }
        for (int i = 0; i < trimmed.length(); i++) {
            char c = trimmed.charAt(i);
            if (!Character.isLetterOrDigit(c) && c != '_' && c != '-' && c != '.') {
                return false;
            }
        }
        return true;
    }

    public static boolean isValidHealth(float health) {
        return Float.isFinite(health) && health > 0.0F;
    }

    private static final class ConfigRoot {
        private int version = 4;
        private List<ZoneData> zones = new ArrayList<>();
    }

    private static final class ZoneData {
        private String id;
        private double minX;
        private double minY;
        private double minZ;
        private double maxX;
        private double maxY;
        private double maxZ;
        private String dimension;
        private float standardHealth;
        private Float restoreBaseline;
        private Boolean keepDurability;
        private Boolean damageDisabled;
        private Boolean healDisabled;

        private static ZoneData from(PvpZone zone) {
            ZoneData data = new ZoneData();
            data.id = zone.getId();
            data.dimension = zone.getDimension();
            data.minX = zone.getMinX();
            data.minY = zone.getMinY();
            data.minZ = zone.getMinZ();
            data.maxX = zone.getMaxX();
            data.maxY = zone.getMaxY();
            data.maxZ = zone.getMaxZ();
            data.standardHealth = zone.getStandardHealth();
            data.restoreBaseline = zone.getRestoreBaseline();
            data.keepDurability = zone.isKeepDurability();
            data.damageDisabled = zone.isDamageDisabled();
            data.healDisabled = zone.isHealDisabled();
            return data;
        }
    }
}
