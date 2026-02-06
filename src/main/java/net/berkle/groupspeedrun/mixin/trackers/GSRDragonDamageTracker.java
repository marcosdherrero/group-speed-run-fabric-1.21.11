package net.berkle.groupspeedrun.mixin.trackers;

import net.berkle.groupspeedrun.GSRMain;
import net.berkle.groupspeedrun.GSRStats;
import net.minecraft.entity.boss.dragon.EnderDragonEntity;
import net.minecraft.entity.boss.dragon.EnderDragonPart;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Mixin for EnderDragonPart to attribute damage to specific players.
 * The dragon is made of multiple parts (head, wings, etc.); this captures hits on any of them.
 */
@Mixin(EnderDragonPart.class)
public abstract class GSRDragonDamageTracker {

    /**
     * Injects into the damage method of dragon parts to track player contributions.
     */
    @Inject(method = "damage", at = @At("TAIL"))
    private void onPartDamage(ServerWorld world, DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        // Only track damage if a run is currently active and not already finished
        if (GSRMain.CONFIG == null || GSRMain.CONFIG.wasVictorious || GSRMain.CONFIG.isFailed || GSRMain.CONFIG.startTime < 0) {
            return;
        }

        EnderDragonPart part = (EnderDragonPart) (Object) this;
        EnderDragonEntity dragon = part.owner;

        // Ensure the dragon exists and the attacker is a player
        if (dragon != null && source.getAttacker() instanceof ServerPlayerEntity player) {

            // Log the damage amount to the player's UUID in the global stats map
            // This is used by GSRRunHistoryManager to determine the 'Dragon Warrior' award.
            GSRStats.addFloat(GSRStats.DRAGON_DAMAGE_MAP, player.getUuid(), amount);

            // Optional: Log to console for debugging damage attribution
            // GSRMain.LOGGER.debug("[GSR] Player {} dealt {} damage to Dragon part", player.getName().getString(), amount);
        }
    }
}