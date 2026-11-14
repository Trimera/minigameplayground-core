package com.minigameplayground.mixin;

import com.minigameplayground.flight.ElytraController;
import com.minigameplayground.flight.FlightController;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Enforces the zone creative-flight policy after all other player-tick logic.
 *
 * <p>Equipment, potions, commands and contraptions may set {@code mayfly} earlier in
 * the tick. Running at TAIL makes the zone rule the final authority without needing to
 * know which mod granted the ability.</p>
 */
@Mixin(ServerPlayer.class)
public abstract class ServerPlayerMixin {
    @Inject(method = "tick", at = @At("HEAD"))
    private void minigameplayground$enforceFlightBeforeMovement(CallbackInfo ci) {
        FlightController.beforeTick((ServerPlayer) (Object) this);
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void minigameplayground$enforceFlightRules(CallbackInfo ci) {
        ServerPlayer player = (ServerPlayer) (Object) this;
        FlightController.tick(player);
        ElytraController.tick(player);
    }
}
