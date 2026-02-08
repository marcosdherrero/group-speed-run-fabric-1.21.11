package net.berkle.groupspeedrun;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.mojang.datafixers.util.Pair;
import net.berkle.groupspeedrun.config.GSRConfigPlayer;
import net.berkle.groupspeedrun.managers.GSRBroadcastManager;
import net.minecraft.advancement.AdvancementProgress;
import net.minecraft.advancement.PlayerAdvancementTracker;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.entry.RegistryEntryList;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.GameMode;
import net.minecraft.world.gen.structure.Structure;
import net.minecraft.world.gen.structure.StructureKeys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static net.minecraft.server.command.CommandManager.literal;
import static net.minecraft.server.command.CommandManager.argument;

/**
 * Main command handling class for Group Speedrun (GSR).
 * Handles run control, global settings, player-specific HUD preferences, and structure locators.
 */
public class GSRCommands {
    private static final Logger LOGGER = LoggerFactory.getLogger("GSR-Commands");

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        // We capture the node, so the semicolon goes at the very end of the builder chain.
        LiteralCommandNode<ServerCommandSource> gsrRoot = dispatcher.register(literal("gsr")
                .requires(source -> CommandManager.ALWAYS_PASS_CHECK.allows(source.getPermissions()))

                // --- ROOT: STATS & STATUS ---
                .then(literal("stats").executes(context -> {
                    var config = GSRMain.CONFIG;
                    if (config == null || config.startTime < 0) {
                        context.getSource().sendError(Text.literal("No active run found."));
                        return 0;
                    }
                    GSRBroadcastManager.broadcastLiveStats(context.getSource().getServer());
                    return 1;
                }))
                .then(literal("status").executes(context -> {
                    displayStatus(context.getSource());
                    return 1;
                }))

                // --- BRANCH: RUN CONTROL ---
                .then(literal("pause")
                        .requires(source -> {
                            var config = GSRMain.CONFIG;
                            // Admin only, and only if the run is still in progress
                            return CommandManager.ADMINS_CHECK.allows(source.getPermissions()) &&
                                    (config != null && !config.isFailed && !config.isVictorious);
                        })
                        .executes(context -> togglePause(context.getSource(), true)))
                .then(literal("resume")
                        .requires(source -> {
                            var config = GSRMain.CONFIG;
                            // Must be an admin AND the run must still be valid (not failed and not victorious)
                            return CommandManager.ADMINS_CHECK.allows(source.getPermissions()) &&
                                    (config != null && !config.isFailed && !config.isVictorious);
                        })
                        .executes(context -> togglePause(context.getSource(), false)))
                .then(literal("reset")
                        .requires(source -> {
                            var config = GSRMain.CONFIG;
                            return CommandManager.ADMINS_CHECK.allows(source.getPermissions()) ||
                                    (config != null && (config.isFailed || config.isVictorious));
                        })
                        .executes(context -> {
                            executeReset(context.getSource().getServer());
                            context.getSource().sendFeedback(() -> Text.literal("§6[GSR] §aRun has been fully reset!"), true);
                            return 1;
                        }))

                // --- BRANCH: SETTINGS ---
                .then(literal("settings")
                        .requires(source -> CommandManager.ADMINS_CHECK.allows(source.getPermissions()))
                        .then(literal("shared_hp_toggle").executes(context -> {
                            GSRMain.CONFIG.sharedHealthEnabled = !GSRMain.CONFIG.sharedHealthEnabled;
                            GSRMain.saveAndSync(context.getSource().getServer());
                            String state = GSRMain.CONFIG.sharedHealthEnabled ? "§aON" : "§cOFF";
                            context.getSource().sendFeedback(() -> Text.literal("§6[GSR] Shared Health: " + state), true);
                            return 1;
                        }))
                        .then(literal("group_death_toggle").executes(context -> {
                            GSRMain.CONFIG.groupDeathEnabled = !GSRMain.CONFIG.groupDeathEnabled;
                            GSRMain.saveAndSync(context.getSource().getServer());
                            String state = GSRMain.CONFIG.groupDeathEnabled ? "§aON" : "§cOFF";
                            context.getSource().sendFeedback(() -> Text.literal("§6[GSR] Group Death: " + state), true);
                            return 1;
                        }))
                        .then(literal("max_hp")
                                .then(argument("amount", FloatArgumentType.floatArg(0.5f, 100.0f))
                                        .executes(context -> {
                                            float val = FloatArgumentType.getFloat(context, "amount");
                                            updateMaxHearts(context.getSource().getServer(), val);
                                            context.getSource().sendFeedback(() -> Text.literal("§6[GSR] Global Max Health: §f" + val), true);
                                            return 1;
                                        }))))

                // --- BRANCH: HUD ---
                .then(literal("hud")
                        .then(literal("visibility_toggle").executes(context -> {
                            var pConfig = GSRConfigPlayer.INSTANCE;
                            pConfig.hudMode = (pConfig.hudMode + 1) % 3;
                            pConfig.save();
                            String mode = switch (pConfig.hudMode) {
                                case 0 -> "ALWAYS VISIBLE";
                                case 1 -> "TAB ONLY";
                                default -> "HIDDEN";
                            };
                            context.getSource().sendFeedback(() -> Text.literal("§6[GSR] HUD Mode: §f" + mode), false);
                            return 1;
                        }))
                        .then(literal("side_toggle").executes(context -> {
                            var pConfig = GSRConfigPlayer.INSTANCE;
                            pConfig.timerHudOnRight = !pConfig.timerHudOnRight;
                            pConfig.save();
                            context.getSource().sendFeedback(() -> Text.literal("§6[GSR] Timer Side: §b" + (pConfig.timerHudOnRight ? "RIGHT" : "LEFT")), false);
                            return 1;
                        }))
                        .then(literal("height_toggle").executes(context -> {
                            var pConfig = GSRConfigPlayer.INSTANCE;
                            pConfig.locateHudOnTop = !pConfig.locateHudOnTop;
                            pConfig.save();
                            String pos = pConfig.locateHudOnTop ? "TOP" : "BOTTOM";
                            context.getSource().sendFeedback(() -> Text.literal("§6[GSR] Locate Bar Position: §e" + pos), false);
                            return 1;
                        }))
                        .then(literal("scale")
                                .then(argument("value", FloatArgumentType.floatArg(GSRConfigPlayer.MIN_HUD_SCALE, GSRConfigPlayer.MAX_HUD_SCALE))
                                        .executes(context -> {
                                            float scale = FloatArgumentType.getFloat(context, "value");
                                            GSRConfigPlayer.INSTANCE.timerHudScale = scale;
                                            GSRConfigPlayer.INSTANCE.locateHudScale = scale;
                                            GSRConfigPlayer.INSTANCE.save();
                                            context.getSource().sendFeedback(() -> Text.literal("§6[GSR] HUD Scale set to: §f" + String.format("%.2f", scale)), false);
                                            return 1;
                                        })))) // Correctly closes scale -> argument -> executes

                // --- BRANCH: LOCATE ---
                .then(literal("locate")
                        .requires(source -> {
                            var config = GSRMain.CONFIG;
                            return CommandManager.ADMINS_CHECK.allows(source.getPermissions()) ||
                                    (config != null && (config.isFailed || config.isVictorious));
                        })
                        .then(literal("fortress_toggle").executes(context -> toggleLocate(context.getSource(), "Fortress")))
                        .then(literal("bastion_toggle").executes(context -> toggleLocate(context.getSource(), "Bastion")))
                        .then(literal("stronghold_toggle").executes(context -> toggleLocate(context.getSource(), "Stronghold")))
                        .then(literal("ship_toggle").executes(context -> toggleLocate(context.getSource(), "Ship")))
                        .then(literal("clear").executes(context -> {
                            clearAllLocates(context.getSource().getServer());
                            context.getSource().sendFeedback(() -> Text.literal("§6[GSR] §aAll structure locators cleared."), false);
                            return 1;
                        })))
        );
    }

    /**
     * Prints a formatted status summary of the current settings to the user's chat.
     */
    private static void displayStatus(ServerCommandSource source) {
        var worldConfig = GSRMain.CONFIG;
        var playerConfig = GSRConfigPlayer.INSTANCE;
        String hudStatus = switch (playerConfig.hudMode) {
            case 0 -> "§aALWAYS VISIBLE";
            case 1 -> "§bTAB ONLY";
            default -> "§cHIDDEN";
        };
        source.sendFeedback(() -> Text.literal(
                "§6§l[GSR] Current Configuration:\n" +
                        "§f- HUD Mode: " + hudStatus + "\n" +
                        "§f- Timer Side: §b" + (playerConfig.timerHudOnRight ? "RIGHT" : "LEFT") + "\n" +
                        "§f- Locate Pos: §e" + (playerConfig.locateHudOnTop ? "TOP" : "BOTTOM") + "\n" +
                        "§f- Group Death: " + (worldConfig.groupDeathEnabled ? "§aON" : "§cOFF") + "\n" +
                        "§f- Shared HP: " + (worldConfig.sharedHealthEnabled ? "§aON" : "§cOFF") + "\n" +
                        "§f- Max Hearts: §c" + worldConfig.maxHearts
        ), false);
    }

    /**
     * Pauses or resumes the global timer and saves the state to config.
     */
    private static int togglePause(ServerCommandSource source, boolean shouldPause) {
        var config = GSRMain.CONFIG;
        if (config == null) return 0;
        if (shouldPause && !config.isTimerFrozen) {
            config.isTimerFrozen = true;
            config.frozenTime = System.currentTimeMillis() - config.startTime;
            source.getServer().getPlayerManager().broadcast(Text.literal("§6[GSR] §cTimer Paused!"), false);
        } else if (!shouldPause && config.isTimerFrozen) {
            config.isTimerFrozen = false;
            config.startTime = System.currentTimeMillis() - config.frozenTime;
            source.getServer().getPlayerManager().broadcast(Text.literal("§6[GSR] §aTimer Resumed!"), false);
        } else {
            source.sendFeedback(() -> Text.literal("§e[GSR] Timer is already " + (shouldPause ? "paused." : "running.")), false);
        }
        GSRMain.saveAndSync(source.getServer());
        return 1;
    }

    /**
     * Updates max health for all online players and updates the global config.
     */
    private static void updateMaxHearts(MinecraftServer server, float amount) {
        GSRMain.CONFIG.maxHearts = amount;
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            GSREvents.applyMaxHealth(p, amount);
        }
        GSRMain.saveAndSync(server);
    }

    /**
     * Logic for individual structure toggles. If turning ON, it triggers a search.
     */
    private static int toggleLocate(ServerCommandSource source, String type) {
        var config = GSRMain.CONFIG;
        boolean nowActive;
        switch (type.toLowerCase()) {
            case "fortress" -> { config.fortressActive = !config.fortressActive; nowActive = config.fortressActive; }
            case "bastion" -> { config.bastionActive = !config.bastionActive; nowActive = config.bastionActive; }
            case "stronghold" -> { config.strongholdActive = !config.strongholdActive; nowActive = config.strongholdActive; }
            case "ship" -> { config.shipActive = !config.shipActive; nowActive = config.shipActive; }
            default -> { return 0; }
        }

        if (nowActive) {
            return locateAndSync(source, type);
        } else {
            GSRMain.saveAndSync(source.getServer());
            source.sendFeedback(() -> Text.literal("§6[GSR] " + type + " locator §cOFF"), false);
            return 1;
        }
    }

    /**
     * Performs a server-side search for the nearest specified structure and saves coordinates.
     */
    private static int locateAndSync(ServerCommandSource source, String type) {
        ServerPlayerEntity player = source.getPlayer();
        if (player == null) return 0;
        ServerWorld world = source.getWorld();
        var structureRegistry = world.getRegistryManager().getOrThrow(RegistryKeys.STRUCTURE);

        // 1. Identify the structure key
        var structureKey = switch (type.toLowerCase()) {
            case "fortress" -> StructureKeys.FORTRESS;
            case "bastion" -> StructureKeys.BASTION_REMNANT;
            case "stronghold" -> StructureKeys.STRONGHOLD;
            case "ship" -> StructureKeys.END_CITY;
            default -> StructureKeys.FORTRESS;
        };

        var structureEntry = structureRegistry.getOptional(structureKey);
        if (structureEntry.isPresent()) {
            RegistryEntryList<Structure> structureSet = RegistryEntryList.of(structureEntry.get());

            // 2. Locate the nearest instance of that structure
            Pair<BlockPos, RegistryEntry<Structure>> result = world.getChunkManager().getChunkGenerator()
                    .locateStructure(world, structureSet, player.getBlockPos(), 100, false);

            if (result != null) {
                BlockPos pos = result.getFirst();
                var config = GSRMain.CONFIG;

                // 3. SPECIAL LOGIC FOR END SHIPS (Verification)
                if (type.equalsIgnoreCase("ship")) {
                    var accessor = world.getStructureAccessor();

                    // Get the specific structure start using the actual Structure object (not the key)
                    var start = accessor.getStructureAt(pos, structureEntry.get().value());

                    if (start != null && start.hasChildren()) {
                        // Check pieces for "ship" identifier
                        boolean hasShip = start.getChildren().stream()
                                .anyMatch(piece -> piece.getType().toString().toLowerCase().contains("ship"));

                        if (!hasShip) {
                            source.sendError(Text.literal("§6[GSR] §cFound End City at " + pos.getX() + ", " + pos.getZ() + " but it has no Ship!"));
                            return 0;
                        }
                    }
                }

                // 4. Save coordinates to config and activate HUD pin
                switch (type.toLowerCase()) {
                    case "fortress" -> { config.fortressX = pos.getX(); config.fortressZ = pos.getZ(); config.fortressActive = true; }
                    case "bastion" -> { config.bastionX = pos.getX(); config.bastionZ = pos.getZ(); config.bastionActive = true; }
                    case "stronghold" -> { config.strongholdX = pos.getX(); config.strongholdZ = pos.getZ(); config.strongholdActive = true; }
                    case "ship" -> { config.shipX = pos.getX(); config.shipZ = pos.getZ(); config.shipActive = true; }
                }

                GSRMain.saveAndSync(source.getServer());
                source.sendFeedback(() -> Text.literal("§6[GSR] " + type + " located at §f" + pos.getX() + ", " + pos.getZ() + " §a(HUD ON)"), false);
                return 1;
            }
        }

        source.sendError(Text.literal("§6[GSR] §cCould not find " + type + " in this dimension."));
        return 0;
    }

    /**
     * Batch-disables all active structure locators.
     */
    private static void clearAllLocates(MinecraftServer server) {
        var config = GSRMain.CONFIG;
        if (config == null) return;
        config.fortressActive = false;
        config.bastionActive = false;
        config.strongholdActive = false;
        config.shipActive = false;
        GSRMain.saveAndSync(server);
    }

    /**
     * The heavy lifter: Resets the world time, resets player stats, clears inventories,
     * revokes advancements, and teleports everyone back to spawn.
     */
    private static void executeReset(MinecraftServer server) {
        ServerWorld overworld = server.getOverworld();
        overworld.setTimeOfDay(0);
        GSRStats.reset();
        GSREvents.resetHealth();

        var config = GSRMain.CONFIG;
        config.startTime = -1;
        config.isTimerFrozen = false;
        config.frozenTime = 0;
        config.isFailed = false;
        config.isVictorious = false;
        config.fortressActive = false;
        config.bastionActive = false;
        config.strongholdActive = false;
        config.shipActive = false;

        BlockPos spawnPos = overworld.getSpawnPoint().getPos();
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            p.changeGameMode(GameMode.SURVIVAL);
            p.getInventory().clear();
            p.getHungerManager().setFoodLevel(20);
            GSREvents.applyMaxHealth(p, config.maxHearts);
            p.clearStatusEffects();
            p.setFireTicks(0);
            p.setOnFire(false);
            p.setExperienceLevel(0);
            p.setExperiencePoints(0);
            // Teleport to spawn
            p.teleport(overworld, spawnPos.getX() + 0.5, spawnPos.getY(), spawnPos.getZ() + 0.5, java.util.Collections.emptySet(), 0.0f, 0.0f, true);
            p.setSpawnPoint(null, false);

            // Wipe Advancements
            PlayerAdvancementTracker tracker = p.getAdvancementTracker();
            server.getAdvancementLoader().getAdvancements().forEach(entry -> {
                AdvancementProgress progress = tracker.getProgress(entry);
                for (String criteria : progress.getObtainedCriteria()) {
                    tracker.revokeCriterion(entry, criteria);
                }
            });
        }
        GSRMain.saveAndSync(server);
        server.getPlayerManager().broadcast(Text.literal("§6§l[GSR] Run Reset, Go!"), false);
    }
}