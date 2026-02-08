package net.berkle.groupspeedrun.mixin.trackers;

import net.berkle.groupspeedrun.GSRMain;
import net.berkle.groupspeedrun.GSRStats;
import net.berkle.groupspeedrun.managers.GSRSplitManager;
import net.berkle.groupspeedrun.managers.GSRBroadcastManager;
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
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Main tracker for player-specific events.
 * Handles distance tracking, structure detection, and custom death broadcasts.
 */
@Mixin(ServerPlayerEntity.class)
public abstract class GSRServerPlayerEntityTracker {

    @Shadow @Final public MinecraftServer server;

    @Unique private double gsrPrevX;
    @Unique private double gsrPrevY;
    @Unique private double gsrPrevZ;
    @Unique private boolean gsrInitialized = false;

    /**
     * Ticks every frame for every player.
     * Manages distance traveled stats and Nether structure detection.
     */
    @Inject(method = "tick", at = @At("TAIL"))
    private void groupspeedrun$onPlayerTick(CallbackInfo ci) {
        ServerPlayerEntity player = (ServerPlayerEntity) (Object) this;
        var config = GSRMain.CONFIG;

        double currentX = player.getX();
        double currentY = player.getY();
        double currentZ = player.getZ();

        // 1. STATS: Distance Tracking (Only while run is active)
        if (config != null && config.startTime > 0 && !config.isTimerFrozen && !config.isFailed && !config.isVictorious) {
            if (!gsrInitialized) {
                gsrPrevX = currentX; gsrPrevY = currentY; gsrPrevZ = currentZ;
                gsrInitialized = true;
            } else {
                double dx = currentX - gsrPrevX;
                double dy = currentY - gsrPrevY;
                double dz = currentZ - gsrPrevZ;
                double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);

                // Filter out jitter/teleports (Movement < 10 blocks per tick)
                if (dist > 0.01 && dist < 10.0) {
                    GSRStats.addFloat(GSRStats.DISTANCE_MOVED, player.getUuid(), (float) dist);
                }
                gsrPrevX = currentX; gsrPrevY = currentY; gsrPrevZ = currentZ;
            }
        }

        // 2. LOGIC: Stop detection if the run is over or paused
        if (config == null || config.isTimerFrozen || config.startTime <= 0 || config.isFailed || config.isVictorious) return;

        // 3. STRUCTURES: Nether Split Detection (Fortress/Bastion)
        if (player.getEntityWorld() instanceof ServerWorld world && world.getRegistryKey() == World.NETHER) {
            var structureRegistry = world.getRegistryManager().getOrThrow(RegistryKeys.STRUCTURE);

            // Fortress Split
            if (config.timeFortress <= 0) {
                structureRegistry.getOptional(StructureKeys.FORTRESS).ifPresent(entry -> {
                    if (world.getStructureAccessor().getStructureAt(player.getBlockPos(), entry.value()).hasChildren()) {
                        GSRSplitManager.completeSplit(this.server, "fortress");
                    }
                });
            }

            // Bastion Split
            if (config.timeBastion <= 0) {
                structureRegistry.getOptional(StructureKeys.BASTION_REMNANT).ifPresent(entry -> {
                    if (world.getStructureAccessor().getStructureAt(player.getBlockPos(), entry.value()).hasChildren()) {
                        GSRSplitManager.completeSplit(this.server, "bastion");
                    }
                });
            }
        }
    }

    /**
     * STATS: Increments count when opening chests, furnaces, etc.
     */
    @Inject(method = "onScreenHandlerOpened", at = @At("HEAD"))
    private void onOpen(ScreenHandler handler, CallbackInfo ci) {
        if (GSRMain.CONFIG == null || GSRMain.CONFIG.isTimerFrozen || GSRMain.CONFIG.isFailed || GSRMain.CONFIG.isVictorious) return;
        ServerPlayerEntity player = (ServerPlayerEntity) (Object) this;

        // Don't count opening personal inventory (E)
        if (!(handler instanceof net.minecraft.screen.PlayerScreenHandler)) {
            GSRStats.addInt(GSRStats.INVENTORIES_OPENED, player.getUuid(), 1);
        }
    }

    /**
     * CUSTOM DEATH LOGIC:
     * Intercepts death to broadcast "Disgrace" failure and SILENCE vanilla death messages.
     */
    @Inject(method = "onDeath", at = @At("HEAD"), cancellable = true)
    private void onPlayerDeath(DamageSource source, CallbackInfo ci) {
        ServerPlayerEntity player = (ServerPlayerEntity) (Object) this;
        var config = GSRMain.CONFIG;

        // Only trigger if a run is actually happening and group death is enabled
        if (config != null && config.startTime != -1 && config.groupDeathEnabled && !config.isFailed && !config.isVictorious) {

            // 1. Mark failure state immediately
            config.isFailed = true;

            // 2. Broadcast custom failure message
            GSRBroadcastManager.broadcastFailure(
                    this.server,
                    config.getElapsedTime(),
                    player.getName().getString(),
                    source.getDeathMessage(player).getString(),
                    null
            );

            // 3. Save world and sync config to all clients
            GSRMain.saveAndSync(this.server);

            // 4. CANCEL: Stops vanilla logic from sending its own death message packet
            ci.cancel();
        }
    }
}