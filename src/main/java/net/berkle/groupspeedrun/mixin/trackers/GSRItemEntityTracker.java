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
        // 1. Safety Checks: Server-side only and active timer
        if (player.getEntityWorld().isClient() || GSRMain.CONFIG == null || GSRMain.CONFIG.isTimerFrozen) return;

        ItemEntity itemEntity = (ItemEntity) (Object) this;
        ItemStack stack = this.getStack();

        // 2. Target Item: Blaze Rods
        if (stack.isOf(Items.BLAZE_ROD) && !itemEntity.isRemoved()) {
            long lastKill = GSRStats.LAST_BLAZE_KILL_TIME.getOrDefault(player.getUuid(), 0L);
            long currentTime = player.getEntityWorld().getTime();

            // 600 ticks = 30 seconds
            if (lastKill > 0 && (currentTime - lastKill <= 600)) {
                // Use the helper method to flip the isDirty flag!
                GSRStats.addInt(GSRStats.POG_CHAMP_COUNT, player.getUuid(), stack.getCount());

                // Reset the kill time to 0 to prevent double-dipping on multiple stacks
                GSRStats.LAST_BLAZE_KILL_TIME.put(player.getUuid(), 0L);

                // Note: We use .put for LAST_BLAZE_KILL_TIME because it's a temporary
                // timestamp tracker, not a cumulative stat that needs to be persisted
                // across server restarts like the POG_CHAMP_COUNT.
            }
        }
    }
}