package net.berkle.groupspeedrun.mixin.trackers;

import net.berkle.groupspeedrun.GSRMain;
import net.berkle.groupspeedrun.GSRSplitManager;
import net.minecraft.advancement.AdvancementEntry;
import net.minecraft.advancement.PlayerAdvancementTracker;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * GSRSplitTracker intercepts advancement progress on the server side.
 * When a player completes a specific milestone advancement, it informs the
 * GSRSplitManager to record the time and trigger the HUD pop-up.
 */
@Mixin(PlayerAdvancementTracker.class)
public abstract class GSRSplitTracker {

    // The player associated with this specific advancement tracker instance
    @Shadow private ServerPlayerEntity owner;

    /**
     * ADVANCEMENT TRACKING: Intercepts the moment a player earns a specific criterion.
     * * We use @At("TAIL") to ensure that the criterion has already been successfully
     * added to the player's progress before we check if the entire advancement is finished.
     * * @param advancement The advancement being updated (e.g., "Enter the Nether")
     * @param criterionName The specific requirement met (e.g., "entered_nether")
     * @param cir The callback info containing the return value (Boolean: was it granted?)
     */
    @Inject(method = "grantCriterion", at = @At("TAIL"))
    private void onGrantCriterion(AdvancementEntry advancement, String criterionName, CallbackInfoReturnable<Boolean> cir) {

        // 1. Check if the criterion was actually granted (not already owned).
        // 2. Ensure the config exists.
        // 3. Ensure the timer isn't frozen/paused; splits shouldn't count if the run is stopped.
        if (!cir.getReturnValue() || GSRMain.CONFIG == null || GSRMain.CONFIG.isTimerFrozen) return;

        // Retrieve the server instance from the player's world
        MinecraftServer server = this.owner.getEntityWorld().getServer();
        if (server == null) return;

        /**
         * Check if the full advancement is complete.
         * Many advancements have multiple criteria (like "A Seedy Place" or "How Did We Get Here?").
         * We only want to trigger the split when the very last requirement is met.
         */
        if (this.owner.getAdvancementTracker().getProgress(advancement).isDone()) {

            // Extract the path string (e.g., "story/enter_the_nether") from the ResourceLocation/Identifier
            String path = advancement.id().getPath();

            /**
             * Trigger the Split Logic.
             * This call to completeSplit will:
             * 1. Record the current world time in the config.
             * 2. Set 'GSRMain.CONFIG.lastSplitTime', which triggers the 10s HUD pop-up.
             * 3. Send a message to all players.
             */
            switch (path) {
                case "story/enter_the_nether" -> GSRSplitManager.completeSplit(server, "nether");
                case "nether/find_bastion"    -> GSRSplitManager.completeSplit(server, "bastion");
                case "nether/find_fortress"   -> GSRSplitManager.completeSplit(server, "fortress");
                case "story/enter_the_end"    -> GSRSplitManager.completeSplit(server, "end");
            }
        }
    }
}