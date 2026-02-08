package net.berkle.groupspeedrun.mixin.trackers;

import net.berkle.groupspeedrun.GSRMain;
import net.berkle.groupspeedrun.managers.GSRRunHistoryManager;
import net.berkle.groupspeedrun.managers.GSRSplitManager;
import net.minecraft.entity.boss.dragon.EnderDragonEntity;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EnderDragonEntity.class)
public abstract class GSRDragonDeathTracker {

    /**
     * updatePostDeath is called every tick once the Dragon's health reaches zero.
     * This Mixin captures the exact moment the victory occurs.
     */
    @Inject(method = "updatePostDeath", at = @At("HEAD"))
    private void groupspeedrun$onDragonDeathAnimation(CallbackInfo ci) {
        EnderDragonEntity dragon = (EnderDragonEntity) (Object) this;

        if (dragon.getEntityWorld().isClient() || GSRMain.CONFIG == null) return;

        // dragon.deathTime increments every tick of the animation starting at 0.
        // Checking deathTime == 0 ensures this code runs EXACTLY once on the first frame.
        if (dragon.deathTime == 0 && !GSRMain.CONFIG.isVictorious && !GSRMain.CONFIG.isFailed) {
            MinecraftServer server = dragon.getEntityWorld().getServer();
            if (server != null) {
                triggerVictory(server);
            }
        }
    }

    private void triggerVictory(MinecraftServer server) {
        // 1. Trigger the Split Logic
        // This handles the Chat Broadcast, Sound, and pinning the timeDragon variable.
        GSRSplitManager.completeSplit(server, "Dragon");

        // 2. Additional Run Persistence
        // Log the final success state into the JSON run history.
        GSRRunHistoryManager.saveRun(server, "SUCCESS", "The Group", "Ender Dragon Defeated");

        // 3. Log to Console for debugging
        GSRMain.LOGGER.info("[GSR] Victory condition met! Timer finalized and broadcast sent.");
    }
}