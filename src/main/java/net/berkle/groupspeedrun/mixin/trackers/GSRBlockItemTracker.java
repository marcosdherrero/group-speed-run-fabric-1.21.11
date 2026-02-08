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
        // Use the centralized helper!
        // This ensures 'isDirty' becomes true and the stats actually save to disk.
        if (context.getPlayer() != null && cir.getReturnValue().isAccepted()) {
            if (!context.getWorld().isClient()) {
                GSRStats.addInt(GSRStats.BLOCKS_PLACED, context.getPlayer().getUuid(), 1);
            }
        }
    }
}