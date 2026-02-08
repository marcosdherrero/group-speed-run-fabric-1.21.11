package net.berkle.groupspeedrun.mixin.trackers;

import net.berkle.groupspeedrun.GSRMain;
import net.berkle.groupspeedrun.GSRStats;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.network.ServerPlayerInteractionManager;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerPlayerInteractionManager.class)
public class GSRServerPlayerInterTracker {

    @Shadow @Final protected ServerPlayerEntity player;

    /**
     * STATS TRACKING: Increments "Blocks Broken" after a successful block break.
     */
    @Inject(method = "tryBreakBlock", at = @At("RETURN"))
    private void afterBlockBroken(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        // 1. Success Check: Only count if the block was actually broken
        if (!cir.getReturnValue() || player == null) return;

        // 2. State Check: Only track if the run is active and not paused
        if (GSRMain.CONFIG == null || GSRMain.CONFIG.isTimerFrozen || GSRMain.CONFIG.startTime < 0) {
            return;
        }

        // 3. Persistence: Use the helper to mark the stats as 'dirty' for the next save cycle
        GSRStats.addInt(GSRStats.BLOCKS_BROKEN, player.getUuid(), 1);
    }
}