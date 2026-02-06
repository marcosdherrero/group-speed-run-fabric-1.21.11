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
        // Only count if the block was successfully broken (logic returned true)
        if (cir.getReturnValue() && player != null) {

            // Check global config and timer state via the new GSRMain
            if (GSRMain.CONFIG != null && !GSRMain.CONFIG.isTimerFrozen) {
                GSRStats.BLOCKS_BROKEN.merge(player.getUuid(), 1, Integer::sum);
            }
        }
    }
}