package net.berkle.groupspeedrun.managers;

import net.berkle.groupspeedrun.GSREvents;
import net.berkle.groupspeedrun.GSRMain;
import net.berkle.groupspeedrun.util.GSRFormatUtil;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.gen.structure.Structure;

public class GSRSplitManager {

    private static final RegistryKey<Structure> FORTRESS_KEY = RegistryKey.of(RegistryKeys.STRUCTURE, Identifier.ofVanilla("fortress"));
    private static final RegistryKey<Structure> BASTION_KEY = RegistryKey.of(RegistryKeys.STRUCTURE, Identifier.ofVanilla("bastion_remnant"));

    /**
     * Resets all split times. CRITICAL: Call this in GSREvents.executeReset()
     */
    public static void resetSplits() {
        var config = GSRMain.CONFIG;
        if (config == null) return;
        config.timeNether = 0;
        config.timeBastion = 0;
        config.timeFortress = 0;
        config.timeEnd = 0;
        config.timeDragon = 0;
        config.lastSplitTime = 0;
    }

    public static void checkSplits(MinecraftServer server) {
        var config = GSRMain.CONFIG;
        if (config == null || config.startTime < 0 || config.isTimerFrozen) return;

        var players = server.getPlayerManager().getPlayerList();
        if (players.isEmpty()) return;

        for (ServerPlayerEntity player : players) {
            ServerWorld world = (ServerWorld) player.getEntityWorld();
            var dim = world.getRegistryKey();
            BlockPos pos = player.getBlockPos();

            if (dim == World.NETHER && config.timeNether <= 0) {
                completeSplit(server, "Nether");
            } else if (dim == World.END && config.timeEnd <= 0) {
                completeSplit(server, "The End");
            }

            if (dim == World.NETHER) {
                var structureRegistry = world.getRegistryManager().getOrThrow(RegistryKeys.STRUCTURE);
                if (config.timeBastion <= 0) {
                    structureRegistry.getOptional(BASTION_KEY).ifPresent(entry -> {
                        if (world.getStructureAccessor().getStructureAt(pos, entry.value()).hasChildren()) {
                            completeSplit(server, "Bastion");
                        }
                    });
                }
                if (config.timeFortress <= 0) {
                    structureRegistry.getOptional(FORTRESS_KEY).ifPresent(entry -> {
                        if (world.getStructureAccessor().getStructureAt(pos, entry.value()).hasChildren()) {
                            completeSplit(server, "Fortress");
                        }
                    });
                }
            }
        }
    }

    public static void completeSplit(MinecraftServer server, String type) {
        var config = GSRMain.CONFIG;
        if (config == null || config.startTime < 0) return;

        long splitTicks = GSREvents.getRunTicks(server);
        boolean changed = false;

        switch (type.toLowerCase().replace(" ", "")) {
            case "nether" -> { if (config.timeNether <= 0) { config.timeNether = splitTicks; changed = true; } }
            case "bastion" -> { if (config.timeBastion <= 0) { config.timeBastion = splitTicks; changed = true; } }
            case "fortress" -> { if (config.timeFortress <= 0) { config.timeFortress = splitTicks; changed = true; } }
            case "theend", "end" -> { if (config.timeEnd <= 0) { config.timeEnd = splitTicks; changed = true; } }
            case "dragon" -> {
                if (!config.isVictorious && !config.isFailed) {
                    config.timeDragon = splitTicks;
                    config.isVictorious = true;
                    config.isTimerFrozen = true;
                    // Using real-world clock diff for the frozen anchor
                    config.frozenTime = System.currentTimeMillis() - config.startTime;
                    config.victoryTimer = 200;
                    changed = true;
                }
            }
        }

        if (changed) {
            config.lastSplitTime = server.getOverworld().getTime();
            String formatted = GSRFormatUtil.formatTime(splitTicks);

            // Output to tell the user something happened (Broadcast)
            server.getPlayerManager().broadcast(
                    Text.literal("§6§l[GSR] Split: §b" + type.toUpperCase() + " §fat §e" + formatted),
                    false
            );

            for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
                p.playSound(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
            }

            GSRMain.saveAndSync(server);
        }
    }
}