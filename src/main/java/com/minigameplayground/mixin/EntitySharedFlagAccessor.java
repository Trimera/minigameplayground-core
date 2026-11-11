package com.minigameplayground.mixin;

import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Accessor for the shared entity flag used by elytra gliding.
 */
@Mixin(Entity.class)
public interface EntitySharedFlagAccessor {
    @Invoker("setSharedFlag")
    void minigameplayground$setSharedFlag(int index, boolean value);
}
