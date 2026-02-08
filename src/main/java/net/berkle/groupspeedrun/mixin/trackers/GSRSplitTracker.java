package net.berkle.groupspeedrun.mixin.trackers;

import net.berkle.groupspeedrun.GSRMain;
import net.berkle.groupspeedrun.managers.GSRSplitManager;
import net.minecraft.advancement.AdvancementEntry;
import net.minecraft.advancement.PlayerAdvancementTracker;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerAdvancementTracker.class)
public abstract class GSRSplitTracker {

    @Shadow private ServerPlayerEntity owner;

    /**
     * ADVANCEMENT TRACKING: Intercepts the moment a player earns a split-defining advancement.
     */
    @Inject(method = "grantCriterion", at = @At("TAIL"))
    private void onGrantCriterion(AdvancementEntry advancement, String criterionName, CallbackInfoReturnable<Boolean> cir) {
        // 1. Safety Check: If the grant failed, or config is missing, or timer is paused, stop.
        if (!cir.getReturnValue() || GSRMain.CONFIG == null || GSRMain.CONFIG.isTimerFrozen) return;

        MinecraftServer server = this.owner.getEntityWorld().getServer(); // Direct access to server
        if (server == null) return;

        // 2. Completion Check: Ensure the player actually finished the whole advancement
        if (this.owner.getAdvancementTracker().getProgress(advancement).isDone()) {
            String path = advancement.id().getPath();

            // 3. Split Logic Assignment
            switch (path) {
                case "story/enter_the_nether" -> GSRSplitManager.completeSplit(server, "nether");
                case "story/enter_the_end"    -> GSRSplitManager.completeSplit(server, "end");

                /* * NOTE: Bastion and Fortress are better handled in GSRPlayerTracker
                 * via structure bounding-box checks to ensure the split triggers
                 * the exact second they step foot inside, rather than waiting for
                 * the advancement toast to appear.
                 */
            }
        }
    }
}