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
 *
 * <p>The optional {@link #afterRestore} hook runs after block and entity restoration.
 * Compatibility code can use it to rebuild non-NBT runtime state, such as a kinetic
 * network, without adding a dependency to the core.</p>
 */
public interface ZoneSnapshotExtension {
    String id();

    boolean handlesEntity(Entity entity);

    ListTag saveEntities(ServerLevel level, PlaygroundZone zone);

    void clearEntities(ServerLevel level, PlaygroundZone zone);

    /**
     * Called after a successful block/entity restore, once the restore context has ended.
     * Implementations must not throw; the core also guards this call.
     */
    default void afterRestore(ServerLevel level, PlaygroundZone zone) {
    }
}
