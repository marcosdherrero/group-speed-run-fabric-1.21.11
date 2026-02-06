package net.berkle.groupspeedrun;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.datafixers.util.Pair;
import net.berkle.groupspeedrun.config.GSRConfig;
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

public class GSRCommands {
    private static final Logger LOGGER = LoggerFactory.getLogger("GSR-Commands");

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(literal("gsr")
                .requires(source -> CommandManager.ALWAYS_PASS_CHECK.allows(source.getPermissions()))

                // --- STATUS COMMAND ---
                .then(literal("status")
                        .executes(context -> {
                            var config = GSRMain.CONFIG;
                            String hud = switch (config.hudMode) {
                                case 0 -> "§aALWAYS VISIBLE";
                                case 1 -> "§bTAB ONLY";
                                default -> "§cHIDDEN";
                            };
                            context.getSource().sendFeedback(() -> Text.literal(
                                    "§6§l[GSR] Status:\n" +
                                            "§f- HUD Mode: " + hud + "\n" +
                                            "§f- Group Death: " + (config.groupDeathEnabled ? "§aON" : "§cOFF") + "\n" +
                                            "§f- Shared HP: " + (config.sharedHealthEnabled ? "§aON" : "§cOFF") + "\n" +
                                            "§f- Max Hearts: §c" + config.maxHearts
                            ), false);
                            return 1;
                        }))

                // --- GAMEPLAY TOGGLES (Shared Health & Group Death) ---
                .then(literal("toggle_shared_hp")
                        .requires(source -> CommandManager.ADMINS_CHECK.allows(source.getPermissions()))
                        .executes(context -> {
                            GSRMain.CONFIG.sharedHealthEnabled = !GSRMain.CONFIG.sharedHealthEnabled;
                            GSRMain.saveAndSync(context.getSource().getServer());
                            String state = GSRMain.CONFIG.sharedHealthEnabled ? "§aON" : "§cOFF";
                            context.getSource().sendFeedback(() -> Text.literal("§6[GSR] Shared Health: " + state), true);
                            return 1;
                        }))

                .then(literal("toggle_group_death")
                        .requires(source -> CommandManager.ADMINS_CHECK.allows(source.getPermissions()))
                        .executes(context -> {
                            GSRMain.CONFIG.groupDeathEnabled = !GSRMain.CONFIG.groupDeathEnabled;
                            GSRMain.saveAndSync(context.getSource().getServer());
                            String state = GSRMain.CONFIG.groupDeathEnabled ? "§aON" : "§cOFF";
                            context.getSource().sendFeedback(() -> Text.literal("§6[GSR] Group Death: " + state), true);
                            return 1;
                        }))

                // --- SET MAX HEALTH (Defaults to 10 if no value provided) ---
                .then(literal("set_max_hp")
                        .requires(source -> CommandManager.ADMINS_CHECK.allows(source.getPermissions()))
                        // Case 1: /gsr set_max_hp -> Defaults to 10
                        .executes(context -> {
                            updateMaxHearts(context.getSource().getServer(), 10.0f);
                            context.getSource().sendFeedback(() -> Text.literal("§6[GSR] Max Health reset to DEFAULT: §f10 hearts"), true);
                            return 1;
                        })
                        // Case 2: /gsr set_max_hp <amount>
                        .then(argument("amount", FloatArgumentType.floatArg(0.5f, 100.0f))
                                .executes(context -> {
                                    float val = FloatArgumentType.getFloat(context, "amount");
                                    updateMaxHearts(context.getSource().getServer(), val);
                                    context.getSource().sendFeedback(() -> Text.literal("§6[GSR] Max Health set to: §f" + val + " hearts"), true);
                                    return 1;
                                })))

                // --- HUD CONFIGURATION ---
                .then(literal("toggle_hud")
                        .requires(source -> CommandManager.ALWAYS_PASS_CHECK.allows(source.getPermissions()))
                        .executes(context -> {
                            GSRMain.CONFIG.hudMode = (GSRMain.CONFIG.hudMode + 1) % 3;
                            GSRMain.saveAndSync(context.getSource().getServer());
                            String mode = switch (GSRMain.CONFIG.hudMode) {
                                case 0 -> "Always Visible";
                                case 1 -> "Tab Only (Hold TAB)";
                                default -> "Hidden";
                            };
                            context.getSource().sendFeedback(() -> Text.literal("§6[GSR] HUD Mode: §f" + mode), false);
                            return 1;
                        })
                        .then(literal("timer_hud_side")
                                .executes(context -> {
                                    GSRMain.CONFIG.timerHudOnRight = !GSRMain.CONFIG.timerHudOnRight;
                                    GSRMain.saveAndSync(context.getSource().getServer());
                                    String side = GSRMain.CONFIG.timerHudOnRight ? "RIGHT" : "LEFT";
                                    context.getSource().sendFeedback(() -> Text.literal("§6[GSR] Timer HUD moved to: §f" + side), false);
                                    return 1;
                                }))
                        .then(literal("locate_hud_height")
                                .executes(context -> {
                                    GSRMain.CONFIG.locateHudOnTop = !GSRMain.CONFIG.locateHudOnTop;
                                    GSRMain.saveAndSync(context.getSource().getServer());
                                    // If true -> Top, If false -> Bottom
                                    String pos = GSRMain.CONFIG.locateHudOnTop ? "TOP" : "BOTTOM";
                                    context.getSource().sendFeedback(() -> Text.literal("§6[GSR] Locate HUD moved to: §f" + pos), false);
                                    return 1;
                                }))
                        .then(literal("scale_timer_hud")
                                .executes(context -> {
                                    GSRMain.CONFIG.timerHudScale = 1.0f;
                                    GSRMain.saveAndSync(context.getSource().getServer());
                                    context.getSource().sendFeedback(() -> Text.literal("§6[GSR] Timer HUD scale reset to §f1.0"), true);
                                    return 1;
                                })
                                .then(argument("value", FloatArgumentType.floatArg(GSRConfig.MIN_TIMER_HUD_SCALE, GSRConfig.MAX_TIMER_HUD_SCALE))
                                        .executes(context -> {
                                            float val = FloatArgumentType.getFloat(context, "value");
                                            GSRMain.CONFIG.timerHudScale = val;
                                            GSRMain.saveAndSync(context.getSource().getServer());
                                            context.getSource().sendFeedback(() -> Text.literal("§6[GSR] Timer HUD scale set to: §f" + val), true);
                                            return 1;
                                        })))
                        .then(literal("scale_locate_hud")
                                .executes(context -> {
                                    GSRMain.CONFIG.locateHudScale = 1.0f;
                                    GSRMain.saveAndSync(context.getSource().getServer());
                                    context.getSource().sendFeedback(() -> Text.literal("§6[GSR] Locate HUD scale reset to §f1.0"), true);
                                    return 1;
                                })
                                .then(argument("value", FloatArgumentType.floatArg(GSRConfig.MIN_LOCATE_HUD_SCALE, GSRConfig.MAX_LOCATE_HUD_SCALE))
                                        .executes(context -> {
                                            float val = FloatArgumentType.getFloat(context, "value");
                                            GSRMain.CONFIG.locateHudScale = val;
                                            GSRMain.saveAndSync(context.getSource().getServer());
                                            context.getSource().sendFeedback(() -> Text.literal("§6[GSR] Locate HUD scale set to: §f" + val), true);
                                            return 1;
                                        })))
                )

                // --- STRUCTURE LOCATOR ---
                .then(literal("easy_locate")
                        .requires(source -> CommandManager.ALWAYS_PASS_CHECK.allows(source.getPermissions()))
                        .then(literal("fortress").executes(context -> toggleLocate(context.getSource(), "Fortress")))
                        .then(literal("bastion").executes(context -> toggleLocate(context.getSource(), "Bastion")))
                        .then(literal("stronghold").executes(context -> toggleLocate(context.getSource(), "Stronghold")))
                        .then(literal("ship").executes(context -> toggleLocate(context.getSource(), "Ship")))
                        .then(literal("clear").executes(context -> {
                            clearLocator(context.getSource().getServer());
                            notifyOps(context.getSource(), "§8[GSR] All locators cleared");
                            return 1;
                        }))
                )

                // --- ADMIN COMMANDS: RESET RUN ---
                .then(literal("reset")
                        .requires(source -> CommandManager.ADMINS_CHECK.allows(source.getPermissions()))
                        .executes(context -> {
                            executeReset(context.getSource().getServer());
                            return 1;
                        }))
        );
    }

    /**
     * Helper to apply max health changes to the config and all active players.
     */
    private static void updateMaxHearts(MinecraftServer server, float amount) {
        GSRMain.CONFIG.maxHearts = amount; // Ensure GSRConfig.maxHearts is float
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            GSREvents.applyMaxHealth(p, amount);
        }
        GSRMain.saveAndSync(server);
    }

    private static int toggleLocate(ServerCommandSource source, String type) {
        var config = GSRMain.CONFIG;
        boolean isActive = switch (type.toLowerCase()) {
            case "fortress" -> config.fortressActive;
            case "bastion" -> config.bastionActive;
            case "stronghold" -> config.strongholdActive;
            case "ship" -> config.shipActive;
            default -> false;
        };

        if (isActive) {
            switch (type.toLowerCase()) {
                case "fortress" -> config.fortressActive = false;
                case "bastion" -> config.bastionActive = false;
                case "stronghold" -> config.strongholdActive = false;
                case "ship" -> config.shipActive = false;
            }
            GSRMain.saveAndSync(source.getServer());
            source.sendFeedback(() -> Text.literal("§6[GSR] " + type + " locator §cOFF"), false);
            return 1;
        }

        return locateAndSync(source, type);
    }

    private static void clearLocator(MinecraftServer server) {
        var config = GSRMain.CONFIG;
        config.fortressActive = false;
        config.bastionActive = false;
        config.strongholdActive = false;
        config.shipActive = false;
        GSRMain.saveAndSync(server);
    }

    private static int locateAndSync(ServerCommandSource source, String type) {
        ServerPlayerEntity player = source.getPlayer();
        if (player == null) return 0;

        ServerWorld world = source.getWorld();
        var structureRegistry = world.getRegistryManager().getOrThrow(RegistryKeys.STRUCTURE);

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
            Pair<BlockPos, RegistryEntry<Structure>> result = world.getChunkManager().getChunkGenerator()
                    .locateStructure(world, structureSet, player.getBlockPos(), 100, false);

            if (result != null) {
                BlockPos pos = result.getFirst();
                var config = GSRMain.CONFIG;

                switch (type.toLowerCase()) {
                    case "fortress" -> { config.fortressX = pos.getX(); config.fortressZ = pos.getZ(); config.fortressActive = true; }
                    case "bastion" -> { config.bastionX = pos.getX(); config.bastionZ = pos.getZ(); config.bastionActive = true; }
                    case "stronghold" -> { config.strongholdX = pos.getX(); config.strongholdZ = pos.getZ(); config.strongholdActive = true; }
                    case "ship" -> { config.shipX = pos.getX(); config.shipZ = pos.getZ(); config.shipActive = true; }
                }

                GSRMain.saveAndSync(source.getServer());
                source.sendFeedback(() -> Text.literal("§6[GSR] " + type + " located at §f" + pos.getX() + ", " + pos.getZ()), false);
                return 1;
            }
        }

        source.sendError(Text.literal("Could not find " + type + " in this dimension."));
        return 0;
    }

    /**
     * Resets the entire run state, world time, and player statistics.
     */
    private static void executeReset(MinecraftServer server) {
        ServerWorld overworld = server.getOverworld();

        // 1. Reset World Time
        overworld.setTimeOfDay(0);

        // 2. Clear Logic State
        GSRStats.reset();
        GSREvents.resetHealth();

        // 3. Reset Config & Timer Flags
        var config = GSRMain.CONFIG;
        config.startTime = -1; // Reset to -1 so auto-start triggers on movement
        config.isTimerFrozen = false;
        config.frozenTime = 0;
        config.isFailed = false;
        config.wasVictorious = false;

        // 4. Clear Locators
        config.fortressActive = false;
        config.bastionActive = false;
        config.strongholdActive = false;
        config.shipActive = false;

        BlockPos spawnPos = overworld.getSpawnPoint().getPos();

        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            // 5. Reset Player Vitals
            p.changeGameMode(GameMode.SURVIVAL);
            p.getInventory().clear();
            p.getHungerManager().setFoodLevel(20);

            // Apply configured max health on reset
            GSREvents.applyMaxHealth(p, config.maxHearts);

            p.clearStatusEffects();
            p.setFireTicks(0);
            p.setOnFire(false);
            p.setExperienceLevel(0);
            p.setExperiencePoints(0);

            // 6. Teleport to Spawn
            p.teleport(overworld,
                    spawnPos.getX() + 0.5,
                    (double) spawnPos.getY(),
                    spawnPos.getZ() + 0.5,
                    java.util.Collections.emptySet(),
                    0.0f,
                    0.0f,
                    true
            );
            p.setSpawnPoint(null, false);

            // 7. Revoke Advancements
            PlayerAdvancementTracker tracker = p.getAdvancementTracker();
            server.getAdvancementLoader().getAdvancements().forEach(entry -> {
                AdvancementProgress progress = tracker.getProgress(entry);
                for (String criteria : progress.getObtainedCriteria()) {
                    tracker.revokeCriterion(entry, criteria);
                }
            });
        }

        // 8. Final Sync
        GSRMain.saveAndSync(server);
        server.getPlayerManager().broadcast(Text.literal("§6§l[GSR] Run Reset, Go!"), false);
    }

    private static void notifyOps(ServerCommandSource source, String message) {
        Text notification = Text.literal(message);
        source.getServer().getPlayerManager().getPlayerList().forEach(player -> {
            if (CommandManager.ADMINS_CHECK.allows(player.getCommandSource().getPermissions())) {
                player.sendMessage(notification, false);
            }
        });
        LOGGER.info(notification.getString());
    }
}