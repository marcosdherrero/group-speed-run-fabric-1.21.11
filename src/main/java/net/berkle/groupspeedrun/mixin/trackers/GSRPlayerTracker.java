package net.berkle.groupspeedrun.mixin.trackers;

import net.berkle.groupspeedrun.GSRMain;
import net.berkle.groupspeedrun.GSRStats;
import net.berkle.groupspeedrun.GSRSplitManager;
import net.berkle.groupspeedrun.GSREvents;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.World;
import net.minecraft.world.gen.structure.StructureKeys;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerPlayerEntity.class)
public abstract class GSRPlayerTracker {

    @Shadow @Final public MinecraftServer server;

    /**
     * TICK LOGIC: Checks for structure entry (Fortress) for splits.
     */
    @Inject(method = "tick", at = @At("HEAD"))
    private void groupspeedrun$checkFortress(CallbackInfo ci) {
        ServerPlayerEntity player = (ServerPlayerEntity) (Object) this;

        // Optimization: Only check every 2 seconds (40 ticks)
        if (player.age % 40 == 0 && GSRMain.CONFIG != null && GSRMain.CONFIG.timeFortress <= 0) {
            if (player.getEntityWorld() instanceof ServerWorld world && world.getRegistryKey() == World.NETHER) {

                var structureRegistry = world.getRegistryManager().getOrThrow(RegistryKeys.STRUCTURE);
                var fortressEntry = structureRegistry.getOptional(StructureKeys.FORTRESS);

                if (fortressEntry.isPresent()) {
                    // Check if player is inside the structure bounding box
                    if (world.getStructureAccessor()
                            .getStructureAt(player.getBlockPos(), fortressEntry.get().value())
                            .hasChildren()) {

                        GSRSplitManager.completeSplit(this.server, "fortress");
                    }
                }
            }
        }
    }

    /**
     * STATS: Track inventory opens for post-run statistics.
     */
    @Inject(method = "onScreenHandlerOpened", at = @At("HEAD"))
    private void onOpen(ScreenHandler handler, CallbackInfo ci) {
        if (GSRMain.CONFIG == null || GSRMain.CONFIG.isTimerFrozen) return;

        ServerPlayerEntity player = (ServerPlayerEntity) (Object) this;
        // Ignore the standard inventory screen
        if (!(handler instanceof net.minecraft.screen.PlayerScreenHandler)) {
            GSRStats.INVENTORIES_OPENED.merge(player.getUuid(), 1, Integer::sum);
        }
    }

    /**
     * FAIL CONDITION: Triggers Group Death logic when a player dies.
     */
    @Inject(method = "onDeath", at = @At("HEAD"))
    private void onPlayerDeath(DamageSource source, CallbackInfo ci) {
        ServerPlayerEntity player = (ServerPlayerEntity) (Object) this;

        // Pass the death event to our central logic engine
        if (this.server != null) {
            GSREvents.handlePlayerDeath(player, this.server);
        }
    }
}