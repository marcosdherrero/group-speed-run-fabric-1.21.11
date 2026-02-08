package net.berkle.groupspeedrun.mixin.trackers;

import net.berkle.groupspeedrun.GSRMain;
import net.berkle.groupspeedrun.GSRStats;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.boss.dragon.EnderDragonEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Tracks health, damage, and armor changes for all LivingEntities.
 * This is the primary data source for the "Healer", "Tank", "ADC", and "Dragon Warrior" awards.
 */
@Mixin(LivingEntity.class)
public abstract class GSRLivingEntityTracker {

    /**
     * STATS: Tracks all damage dealt and received.
     * We use @At("TAIL") to ensure the damage was actually applied.
     */
    @Inject(method = "applyDamage", at = @At("TAIL"))
    private void trackAllDamage(ServerWorld world, DamageSource source, float amount, CallbackInfo ci) {
        // State Check: Only track if a run is active and not paused
        if (GSRMain.CONFIG == null || GSRMain.CONFIG.startTime <= 0 || GSRMain.CONFIG.isTimerFrozen) return;

        LivingEntity target = (LivingEntity) (Object) this;

        // 1. TRACK DAMAGE TAKEN (For "Tank" and "Coward" awards)
        if (target instanceof ServerPlayerEntity player) {
            GSRStats.addFloat(GSRStats.TOTAL_DAMAGE_TAKEN, player.getUuid(), amount);
        }

        // 2. TRACK DAMAGE DEALT (For "ADC" and "Weakling" awards)
        if (source.getAttacker() instanceof ServerPlayerEntity attacker) {
            GSRStats.addFloat(GSRStats.TOTAL_DAMAGE_DEALT, attacker.getUuid(), amount);

            // 3. TRACK DRAGON DAMAGE (For "Dragon Warrior" priority award)
            if (target instanceof EnderDragonEntity) {
                GSRStats.addFloat(GSRStats.DRAGON_DAMAGE_MAP, attacker.getUuid(), amount);
            }
        }
    }

    /**
     * STATS: Tracks successful healing for the "Healer" award.
     * Injected at HEAD to compare health before the heal occurs.
     */
    @Inject(method = "heal", at = @At("HEAD"))
    private void trackHealing(float amount, CallbackInfo ci) {
        if ((Object) this instanceof ServerPlayerEntity player) {
            // State Check
            if (GSRMain.CONFIG != null && GSRMain.CONFIG.startTime > 0 && !GSRMain.CONFIG.isTimerFrozen) {
                float currentHealth = player.getHealth();
                float maxHealth = player.getMaxHealth();

                // Only count healing if the player was actually injured
                if (currentHealth < maxHealth) {
                    // Logic: If player has 19HP and heals 5, only record 1HP of "actual" healing
                    float actualHeal = Math.min(amount, maxHealth - currentHealth);
                    GSRStats.addFloat(GSRStats.DAMAGE_HEALED, player.getUuid(), actualHeal);
                }
            }
        }
    }

    /**
     * STATS: Monitors armor rating for the "Defender" award.
     * Runs during the entity tick but optimized to check once per second.
     */
    @Inject(method = "tick", at = @At("HEAD"))
    private void trackMaxArmor(CallbackInfo ci) {
        if ((Object) this instanceof ServerPlayerEntity player) {
            // Optimization: check every 20 ticks (1 second) to save CPU
            if (player.getEntityWorld().getTime() % 20 == 0) {
                if (GSRMain.CONFIG != null && GSRMain.CONFIG.startTime > 0 && !GSRMain.CONFIG.isTimerFrozen) {
                    int currentArmor = player.getArmor();

                    // Only update if they are actually wearing armor
                    if (currentArmor > 0) {
                        GSRStats.updateMaxArmor(player.getUuid(), currentArmor);
                    }
                }
            }
        }
    }
}