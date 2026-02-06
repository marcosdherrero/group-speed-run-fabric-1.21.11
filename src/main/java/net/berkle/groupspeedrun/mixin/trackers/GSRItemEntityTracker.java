package net.berkle.groupspeedrun.mixin.trackers;

import net.berkle.groupspeedrun.GSRStats;
import net.berkle.groupspeedrun.GSRMain;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemEntity.class)
public abstract class GSRItemEntityTracker {
    @Shadow public abstract ItemStack getStack();

    /**
     * Stats Tracking: Detects when a player picks up a Blaze Rod.
     * If picked up within 30 seconds of a Blaze kill, it increments the "Pog Champ" stat.
     */
    @Inject(method = "onPlayerCollision", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/player/PlayerInventory;insertStack(Lnet/minecraft/item/ItemStack;)Z"))
    private void onPickup(PlayerEntity player, CallbackInfo ci) {
        // Ensure logic only runs on the Server side and when the timer is active
        if (player.getEntityWorld().isClient() || GSRMain.CONFIG == null || GSRMain.CONFIG.isTimerFrozen) return;

        ItemStack stack = this.getStack();

        if (stack.isOf(Items.BLAZE_ROD)) {
            long lastKill = GSRStats.LAST_BLAZE_KILL_TIME.getOrDefault(player.getUuid(), 0L);
            long currentTime = player.getEntityWorld().getTime();

            // 600 ticks = 30 seconds
            if (currentTime - lastKill <= 600) {
                GSRStats.POG_CHAMP_COUNT.merge(player.getUuid(), stack.getCount(), Integer::sum);

                // Reset to 0 so a single kill doesn't count for multiple separate rod pickups
                // (e.g., if the rods were dropped in separate stacks)
                GSRStats.LAST_BLAZE_KILL_TIME.put(player.getUuid(), 0L);
            }
        }
    }
}