package com.minigameplayground.zone;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.minigameplayground.MiniGamePlaygroundConfig;
import com.minigameplayground.MiniGamePlaygroundMod;
import com.minigameplayground.RestoreContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.AABB;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * Stores the initial block and entity state of a playground zone and restores it on demand.
 *
 * <p>If WorldEdit is installed, its clipboard format is used for blocks. Otherwise a
 * built-in vanilla structure-template snapshot is used, so {@code /mgp reset} still works
 * on servers that do not have WorldEdit. Non-player entity snapshots live in separate
 * NBT files; compatibility addons can register a {@link ZoneSnapshotExtension} for their
 * own entity types. The volume limit keeps block snapshots bounded.</p>
 */
public final class ZoneSnapshots {
    private static final Path SNAPSHOT_DIR =
            FabricLoader.getInstance().getConfigDir().resolve("minigameplayground-snapshots");
    private static final Path LEGACY_SNAPSHOT_DIR =
            FabricLoader.getInstance().getConfigDir().resolve("pvpbalance-snapshots");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String WORLDEDIT_FORMAT = "worldedit";
    private static final String INTERNAL_FORMAT = "internal";
    private static final int ENTITY_SNAPSHOT_VERSION = 1;
    private static final List<ZoneSnapshotExtension> EXTENSIONS = new ArrayList<>();

    static {
        migrateLegacySnapshots();
    }

    private ZoneSnapshots() {
    }

    public static synchronized void registerExtension(ZoneSnapshotExtension extension) {
        if (extension == null || extension.id() == null || extension.id().isBlank()) {
            MiniGamePlaygroundMod.LOGGER.warn("Ignoring invalid zone snapshot extension.");
            return;
        }
        for (ZoneSnapshotExtension existing : EXTENSIONS) {
            if (existing.id().equals(extension.id())) {
                MiniGamePlaygroundMod.LOGGER.warn(
                        "Replacing duplicate zone snapshot extension '{}'.", extension.id());
                EXTENSIONS.remove(existing);
                break;
            }
        }
        EXTENSIONS.add(extension);
        MiniGamePlaygroundMod.LOGGER.info("Registered zone snapshot extension '{}'.", extension.id());
    }

    private static synchronized List<ZoneSnapshotExtension> extensions() {
        return List.copyOf(EXTENSIONS);
    }

    private static void migrateLegacySnapshots() {
        if (Files.exists(SNAPSHOT_DIR) || !Files.isDirectory(LEGACY_SNAPSHOT_DIR)) {
            return;
        }
        try {
            Files.createDirectories(SNAPSHOT_DIR);
            try (var paths = Files.walk(LEGACY_SNAPSHOT_DIR)) {
                paths.forEach(source -> {
                    try {
                        Path target = SNAPSHOT_DIR.resolve(LEGACY_SNAPSHOT_DIR.relativize(source).toString());
                        if (Files.isDirectory(source)) {
                            Files.createDirectories(target);
                        } else {
                            Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
                        }
                    } catch (Exception exception) {
                        MiniGamePlaygroundMod.LOGGER.warn(
                                "Failed to migrate snapshot file {}.", source, exception);
                    }
                });
            }
            MiniGamePlaygroundMod.LOGGER.info(
                    "Migrated legacy snapshots from {} to {}.", LEGACY_SNAPSHOT_DIR, SNAPSHOT_DIR);
        } catch (Exception exception) {
            MiniGamePlaygroundMod.LOGGER.warn(
                    "Failed to migrate legacy snapshot directory {}.", LEGACY_SNAPSHOT_DIR, exception);
        }
    }

    public static boolean isWithinVolumeLimit(PlaygroundZone zone) {
        return MiniGamePlaygroundConfig.isWithinLimit(zone.getBlockVolume());
    }

    public static boolean hasSnapshot(String zoneId) {
        return Files.isRegularFile(nbtFile(zoneId)) || Files.isRegularFile(schematicFile(zoneId));
    }

    public static boolean save(ServerLevel level, PlaygroundZone zone) {
        if (!zone.isDimension(level)) {
            MiniGamePlaygroundMod.LOGGER.error(
                    "Refusing to snapshot zone '{}': dimension mismatch (zone={}, level={}).",
                    zone.getId(), zone.getDimension(), level.dimension().location());
            return false;
        }
        if (!isWithinVolumeLimit(zone)) {
            MiniGamePlaygroundMod.LOGGER.warn("Refusing to snapshot zone '{}': volume {} exceeds the limit {}.",
                    zone.getId(), zone.getBlockVolume(), MiniGamePlaygroundConfig.getMaxZoneVolume());
            return false;
        }

        try {
            Files.createDirectories(SNAPSHOT_DIR);
        } catch (IOException exception) {
            MiniGamePlaygroundMod.LOGGER.error("Failed to create snapshot directory {}.", SNAPSHOT_DIR, exception);
            return false;
        }

        Path schematic = schematicFile(zone.getId());
        Path nbt = nbtFile(zone.getId());

        if (FabricLoader.getInstance().isModLoaded("worldedit")) {
            Path temp = tempFile(schematic);
            if (invokeWorldEditSave(level, zone, temp) && move(temp, schematic)) {
                deleteIfExists(nbt);
                writeMeta(zone.getId(), level, WORLDEDIT_FORMAT);
                if (!saveEntitySnapshots(level, zone)) {
                    MiniGamePlaygroundMod.LOGGER.error(
                            "Failed to save entity snapshot for zone '{}'.", zone.getId());
                    return false;
                }
                MiniGamePlaygroundMod.LOGGER.info("Saved WorldEdit snapshot for zone '{}'.", zone.getId());
                return true;
            }
            deleteIfExists(temp);
            MiniGamePlaygroundMod.LOGGER.warn("WorldEdit snapshot failed for zone '{}'; using built-in snapshot.", zone.getId());
        }

        Path temp = tempFile(nbt);
        if (saveInternal(level, zone, temp) && move(temp, nbt)) {
            deleteIfExists(schematic);
            writeMeta(zone.getId(), level, INTERNAL_FORMAT);
            if (!saveEntitySnapshots(level, zone)) {
                MiniGamePlaygroundMod.LOGGER.error(
                        "Failed to save entity snapshot for zone '{}'.", zone.getId());
                return false;
            }
            MiniGamePlaygroundMod.LOGGER.info("Saved built-in snapshot for zone '{}'.", zone.getId());
            return true;
        }
        deleteIfExists(temp);
        return false;
    }

    private static boolean saveEntitySnapshots(ServerLevel level, PlaygroundZone zone) {
        ListTag baseEntities = snapshotEntityNbt(level, zone, entity -> !isHandledEntity(entity));
        if (!writeEntityData(baseEntityFile(zone.getId()), baseEntities)) {
            return false;
        }

        for (ZoneSnapshotExtension extension : extensions()) {
            ListTag entities = extension.saveEntities(level, zone);
            if (entities == null) {
                return false;
            }
            if (!writeEntityData(extensionEntityFile(zone.getId(), extension.id()), entities)) {
                return false;
            }
        }
        return true;
    }

    private static boolean hasEntitySnapshot(PlaygroundZone zone) {
        if (Files.isRegularFile(baseEntityFile(zone.getId()))) {
            return true;
        }
        for (ZoneSnapshotExtension extension : extensions()) {
            if (Files.isRegularFile(extensionEntityFile(zone.getId(), extension.id()))) {
                return true;
            }
        }
        return false;
    }

    private static EntitySnapshotData readEntitySnapshots(PlaygroundZone zone) {
        Path baseFile = baseEntityFile(zone.getId());
        boolean anyExtensionFile = false;
        for (ZoneSnapshotExtension extension : extensions()) {
            if (Files.isRegularFile(extensionEntityFile(zone.getId(), extension.id()))) {
                anyExtensionFile = true;
                break;
            }
        }
        if (!Files.isRegularFile(baseFile) && !anyExtensionFile) {
            return null;
        }

        ListTag base = new ListTag();
        if (Files.isRegularFile(baseFile)) {
            base = readEntityData(baseFile);
            if (base == null) {
                return null;
            }
        }

        List<EntitySnapshotExtensionData> extensionData = new ArrayList<>();
        for (ZoneSnapshotExtension extension : extensions()) {
            Path file = extensionEntityFile(zone.getId(), extension.id());
            if (!Files.isRegularFile(file)) {
                continue;
            }
            ListTag entities = readEntityData(file);
            if (entities == null) {
                return null;
            }
            extensionData.add(new EntitySnapshotExtensionData(extension, entities));
        }
        return new EntitySnapshotData(base, extensionData);
    }

    private static int clearSnapshotEntities(ServerLevel level, PlaygroundZone zone) {
        int cleared = clearEntities(level, zone, entity -> !isHandledEntity(entity));
        for (ZoneSnapshotExtension extension : extensions()) {
            extension.clearEntities(level, zone);
        }
        return cleared;
    }

    private static int restoreSnapshotEntities(ServerLevel level, PlaygroundZone zone,
                                               EntitySnapshotData data) {
        int restored = restoreEntityNbt(level, data.baseEntities);
        for (EntitySnapshotExtensionData extensionData : data.extensions) {
            restored += restoreEntityNbt(level, extensionData.entities);
        }
        return restored;
    }

    public static ListTag snapshotEntityNbt(ServerLevel level, PlaygroundZone zone,
                                            Predicate<Entity> filter) {
        ListTag entities = new ListTag();
        if (level == null || zone == null || !zone.isDimension(level)) {
            return entities;
        }
        AABB box = zone.getBlockBounds();
        for (Entity entity : level.getEntities((Entity) null, box,
                candidate -> !(candidate instanceof Player)
                        && candidate.getVehicle() == null
                        && filter.test(candidate))) {
            CompoundTag tag = saveEntityNbt(entity);
            if (tag != null) {
                entities.add(tag);
            }
        }
        return entities;
    }

    /**
     * Removes non-player entities in a zone. Players riding any removed vehicle are
     * dismounted first so reset never leaves a player attached to a discarded entity.
     */
    public static int clearEntities(ServerLevel level, PlaygroundZone zone, Predicate<Entity> filter) {
        if (level == null || zone == null || !zone.isDimension(level)) {
            return 0;
        }
        AABB box = zone.getBlockBounds();
        List<Entity> entities = new ArrayList<>(level.getEntities((Entity) null, box,
                candidate -> !(candidate instanceof Player) && filter.test(candidate)));

        for (Entity entity : entities) {
            for (Entity passenger : List.copyOf(entity.getPassengers())) {
                if (passenger instanceof Player player) {
                    player.stopRiding();
                }
            }
        }
        for (Entity entity : entities) {
            entity.discard();
        }
        return entities.size();
    }

    public static CompoundTag saveEntityNbt(Entity entity) {
        if (entity == null || entity instanceof Player) {
            return null;
        }
        CompoundTag tag = new CompoundTag();
        if (!entity.save(tag)) {
            return null;
        }
        stripPlayerPassengers(tag);
        return tag;
    }

    public static int restoreEntityNbt(ServerLevel level, ListTag entities) {
        if (level == null || entities == null || entities.isEmpty()) {
            return 0;
        }
        int restored = 0;
        for (Tag element : entities) {
            if (!(element instanceof CompoundTag tag)) {
                continue;
            }
            if ("minecraft:player".equals(tag.getString("id"))) {
                continue;
            }
            stripPlayerPassengers(tag);
            try {
                Entity entity = EntityType.loadEntityRecursive(tag, level, loaded -> loaded);
                if (entity == null) {
                    MiniGamePlaygroundMod.LOGGER.warn(
                            "Failed to restore snapshot entity '{}'.", tag.getString("id"));
                    continue;
                }

                // The entity being replaced is discarded in the same tick. Some
                // entity implementations (notably Create contraptions) keep their
                // old UUID in the entity manager briefly, so adding the snapshot
                // entity with the saved UUID can fail silently. Restored entities
                // are new identities, not continuations of the discarded instance.
                entity.getSelfAndPassengers().forEach(loaded -> {
                    if (!(loaded instanceof Player)) {
                        loaded.setUUID(UUID.randomUUID());
                    }
                });

                if (!level.addFreshEntity(entity)) {
                    MiniGamePlaygroundMod.LOGGER.warn(
                            "Failed to add restored snapshot entity '{}'.", tag.getString("id"));
                    continue;
                }
                restored++;
            } catch (Exception exception) {
                MiniGamePlaygroundMod.LOGGER.warn(
                        "Failed to restore snapshot entity '{}'.", tag.getString("id"), exception);
            }
        }
        return restored;
    }

    private static void stripPlayerPassengers(CompoundTag tag) {
        if (!tag.contains("Passengers", Tag.TAG_LIST)) {
            return;
        }
        ListTag original = tag.getList("Passengers", Tag.TAG_COMPOUND);
        ListTag filtered = new ListTag();
        for (Tag element : original) {
            if (!(element instanceof CompoundTag passenger)) {
                continue;
            }
            if ("minecraft:player".equals(passenger.getString("id"))) {
                continue;
            }
            stripPlayerPassengers(passenger);
            filtered.add(passenger);
        }
        if (filtered.isEmpty()) {
            tag.remove("Passengers");
        } else {
            tag.put("Passengers", filtered);
        }
    }

    private static boolean isHandledEntity(Entity entity) {
        for (ZoneSnapshotExtension extension : extensions()) {
            if (extension.handlesEntity(entity)) {
                return true;
            }
        }
        return isCreateEntity(entity);
    }

    private static boolean isCreateEntity(Entity entity) {
        return entity != null && entity.getClass().getName().startsWith("com.simibubi.create.");
    }

    private static boolean writeEntityData(Path file, ListTag entities) {
        try {
            CompoundTag root = new CompoundTag();
            root.putInt("Version", ENTITY_SNAPSHOT_VERSION);
            root.put("Entities", entities);
            Path temp = tempFile(file);
            NbtIo.writeCompressed(root, temp.toFile());
            return move(temp, file);
        } catch (Exception exception) {
            MiniGamePlaygroundMod.LOGGER.error("Failed to write entity snapshot {}.", file, exception);
            return false;
        }
    }

    private static ListTag readEntityData(Path file) {
        try {
            CompoundTag root = NbtIo.readCompressed(file.toFile());
            ListTag entities = root.getList("Entities", Tag.TAG_COMPOUND);
            return entities;
        } catch (Exception exception) {
            MiniGamePlaygroundMod.LOGGER.error("Failed to read entity snapshot {}.", file, exception);
            return null;
        }
    }

    public static boolean restore(ServerLevel level, PlaygroundZone zone) {
        if (!zone.isDimension(level)) {
            MiniGamePlaygroundMod.LOGGER.error(
                    "Refusing to restore zone '{}': dimension mismatch (zone={}, level={}).",
                    zone.getId(), zone.getDimension(), level.dimension().location());
            return false;
        }
        if (!isWithinVolumeLimit(zone)) {
            MiniGamePlaygroundMod.LOGGER.warn("Refusing to restore zone '{}': volume {} exceeds the limit {}.",
                    zone.getId(), zone.getBlockVolume(), MiniGamePlaygroundConfig.getMaxZoneVolume());
            return false;
        }

        Path schematic = schematicFile(zone.getId());
        Path nbt = nbtFile(zone.getId());
        EntitySnapshotData entityData = readEntitySnapshots(zone);
        if (entityData == null && hasEntitySnapshot(zone)) {
            MiniGamePlaygroundMod.LOGGER.error(
                    "Failed to read entity snapshot for zone '{}'; refusing to reset.", zone.getId());
            return false;
        }

        if (entityData == null) {
            // Legacy snapshots did not contain entities. Keep the old reset behavior.
            clearItems(level, zone);
        } else {
            clearSnapshotEntities(level, zone);
        }

        boolean blockRestored = false;
        RestoreContext.beginRestore();
        try {
            if (Files.isRegularFile(schematic)) {
                if (FabricLoader.getInstance().isModLoaded("worldedit")
                        && invokeWorldEditRestore(level, zone, schematic)) {
                    blockRestored = true;
                } else if (Files.isRegularFile(nbt)) {
                    blockRestored = restoreInternal(level, zone, nbt);
                } else {
                    MiniGamePlaygroundMod.LOGGER.error(
                            "Zone '{}' has a WorldEdit snapshot but WorldEdit is not installed or failed to restore it.",
                            zone.getId());
                }
            } else if (Files.isRegularFile(nbt)) {
                blockRestored = restoreInternal(level, zone, nbt);
            } else {
                MiniGamePlaygroundMod.LOGGER.error("Zone '{}' has no snapshot file.", zone.getId());
            }
        } finally {
            RestoreContext.endRestore();
        }

        if (!blockRestored) {
            return false;
        }

        if (entityData != null) {
            int restored = restoreSnapshotEntities(level, zone, entityData);
            MiniGamePlaygroundMod.LOGGER.info(
                    "Restored {} snapshot entities for zone '{}'.", restored, zone.getId());
        }

        for (ZoneSnapshotExtension extension : extensions()) {
            try {
                extension.afterRestore(level, zone);
            } catch (Exception exception) {
                MiniGamePlaygroundMod.LOGGER.warn(
                        "Snapshot extension '{}' failed its post-restore hook for zone '{}'.",
                        extension.id(), zone.getId(), exception);
            }
        }
        return true;
    }

    /**
     * Resolves the zone's dimension first, then falls back to snapshot metadata and the
     * command source dimension. This lets console commands target the correct world.
     */
    public static ServerLevel resolveLevel(MinecraftServer server, ServerLevel fallback, PlaygroundZone zone) {
        ResourceLocation zoneDimension = ResourceLocation.tryParse(zone.getDimension());
        if (zoneDimension != null) {
            ServerLevel level = server.getLevel(ResourceKey.create(Registries.DIMENSION, zoneDimension));
            if (level != null) {
                return level;
            }
        }

        SnapshotMeta meta = readMeta(zone.getId());
        if (meta != null && meta.dimension != null && !meta.dimension.isBlank()) {
            ResourceLocation dimensionId = ResourceLocation.tryParse(meta.dimension);
            if (dimensionId != null) {
                ServerLevel level = server.getLevel(ResourceKey.create(Registries.DIMENSION, dimensionId));
                if (level != null) {
                    return level;
                }
            }
            MiniGamePlaygroundMod.LOGGER.warn("Could not resolve snapshot dimension '{}' for zone '{}'; using command dimension.",
                    meta.dimension, zone.getId());
        }
        return fallback;
    }

    public static int clearItems(ServerLevel level, PlaygroundZone zone) {
        if (!zone.isDimension(level)) {
            return 0;
        }
        AABB box = new AABB(
                zone.getMinBlockX(), zone.getMinBlockY(), zone.getMinBlockZ(),
                zone.getMaxBlockX() + 1.0D, zone.getMaxBlockY() + 1.0D, zone.getMaxBlockZ() + 1.0D);
        List<ItemEntity> items = level.getEntitiesOfClass(ItemEntity.class, box);
        for (ItemEntity item : items) {
            item.discard();
        }
        return items.size();
    }

    public static void remove(String zoneId) {
        deleteIfExists(nbtFile(zoneId));
        deleteIfExists(schematicFile(zoneId));
        deleteIfExists(metaFile(zoneId));
        deleteIfExists(baseEntityFile(zoneId));
        for (ZoneSnapshotExtension extension : extensions()) {
            deleteIfExists(extensionEntityFile(zoneId, extension.id()));
        }
        deleteIfExists(tempFile(nbtFile(zoneId)));
        deleteIfExists(tempFile(schematicFile(zoneId)));
    }

    private static boolean saveInternal(ServerLevel level, PlaygroundZone zone, Path file) {
        try {
            StructureTemplate template = new StructureTemplate();
            template.fillFromWorld(level, zone.getMinBlockPos(), zone.getBlockSize(), false, null);
            CompoundTag tag = template.save(new CompoundTag());
            NbtIo.writeCompressed(tag, file.toFile());
            return true;
        } catch (Exception exception) {
            MiniGamePlaygroundMod.LOGGER.error("Failed to write built-in snapshot for zone '{}'.", zone.getId(), exception);
            return false;
        }
    }

    private static boolean restoreInternal(ServerLevel level, PlaygroundZone zone, Path file) {
        try {
            CompoundTag tag = NbtIo.readCompressed(file.toFile());
            StructureTemplate template = new StructureTemplate();
            template.load(level.holderLookup(Registries.BLOCK), tag);

            StructurePlaceSettings settings = new StructurePlaceSettings()
                    .setIgnoreEntities(true)
                    .setKeepLiquids(false)
                    .setKnownShape(false)
                    .setFinalizeEntities(false)
                    .setRandom(level.getRandom());

            template.placeInWorld(level, zone.getMinBlockPos(), zone.getMinBlockPos(),
                    settings, level.getRandom(), Block.UPDATE_ALL);
            return true;
        } catch (Exception exception) {
            MiniGamePlaygroundMod.LOGGER.error("Failed to restore built-in snapshot for zone '{}'.", zone.getId(), exception);
            return false;
        }
    }

    private static boolean invokeWorldEditSave(ServerLevel level, PlaygroundZone zone, Path file) {
        return invokeWorldEdit("save", level, zone, file);
    }

    private static boolean invokeWorldEditRestore(ServerLevel level, PlaygroundZone zone, Path file) {
        return invokeWorldEdit("restore", level, zone, file);
    }

    private static boolean invokeWorldEdit(String methodName, ServerLevel level, PlaygroundZone zone, Path file) {
        try {
            Class<?> bridge = Class.forName("com.minigameplayground.compat.WorldEditBridge", true,
                    ZoneSnapshots.class.getClassLoader());
            Method method = bridge.getMethod(methodName, ServerLevel.class, PlaygroundZone.class, Path.class);
            Object result = method.invoke(null, level, zone, file);
            return result instanceof Boolean value && value;
        } catch (InvocationTargetException exception) {
            Throwable cause = exception.getCause() == null ? exception : exception.getCause();
            MiniGamePlaygroundMod.LOGGER.warn("WorldEdit {} failed for zone '{}': {}",
                    methodName, zone.getId(), cause.toString());
        } catch (Throwable throwable) {
            MiniGamePlaygroundMod.LOGGER.warn("WorldEdit {} unavailable for zone '{}': {}",
                    methodName, zone.getId(), throwable.toString());
        }
        return false;
    }

    private static boolean move(Path source, Path target) {
        try {
            try {
                Files.move(source, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
            }
            return true;
        } catch (IOException exception) {
            MiniGamePlaygroundMod.LOGGER.error("Failed to move snapshot file {} to {}.", source, target, exception);
            return false;
        }
    }

    private static void writeMeta(String zoneId, ServerLevel level, String format) {
        SnapshotMeta meta = new SnapshotMeta();
        meta.dimension = level.dimension().location().toString();
        meta.format = format;
        meta.createdAt = System.currentTimeMillis();
        try {
            Path metaFile = metaFile(zoneId);
            Files.writeString(metaFile, GSON.toJson(meta), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
        } catch (IOException exception) {
            MiniGamePlaygroundMod.LOGGER.warn("Failed to write snapshot metadata for zone '{}'.", zoneId, exception);
        }
    }

    private static SnapshotMeta readMeta(String zoneId) {
        Path metaFile = metaFile(zoneId);
        if (!Files.isRegularFile(metaFile)) {
            return null;
        }
        try {
            return GSON.fromJson(Files.readString(metaFile, StandardCharsets.UTF_8), SnapshotMeta.class);
        } catch (Exception exception) {
            MiniGamePlaygroundMod.LOGGER.warn("Failed to read snapshot metadata for zone '{}'.", zoneId, exception);
            return null;
        }
    }

    private static Path nbtFile(String zoneId) {
        return SNAPSHOT_DIR.resolve(baseName(zoneId) + ".nbt");
    }

    private static Path schematicFile(String zoneId) {
        return SNAPSHOT_DIR.resolve(baseName(zoneId) + ".schem");
    }

    private static Path metaFile(String zoneId) {
        return SNAPSHOT_DIR.resolve(baseName(zoneId) + ".meta.json");
    }

    private static Path baseEntityFile(String zoneId) {
        return SNAPSHOT_DIR.resolve(baseName(zoneId) + ".entities.nbt");
    }

    private static Path extensionEntityFile(String zoneId, String extensionId) {
        String safeExtension = extensionId.replaceAll("[^A-Za-z0-9._-]", "_");
        if (safeExtension.length() > 24) {
            safeExtension = safeExtension.substring(0, 24);
        }
        return SNAPSHOT_DIR.resolve(baseName(zoneId) + ".entities-" + safeExtension + ".nbt");
    }

    private static Path tempFile(Path target) {
        return target.resolveSibling(target.getFileName() + ".tmp");
    }

    private static String baseName(String zoneId) {
        String safe = zoneId.replaceAll("[^A-Za-z0-9._-]", "_");
        if (safe.length() > 40) {
            safe = safe.substring(0, 40);
        }
        return safe + "-" + UUID.nameUUIDFromBytes(zoneId.getBytes(StandardCharsets.UTF_8));
    }

    private static void deleteIfExists(Path file) {
        try {
            Files.deleteIfExists(file);
        } catch (IOException exception) {
            MiniGamePlaygroundMod.LOGGER.warn("Failed to delete snapshot file {}.", file, exception);
        }
    }

    private static final class SnapshotMeta {
        private String dimension;
        private String format;
        private long createdAt;
    }

    private static final class EntitySnapshotData {
        private final ListTag baseEntities;
        private final List<EntitySnapshotExtensionData> extensions;

        private EntitySnapshotData(ListTag baseEntities,
                                   List<EntitySnapshotExtensionData> extensions) {
            this.baseEntities = baseEntities;
            this.extensions = extensions;
        }
    }

    private static final class EntitySnapshotExtensionData {
        private final ZoneSnapshotExtension extension;
        private final ListTag entities;

        private EntitySnapshotExtensionData(ZoneSnapshotExtension extension, ListTag entities) {
            this.extension = extension;
            this.entities = entities;
        }
    }
}
