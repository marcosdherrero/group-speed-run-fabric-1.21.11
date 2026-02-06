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

@Mixin(LivingEntity.class)
public abstract class GSRLivingEntityTracker {

    /**
     * Tracks damage dealt to non-part entities (like the Dragon's center or other mobs).
     */
    @Inject(method = "applyDamage", at = @At("TAIL"))
    private void trackGeneralDamage(ServerWorld world, DamageSource source, float amount, CallbackInfo ci) {
        LivingEntity target = (LivingEntity) (Object) this;

        if (target instanceof EnderDragonEntity && source.getAttacker() instanceof ServerPlayerEntity player) {
            GSRStats.addFloat(GSRStats.DRAGON_DAMAGE_MAP, player.getUuid(), amount);
        }
    }

    /**
     * Tracks natural and item-based healing for the "Healer" award.
     */
    @Inject(method = "heal", at = @At("HEAD"))
    private void trackHealing(float amount, CallbackInfo ci) {
        if ((Object) this instanceof ServerPlayerEntity player) {
            if (GSRMain.CONFIG != null && GSRMain.CONFIG.startTime > 0) {
                GSRStats.addFloat(GSRStats.DAMAGE_HEALED, player.getUuid(), amount);
            }
        }
    }

    /**
     * Updates armor stats every second for the "Defender" award.
     */
    @Inject(method = "tick", at = @At("HEAD"))
    private void trackMaxArmor(CallbackInfo ci) {
        if ((Object) this instanceof ServerPlayerEntity player) {
            if (player.getEntityWorld().getTime() % 20 == 0) {
                int currentArmor = player.getArmor();
                if (currentArmor > 0) GSRStats.updateMaxArmor(player.getUuid(), currentArmor);
            }
        }
    }
}