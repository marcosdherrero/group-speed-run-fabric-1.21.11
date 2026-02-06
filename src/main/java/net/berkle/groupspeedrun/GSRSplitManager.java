package net.berkle.groupspeedrun;

import net.berkle.groupspeedrun.config.GSRConfig;
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

/**
 * Manages speedrun milestones (splits).
 * Detects when players enter specific dimensions or structures and records the time.
 */
public class GSRSplitManager {

    private static final RegistryKey<Structure> FORTRESS_KEY = RegistryKey.of(RegistryKeys.STRUCTURE, Identifier.ofVanilla("fortress"));
    private static final RegistryKey<Structure> BASTION_KEY = RegistryKey.of(RegistryKeys.STRUCTURE, Identifier.ofVanilla("bastion_remnant"));

    /**
     * Logic for scanning players and identifying if a milestone has been reached.
     */
    public static void checkSplits(MinecraftServer server) {
        var config = GSRMain.CONFIG;
        if (config == null || config.startTime < 0 || config.isTimerFrozen) return;

        var players = server.getPlayerManager().getPlayerList();
        if (players.isEmpty()) return;

        for (ServerPlayerEntity player : players) {
            ServerWorld world = (ServerWorld) player.getEntityWorld();
            var dim = world.getRegistryKey();
            BlockPos pos = player.getBlockPos();

            // Dimension checking
            if (dim == World.NETHER && config.timeNether <= 0) {
                completeSplit(server, "Nether");
            } else if (dim == World.END && config.timeEnd <= 0) {
                completeSplit(server, "The End");
            }

            // Structure checking
            if (dim == World.NETHER && (config.timeBastion <= 0 || config.timeFortress <= 0)) {
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

    /**
     * Records the time, locks the split, and triggers the HUD pop-up animation.
     */
    public static void completeSplit(MinecraftServer server, String type) {
        var config = GSRMain.CONFIG;
        if (config == null || config.startTime < 0) return;

        long splitTicks = GSREvents.getRunTicks(server);
        boolean changed = false;

        // --- CORE SPLIT LOGIC ---
        switch (type.toLowerCase().replace(" ", "")) {
            case "nether" -> { if (config.timeNether <= 0) { config.timeNether = splitTicks; changed = true; } }
            case "bastion" -> { if (config.timeBastion <= 0) { config.timeBastion = splitTicks; changed = true; } }
            case "fortress" -> { if (config.timeFortress <= 0) { config.timeFortress = splitTicks; changed = true; } }
            case "theend", "end" -> { if (config.timeEnd <= 0) { config.timeEnd = splitTicks; changed = true; } }
            case "dragon" -> {
                if (!config.wasVictorious && !config.isFailed) {
                    config.timeDragon = splitTicks;
                    config.wasVictorious = true;
                    config.isTimerFrozen = true;
                    config.frozenTime = server.getOverworld().getTime();
                    config.victoryTimer = 100;
                    changed = true;
                }
            }
        }

        if (changed) {
            /**
             * TRIGGER THE HUD FADE:
             * By setting lastSplitTime to the current world time, the GSRTimerHudState
             * logic on the client will calculate a 10-second window to show the HUD.
             */
            config.lastSplitTime = server.getOverworld().getTime();

            // Provide feedback via chat and sound
            String formatted = formatTime(splitTicks);
            server.getPlayerManager().broadcast(
                    Text.literal("§6§l[GSR] Split: §b" + type.toUpperCase() + " §fat §e" + formatted),
                    false
            );

            for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
                p.playSound(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
            }

            /** * SYNC DATA:
             * This sends the updated 'lastSplitTime' to all clients immediately so
             * their HUDs pop up at the same time.
             */
            GSRMain.saveAndSync(server);
        }
    }

    public static String formatTime(long ticks) {
        long totalMs = ticks * 50;
        long h = totalMs / 3600000;
        long m = (totalMs / 60000) % 60;
        long s = (totalMs / 1000) % 60;
        long f = (totalMs % 1000) / 10;

        if (h > 0) return String.format("%d:%02d:%02d.%02d", h, m, s, f);
        return String.format("%02d:%02d.%02d", m, s, f);
    }
}