package com.minigameplayground.zone;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.minigameplayground.MiniGamePlaygroundMod;
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
import java.util.regex.Pattern;

/**
 * In-memory registry of playground zones, persisted to
 * {@code <world>/minigameplayground/zones.json} for the current save.
 *
 * <p>The damage path only performs a small AABB lookup in this registry. Disk I/O is
 * limited to server startup, shutdown and administrative commands.</p>
 */
public final class ZoneManager {
    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .disableHtmlEscaping()
            .create();

    private static final Map<String, PlaygroundZone> ZONES = new LinkedHashMap<>();
    private static boolean dirty;

    private ZoneManager() {
    }

    public static synchronized void load() {
        ZONES.clear();
        dirty = false;

        Path source = ZoneStorage.zonesFile();
        if (!Files.isRegularFile(source)) {
            MiniGamePlaygroundMod.LOGGER.info(
                    "No playground zone config found at {} for this save; starting with no zones.", source);
            return;
        }

        try (Reader reader = Files.newBufferedReader(source, StandardCharsets.UTF_8)) {
            ConfigRoot root = GSON.fromJson(reader, ConfigRoot.class);
            if (root != null && root.zones != null) {
                for (ZoneData data : root.zones) {
                    if (data == null || !isValidId(data.id)) {
                        MiniGamePlaygroundMod.LOGGER.warn("Skipping invalid playground zone entry (missing/invalid id).");
                        continue;
                    }
                    if (!isValidHealth(data.standardHealth)
                            || !Double.isFinite(data.minX) || !Double.isFinite(data.minY)
                            || !Double.isFinite(data.minZ) || !Double.isFinite(data.maxX)
                            || !Double.isFinite(data.maxY) || !Double.isFinite(data.maxZ)) {
                        MiniGamePlaygroundMod.LOGGER.warn("Skipping invalid playground zone '{}' (bad bounds or damage baseline).", data.id);
                        continue;
                    }

                    float restoreBaseline = PlaygroundZone.DEFAULT_RESTORE_BASELINE;
                    if (data.restoreBaseline != null) {
                        if (isValidHealth(data.restoreBaseline)) {
                            restoreBaseline = data.restoreBaseline;
                        } else {
                            MiniGamePlaygroundMod.LOGGER.warn(
                                    "Zone '{}' has an invalid restore baseline; using default {}.",
                                    data.id, PlaygroundZone.DEFAULT_RESTORE_BASELINE);
                        }
                    }
                    boolean keepDurability = data.keepDurability == null || data.keepDurability;
                    boolean damageDisabled = data.damageDisabled != null && data.damageDisabled;
                    boolean healDisabled = data.healDisabled != null && data.healDisabled;
                    DamageMode damageMode = DamageMode.PVP;
                    if (data.damageMode != null && !data.damageMode.isBlank()) {
                        DamageMode parsed = DamageMode.fromId(data.damageMode);
                        if (parsed == null) {
                            MiniGamePlaygroundMod.LOGGER.warn(
                                    "Zone '{}' has an invalid damage mode '{}'; using pvp.",
                                    data.id, data.damageMode);
                        } else {
                            damageMode = parsed;
                        }
                    }

                    boolean allowCreativeFlight = data.allowCreativeFlight != null
                            && data.allowCreativeFlight;
                    boolean allowMobDrops = data.allowMobDrops != null
                            && data.allowMobDrops;
                    boolean keepInventory = data.keepInventory != null
                            && data.keepInventory;
                    boolean disableSimplySkills = data.disableSimplySkills != null
                            && data.disableSimplySkills;

                    String dimension = PlaygroundZone.DEFAULT_DIMENSION;
                    if (data.dimension != null && !data.dimension.isBlank()) {
                        if (ResourceLocation.tryParse(data.dimension) != null) {
                            dimension = data.dimension;
                        } else {
                            MiniGamePlaygroundMod.LOGGER.warn(
                                    "Zone '{}' has an invalid dimension '{}'; using default {}.",
                                    data.id, data.dimension, PlaygroundZone.DEFAULT_DIMENSION);
                        }
                    }

                    PlaygroundZone zone = new PlaygroundZone(data.id, dimension,
                            data.minX, data.minY, data.minZ,
                            data.maxX, data.maxY, data.maxZ, data.standardHealth,
                            restoreBaseline, keepDurability, damageDisabled, healDisabled,
                            damageMode, allowCreativeFlight, allowMobDrops,
                            keepInventory, disableSimplySkills);
                    if (ZONES.put(zone.getId(), zone) != null) {
                        MiniGamePlaygroundMod.LOGGER.warn("Duplicate playground zone id '{}'; the later entry wins.", zone.getId());
                    }
                }
            }
            MiniGamePlaygroundMod.LOGGER.info("Loaded {} playground zone(s) from {}.", ZONES.size(), source);
        } catch (Exception exception) {
            ZONES.clear();
            MiniGamePlaygroundMod.LOGGER.error("Failed to load playground zone config from {}.", source, exception);
        }
    }

    public static synchronized boolean save() {
        ConfigRoot root = new ConfigRoot();
        for (PlaygroundZone zone : ZONES.values()) {
            root.zones.add(ZoneData.from(zone));
        }

        try {
            Path parent = ZoneStorage.zonesFile().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }

            Path tempFile = ZoneStorage.zonesFile().resolveSibling(ZoneStorage.zonesFile().getFileName() + ".tmp");
            Files.writeString(tempFile, GSON.toJson(root), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);

            try {
                Files.move(tempFile, ZoneStorage.zonesFile(), StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(tempFile, ZoneStorage.zonesFile(), StandardCopyOption.REPLACE_EXISTING);
            }

            dirty = false;
            MiniGamePlaygroundMod.LOGGER.info("Saved {} playground zone(s) to {}.", ZONES.size(), ZoneStorage.zonesFile());
            return true;
        } catch (Exception exception) {
            MiniGamePlaygroundMod.LOGGER.error("Failed to save playground zone config to {}.", ZoneStorage.zonesFile(), exception);
            return false;
        }
    }

    public static synchronized void saveIfDirty() {
        if (dirty && ZoneStorage.isInitialized()) {
            save();
        }
    }

    /**
     * Adds a zone, replacing an existing zone with the same id.
     *
     * @return {@code true} when an existing zone was replaced
     */
    public static synchronized boolean upsert(PlaygroundZone zone) {
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
        PlaygroundZone old = ZONES.get(id);
        if (old == null) {
            return false;
        }
        ZONES.put(id, old.withStandardHealth(standardHealth).withDamageDisabled(false));
        dirty = true;
        save();
        return true;
    }

    public static synchronized boolean setRestoreBaseline(String id, float restoreBaseline) {
        PlaygroundZone old = ZONES.get(id);
        if (old == null) {
            return false;
        }
        ZONES.put(id, old.withRestoreBaseline(restoreBaseline).withHealDisabled(false));
        dirty = true;
        save();
        return true;
    }

    public static synchronized boolean setKeepDurability(String id, boolean keepDurability) {
        PlaygroundZone old = ZONES.get(id);
        if (old == null) {
            return false;
        }
        ZONES.put(id, old.withKeepDurability(keepDurability));
        dirty = true;
        save();
        return true;
    }

    public static synchronized boolean setDamageDisabled(String id, boolean disabled) {
        PlaygroundZone old = ZONES.get(id);
        if (old == null) {
            return false;
        }
        ZONES.put(id, old.withDamageDisabled(disabled));
        dirty = true;
        save();
        return true;
    }

    public static synchronized boolean setHealDisabled(String id, boolean disabled) {
        PlaygroundZone old = ZONES.get(id);
        if (old == null) {
            return false;
        }
        ZONES.put(id, old.withHealDisabled(disabled));
        dirty = true;
        save();
        return true;
    }

    public static synchronized boolean setDamageMode(String id, DamageMode damageMode) {
        PlaygroundZone old = ZONES.get(id);
        if (old == null || damageMode == null) {
            return false;
        }
        ZONES.put(id, old.withDamageMode(damageMode));
        dirty = true;
        save();
        return true;
    }

    public static synchronized boolean setAllowCreativeFlight(String id, boolean allowed) {
        PlaygroundZone old = ZONES.get(id);
        if (old == null) {
            return false;
        }
        ZONES.put(id, old.withAllowCreativeFlight(allowed));
        dirty = true;
        save();
        return true;
    }

    public static synchronized boolean setAllowMobDrops(String id, boolean allowed) {
        PlaygroundZone old = ZONES.get(id);
        if (old == null) {
            return false;
        }
        ZONES.put(id, old.withAllowMobDrops(allowed));
        dirty = true;
        save();
        return true;
    }

    public static synchronized boolean setKeepInventory(String id, boolean keepInventory) {
        PlaygroundZone old = ZONES.get(id);
        if (old == null) {
            return false;
        }
        ZONES.put(id, old.withKeepInventory(keepInventory));
        dirty = true;
        save();
        return true;
    }

    public static synchronized boolean setDisableSimplySkills(String id, boolean disabled) {
        PlaygroundZone old = ZONES.get(id);
        if (old == null) {
            return false;
        }
        ZONES.put(id, old.withDisableSimplySkills(disabled));
        dirty = true;
        save();
        return true;
    }

    public static synchronized PlaygroundZone getZone(String id) {
        return ZONES.get(id);
    }

    public static synchronized PlaygroundZone getZone(ServerPlayer player) {
        for (PlaygroundZone zone : ZONES.values()) {
            if (zone.contains(player)) {
                return zone;
            }
        }
        return null;
    }

    /**
     * Returns every zone that contains the given world position. Used by the mob-drop
     * check so overlapping zones can apply the lenient rule "any allowing zone wins".
     */
    public static synchronized List<PlaygroundZone> getZonesAt(Level level,
                                                               double x, double y, double z) {
        if (level == null || level.isClientSide()) {
            return List.of();
        }
        List<PlaygroundZone> result = new ArrayList<>();
        for (PlaygroundZone zone : ZONES.values()) {
            if (zone.isDimension(level) && zone.contains(x, y, z)) {
                result.add(zone);
            }
        }
        return result;
    }

    /**
     * Returns every zone that contains the player. Flight checks use this so overlapping
     * zones can apply the lenient rule "any allowing zone wins".
     */
    public static synchronized List<PlaygroundZone> getZonesAt(ServerPlayer player) {
        if (player == null) {
            return List.of();
        }
        List<PlaygroundZone> result = new ArrayList<>();
        for (PlaygroundZone zone : ZONES.values()) {
            if (zone.contains(player)) {
                result.add(zone);
            }
        }
        return result;
    }

    /**
     * Returns the first zone that contains both players, or {@code null} when they are
     * not in a shared playground zone. This also behaves correctly for overlapping zones.
     */
    public static synchronized PlaygroundZone getCommonZone(ServerPlayer first, ServerPlayer second) {
        for (PlaygroundZone zone : ZONES.values()) {
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
    public static synchronized boolean isInAnyPlaygroundZone(Level level, BlockPos pos) {
        if (level == null || level.isClientSide()) {
            return false;
        }
        for (PlaygroundZone zone : ZONES.values()) {
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
    public static synchronized List<PlaygroundZone> getZonesIntersecting(Level level, AABB bounds) {
        if (level == null || level.isClientSide() || bounds == null) {
            return List.of();
        }
        List<PlaygroundZone> result = new ArrayList<>();
        for (PlaygroundZone zone : ZONES.values()) {
            if (zone.isDimension(level) && zone.intersectsBlockBounds(bounds)) {
                result.add(zone);
            }
        }
        return result;
    }

    /**
     * Matches zone ids using a simple glob: {@code *} for any sequence and
     * {@code ?} for one character. Results keep the zone registry order.
     */
    public static synchronized List<PlaygroundZone> matchZones(String pattern) {
        if (pattern == null || pattern.isBlank()) {
            return List.of();
        }
        if (pattern.indexOf('*') < 0 && pattern.indexOf('?') < 0) {
            PlaygroundZone exact = ZONES.get(pattern);
            return exact == null ? List.of() : List.of(exact);
        }
        Pattern regex = Pattern.compile(globToRegex(pattern));
        List<PlaygroundZone> result = new ArrayList<>();
        for (PlaygroundZone zone : ZONES.values()) {
            if (regex.matcher(zone.getId()).matches()) {
                result.add(zone);
            }
        }
        return result;
    }

    private static String globToRegex(String glob) {
        StringBuilder regex = new StringBuilder("^");
        for (int i = 0; i < glob.length(); i++) {
            char c = glob.charAt(i);
            switch (c) {
                case '*' -> regex.append(".*");
                case '?' -> regex.append('.');
                default -> {
                    if ("\\.^$|()[]{}+-".indexOf(c) >= 0) {
                        regex.append('\\');
                    }
                    regex.append(c);
                }
            }
        }
        return regex.append('$').toString();
    }

    public static synchronized List<PlaygroundZone> getZones() {
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
        private int version = 6;
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
        private String damageMode;
        private Boolean allowCreativeFlight;
        private Boolean allowMobDrops;
        private Boolean keepInventory;
        private Boolean disableSimplySkills;

        private static ZoneData from(PlaygroundZone zone) {
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
            data.damageMode = zone.getDamageMode().getId();
            data.allowCreativeFlight = zone.isCreativeFlightAllowed();
            data.allowMobDrops = zone.isMobDropsAllowed();
            data.keepInventory = zone.isKeepInventory();
            data.disableSimplySkills = zone.isSimplySkillsDisabled();
            return data;
        }
    }
}
