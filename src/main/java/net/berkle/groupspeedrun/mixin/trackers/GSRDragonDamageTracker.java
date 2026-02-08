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

@Mixin(EnderDragonPart.class)
public abstract class GSRDragonDamageTracker {

    @Inject(method = "damage", at = @At("TAIL"))
    private void onPartDamage(ServerWorld world, DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        // 1. Success Check: Only count if the damage was actually applied (returned true)
        if (!cir.getReturnValue()) return;

        // 2. State Check: Is the run active?
        if (GSRMain.CONFIG == null || GSRMain.CONFIG.isTimerFrozen || GSRMain.CONFIG.startTime < 0) {
            return;
        }

        // 3. Attribution: Was it a player?
        if (source.getAttacker() instanceof ServerPlayerEntity player) {
            // Note: EnderDragonPart.damage() logic handles passing the amount to the parent.
            // We capture the raw 'amount' passed to this specific part.
            GSRStats.addFloat(GSRStats.DRAGON_DAMAGE_MAP, player.getUuid(), amount);
        }
    }
}