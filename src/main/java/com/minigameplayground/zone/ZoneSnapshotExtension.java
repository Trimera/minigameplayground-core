package com.minigameplayground.zone;

import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;

/**
 * Optional extension point for snapshotting entities owned by a compatibility addon.
 *
 * <p>The core handles ordinary entities. An addon can register one extension to
 * exclude its entities from the core list and provide their own NBT snapshot and
 * clear behavior. Restoring is done by the core from the returned entity NBT list.</p>
 */
public interface ZoneSnapshotExtension {
    String id();

    boolean handlesEntity(Entity entity);

    ListTag saveEntities(ServerLevel level, PlaygroundZone zone);

    void clearEntities(ServerLevel level, PlaygroundZone zone);
}
