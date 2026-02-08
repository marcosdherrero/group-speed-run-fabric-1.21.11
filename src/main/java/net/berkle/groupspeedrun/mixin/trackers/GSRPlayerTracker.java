package net.berkle.groupspeedrun.mixin.trackers;

import net.berkle.groupspeedrun.GSRMain;
import net.berkle.groupspeedrun.GSRStats;
import net.berkle.groupspeedrun.managers.GSRSplitManager;
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
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerPlayerEntity.class)
public abstract class GSRPlayerTracker {

    @Shadow @Final public MinecraftServer server;

    @Unique private double gsrPrevX;
    @Unique private double gsrPrevY;
    @Unique private double gsrPrevZ;
    @Unique private boolean gsrInitialized = false;

    @Inject(method = "tick", at = @At("TAIL"))
    private void groupspeedrun$onPlayerTick(CallbackInfo ci) {
        ServerPlayerEntity player = (ServerPlayerEntity) (Object) this;

        // 1. POSITION CAPTURE
        double currentX = player.getX();
        double currentY = player.getY();
        double currentZ = player.getZ();

        // 2. DISTANCE TRACKING
        if (!gsrInitialized) {
            gsrPrevX = currentX;
            gsrPrevY = currentY;
            gsrPrevZ = currentZ;
            gsrInitialized = true;
            GSRMain.LOGGER.info("[GSR-Debug] Tracker initialized for player: {}", player.getName().getString());
        } else {
            double dx = currentX - gsrPrevX;
            double dy = currentY - gsrPrevY;
            double dz = currentZ - gsrPrevZ;
            double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);

            if (dist > 0.01 && dist < 10.0) {
                GSRStats.addFloat(GSRStats.DISTANCE_MOVED, player.getUuid(), (float) dist);

                // --- DEBUG LOGGING ---
                // We only log every ~20 ticks (1 second) so we don't spam the console 20 times a second.
                if (player.age % 20 == 0) {
                    Float totalDist = GSRStats.DISTANCE_MOVED.get(player.getUuid());
                    GSRMain.LOGGER.info("[GSR-Debug] {} moved. Tick Dist: {} | Total: {}",
                            player.getName().getString(), dist, totalDist);
                }
            }

            gsrPrevX = currentX;
            gsrPrevY = currentY;
            gsrPrevZ = currentZ;
        }

        // 3. LOGIC GATES
        if (GSRMain.CONFIG == null || GSRMain.CONFIG.isTimerFrozen || GSRMain.CONFIG.startTime <= 0) return;

        // 4. NETHER STRUCTURE DETECTION
        if (player.getEntityWorld() instanceof ServerWorld world && world.getRegistryKey() == World.NETHER) {
            var structureRegistry = world.getRegistryManager().getOrThrow(RegistryKeys.STRUCTURE);

            // Fortress Check
            if (GSRMain.CONFIG.timeFortress <= 0) {
                structureRegistry.getOptional(StructureKeys.FORTRESS).ifPresent(entry -> {
                    if (world.getStructureAccessor().getStructureAt(player.getBlockPos(), entry.value()).hasChildren()) {
                        GSRMain.LOGGER.info("[GSR-Debug] Fortress detected for {}", player.getName().getString());
                        GSRSplitManager.completeSplit(this.server, "fortress");
                    }
                });
            }

            // Bastion Check
            if (GSRMain.CONFIG.timeBastion <= 0) {
                structureRegistry.getOptional(StructureKeys.BASTION_REMNANT).ifPresent(entry -> {
                    if (world.getStructureAccessor().getStructureAt(player.getBlockPos(), entry.value()).hasChildren()) {
                        GSRMain.LOGGER.info("[GSR-Debug] Bastion detected for {}", player.getName().getString());
                        GSRSplitManager.completeSplit(this.server, "bastion");
                    }
                });
            }
        }
    }

    @Inject(method = "onScreenHandlerOpened", at = @At("HEAD"))
    private void onOpen(ScreenHandler handler, CallbackInfo ci) {
        if (GSRMain.CONFIG == null || GSRMain.CONFIG.isTimerFrozen) return;
        ServerPlayerEntity player = (ServerPlayerEntity) (Object) this;
        if (!(handler instanceof net.minecraft.screen.PlayerScreenHandler)) {
            GSRStats.addInt(GSRStats.INVENTORIES_OPENED, player.getUuid(), 1);
            GSRMain.LOGGER.info("[GSR-Debug] Inventory opened by {}", player.getName().getString());
        }
    }

    @Inject(method = "onDeath", at = @At("HEAD"))
    private void onPlayerDeath(DamageSource source, CallbackInfo ci) {
        ServerPlayerEntity player = (ServerPlayerEntity) (Object) this;
        GSRMain.LOGGER.info("[GSR-Debug] Death detected for {}", player.getName().getString());
        if (this.server != null) {
            GSREvents.handlePlayerDeath(player, this.server);
        }
    }
}