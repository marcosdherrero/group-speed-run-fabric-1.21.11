package net.berkle.groupspeedrun.mixin.trackers;

import net.berkle.groupspeedrun.GSRMain;
import net.berkle.groupspeedrun.GSREvents;
import net.berkle.groupspeedrun.GSRRunHistoryManager;
import net.minecraft.entity.boss.dragon.EnderDragonEntity;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EnderDragonEntity.class)
public abstract class GSRDragonDeathTracker {

    /**
     * updatePostDeath is called every tick once health <= 0.
     * We trigger victory on the very first tick of this animation.
     */
    @Inject(method = "updatePostDeath", at = @At("HEAD"))
    private void groupspeedrun$onDragonDeathAnimation(CallbackInfo ci) {
        EnderDragonEntity dragon = (EnderDragonEntity) (Object) this;

        // Ensure we are on the server
        if (dragon.getEntityWorld().isClient() || GSRMain.CONFIG == null) return;

        // CRITICAL: Only trigger if health is 0 AND it hasn't been triggered yet
        if (dragon.getHealth() <= 0.0f && !GSRMain.CONFIG.wasVictorious && !GSRMain.CONFIG.isFailed) {
            triggerVictory((ServerWorld) dragon.getEntityWorld());
        }
    }

    private void triggerVictory(ServerWorld world) {
        var config = GSRMain.CONFIG;
        long currentWorldTime = world.getTime();

        // 1. PIN THE CLOCK
        config.wasVictorious = true;
        config.isTimerFrozen = true;
        config.frozenTime = currentWorldTime; // This is what GSREvents uses to stop the clock

        // 2. CALCULATE FINAL TIME
        config.timeDragon = GSREvents.getRunTicks(world.getServer());
        config.victoryTimer = 200;

        // 3. PERSIST
        GSRRunHistoryManager.saveRun(world.getServer(), "SUCCESS", "", "");
        GSRMain.saveAndSync(world.getServer());

        GSRMain.LOGGER.info("[GSR] VICTORY! Timer stopped at: " + config.timeDragon);
    }
}