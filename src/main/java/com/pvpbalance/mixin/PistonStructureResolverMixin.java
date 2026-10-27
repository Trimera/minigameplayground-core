package com.pvpbalance.mixin;

import com.pvpbalance.zone.ZoneManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/**
 * Prevents vanilla pistons from pushing blocks out of a PVP zone.
 *
 * <p>Only the individual source block that would cross the boundary is swallowed;
 * other blocks in the same push continue to move. The swallowed block is removed
 * without drops and is therefore restored by {@code /pvb reset}. Removing it from
 * the resolver's push list also stops the piston from trying to move it later in
 * the same tick.</p>
 */
@Mixin(net.minecraft.world.level.block.piston.PistonStructureResolver.class)
public abstract class PistonStructureResolverMixin {
    @Shadow
    @Final
    private Level level;

    @Shadow
    public abstract List<BlockPos> getToPush();

    @Shadow
    public abstract Direction getPushDirection();

    @Inject(method = "resolve", at = @At("RETURN"))
    private void pvpbalance$swallowBlocksCrossingZoneBoundary(CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValueZ() || this.level == null || this.level.isClientSide()) {
            return;
        }

        Direction direction = this.getPushDirection();
        if (direction == null) {
            return;
        }

        List<BlockPos> toPush = this.getToPush();
        for (BlockPos source : List.copyOf(toPush)) {
            BlockPos destination = source.relative(direction);
            if (!ZoneManager.isInAnyPvpZone(this.level, source)
                    || ZoneManager.isInAnyPvpZone(this.level, destination)) {
                continue;
            }

            // No dropResources call here: the source block is deliberately swallowed.
            // Only update clients; avoid re-entering piston neighbour checks while the
            // resolver's result is being consumed by moveBlocks.
            this.level.setBlock(source, Blocks.AIR.defaultBlockState(), 2);
            toPush.remove(source);
        }
    }
}
