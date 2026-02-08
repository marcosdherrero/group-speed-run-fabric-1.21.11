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
     * Stats Tracking: Detects item pickups for Ender Pearls and Blaze Rods.
     */
    @Inject(method = "onPlayerCollision", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/player/PlayerInventory;insertStack(Lnet/minecraft/item/ItemStack;)Z"))
    private void onPickup(PlayerEntity player, CallbackInfo ci) {
        // 1. Safety Checks: Server-side only and active timer
        if (player.getEntityWorld().isClient() || GSRMain.CONFIG == null || GSRMain.CONFIG.startTime == -1 || GSRMain.CONFIG.isTimerFrozen) return;

        ItemEntity itemEntity = (ItemEntity) (Object) this;
        ItemStack stack = this.getStack();
        if (stack.isEmpty() || itemEntity.isRemoved()) return;

        // --- [ TRACKER: ENDER PEARLS ] ---
        if (stack.isOf(Items.ENDER_PEARL)) {
            GSRStats.addInt(GSRStats.ENDER_PEARLS_COLLECTED, player.getUuid(), stack.getCount());
        }

        // --- [ TRACKER: BLAZE RODS (POG CHAMP) ] ---
        if (stack.isOf(Items.BLAZE_ROD)) {
            long lastKill = GSRStats.LAST_BLAZE_KILL_TIME.getOrDefault(player.getUuid(), 0L);
            long currentTime = player.getEntityWorld().getTime();

            // 600 ticks = 30 seconds
            if (lastKill > 0 && (currentTime - lastKill <= 600)) {
                GSRStats.addInt(GSRStats.POG_CHAMP_COUNT, player.getUuid(), stack.getCount());

                // Reset kill time to prevent multiple stack pickups from counting twice
                GSRStats.LAST_BLAZE_KILL_TIME.put(player.getUuid(), 0L);
            }
        }
    }
}