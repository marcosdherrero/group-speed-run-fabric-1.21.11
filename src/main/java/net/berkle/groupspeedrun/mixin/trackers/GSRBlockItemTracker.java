package net.berkle.groupspeedrun.mixin.trackers;

import net.berkle.groupspeedrun.GSRStats;
import net.berkle.groupspeedrun.GSRMain;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.util.ActionResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockItem.class)
public abstract class GSRBlockItemTracker {

    /**
     * Stats Tracking: Increments the "Blocks Placed" count when a player successfully
     * places a block in the world.
     */
    @Inject(method = "place(Lnet/minecraft/item/ItemPlacementContext;)Lnet/minecraft/util/ActionResult;", at = @At("RETURN"))
    private void onBlockPlace(ItemPlacementContext context, CallbackInfoReturnable<ActionResult> cir) {
        // Safety check for timer state
        if (GSRMain.CONFIG == null || GSRMain.CONFIG.isTimerFrozen) return;

        // isAccepted() covers SUCCESS, CONSUME, and SUCCESS_NO_ITEM_USAGE
        if (context.getWorld() != null && !context.getWorld().isClient() && cir.getReturnValue().isAccepted()) {
            if (context.getPlayer() != null) {
                GSRStats.BLOCKS_PLACED.merge(context.getPlayer().getUuid(), 1, Integer::sum);
            }
        }
    }
}