package net.berkle.groupspeedrun;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.mojang.datafixers.util.Pair;
import net.berkle.groupspeedrun.config.GSRConfigPayload;
import net.berkle.groupspeedrun.config.GSRConfigPlayer;
import net.berkle.groupspeedrun.managers.GSRBroadcastManager;
import net.berkle.groupspeedrun.managers.GSRSplitManager;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.advancement.AdvancementProgress;
import net.minecraft.advancement.PlayerAdvancementTracker;
import net.minecraft.nbt.NbtCompound;
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
import net.minecraft.world.gen.structure.Structure;
import net.minecraft.world.gen.structure.StructureKeys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static net.minecraft.server.command.CommandManager.literal;
import static net.minecraft.server.command.CommandManager.argument;

public class GSRCommands {
    private static final Logger LOGGER = LoggerFactory.getLogger("GSR-Commands");

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        LiteralCommandNode<ServerCommandSource> gsrRoot = dispatcher.register(literal("gsr")
                .requires(source -> CommandManager.ALWAYS_PASS_CHECK.allows(source.getPermissions()))

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

                .then(literal("pause")
                        .requires(source -> {
                            var config = GSRMain.CONFIG;
                            return CommandManager.ADMINS_CHECK.allows(source.getPermissions()) &&
                                    (config != null && !config.isFailed && !config.isVictorious);
                        })
                        .executes(context -> togglePause(context.getSource(), true)))
                .then(literal("resume")
                        .requires(source -> {
                            var config = GSRMain.CONFIG;
                            return CommandManager.ADMINS_CHECK.allows(source.getPermissions()) &&
                                    (config != null && !config.isFailed && !config.isVictorious);
                        })
                        .executes(context -> togglePause(context.getSource(), false)))
                .then(literal("reset")
                        .requires(source -> {
                            var config = GSRMain.CONFIG;
                            boolean isAdmin = CommandManager.ADMINS_CHECK.allows(source.getPermissions());
                            boolean isFinished = (config != null && (config.isFailed || config.isVictorious));
                            return isAdmin || isFinished;
                        })
                        .executes(context -> {
                            executeReset(context.getSource().getServer());
                            context.getSource().sendFeedback(() -> Text.literal("§6[GSR] §aRun has been fully reset!"), true);
                            return 1;
                        }))

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
                        .then(literal("exclude_toggle")
                                .then(argument("player", net.minecraft.command.argument.EntityArgumentType.player())
                                        .executes(context -> {
                                            ServerPlayerEntity target = net.minecraft.command.argument.EntityArgumentType.getPlayer(context, "player");
                                            var excluded = GSRMain.CONFIG.excludedPlayers;
                                            boolean wasExcluded = excluded.contains(target.getUuid());

                                            if (wasExcluded) {
                                                excluded.remove(target.getUuid());
                                                // GLOBAL BROADCAST: Everyone sees the player is back in
                                                context.getSource().getServer().getPlayerManager().broadcast(
                                                        Text.literal("§6[GSR] §f" + target.getName().getString() + " §ais now INCLUDED §7in group mechanics."), false);
                                            } else {
                                                excluded.add(target.getUuid());
                                                // GLOBAL BROADCAST: Everyone sees the player is out
                                                context.getSource().getServer().getPlayerManager().broadcast(
                                                        Text.literal("§6[GSR] §f" + target.getName().getString() + " §cis now EXCLUDED §7from group mechanics."), false);
                                            }

                                            GSRMain.saveAndSync(context.getSource().getServer());
                                            return 1;
                                        })))
                        .then(literal("max_hp")
                                // No argument provided: Reset to default (10 hearts)
                                .executes(context -> {
                                    updateMaxHearts(context.getSource().getServer(), 10.0f);
                                    context.getSource().sendFeedback(() -> Text.literal("§6[GSR] Global Max Health: §aReset to default (10)"), true);
                                    return 1;
                                })
                                // Argument provided: Set specific amount
                                .then(argument("amount", FloatArgumentType.floatArg(0.5f, 100.0f))
                                        .executes(context -> {
                                            float val = FloatArgumentType.getFloat(context, "amount");
                                            updateMaxHearts(context.getSource().getServer(), val);
                                            context.getSource().sendFeedback(() -> Text.literal("§6[GSR] Global Max Health: §f" + val), true);
                                            return 1;
                                        }))))

                .then(literal("hud")
                        .then(literal("visibility_toggle").executes(context -> {
                            ServerPlayerEntity player = context.getSource().getPlayer();
                            if (player == null) return 0;
                            NbtCompound nbt = new NbtCompound();
                            nbt.putBoolean("toggleVisibility", true);
                            ServerPlayNetworking.send(player, new GSRConfigPayload(nbt));
                            return 1;
                        }))
                        .then(literal("side_toggle").executes(context -> {
                            ServerPlayerEntity player = context.getSource().getPlayer();
                            if (player == null) return 0;
                            NbtCompound nbt = new NbtCompound();
                            nbt.putBoolean("toggleSide", true);
                            ServerPlayNetworking.send(player, new GSRConfigPayload(nbt));
                            return 1;
                        }))
                        .then(literal("height_toggle").executes(context -> {
                            ServerPlayerEntity player = context.getSource().getPlayer();
                            if (player == null) return 0;
                            NbtCompound nbt = new NbtCompound();
                            nbt.putBoolean("toggleHeight", true);
                            ServerPlayNetworking.send(player, new GSRConfigPayload(nbt));
                            return 1;
                        }))
                        .then(literal("scale")
                                .then(literal("overall")
                                        .then(argument("value", FloatArgumentType.floatArg(GSRConfigPlayer.MIN_OVERALL_SCALE, GSRConfigPlayer.MAX_OVERALL_SCALE))
                                                .executes(context -> {
                                                    ServerPlayerEntity player = context.getSource().getPlayer();
                                                    if (player == null) return 0;
                                                    float val = FloatArgumentType.getFloat(context, "value");
                                                    NbtCompound nbt = new NbtCompound();
                                                    nbt.putFloat("overallScale", val);
                                                    ServerPlayNetworking.send(player, new GSRConfigPayload(nbt));
                                                    context.getSource().sendFeedback(() -> Text.literal("§6[GSR] §7Overall Scale set to: §f" + val), false);
                                                    return 1;
                                                })))
                                .then(literal("timer")
                                        .then(argument("value", FloatArgumentType.floatArg(GSRConfigPlayer.MIN_TIMER_SCALE, GSRConfigPlayer.MAX_TIMER_SCALE))
                                                .executes(context -> {
                                                    ServerPlayerEntity player = context.getSource().getPlayer();
                                                    if (player == null) return 0;
                                                    float val = FloatArgumentType.getFloat(context, "value");
                                                    NbtCompound nbt = new NbtCompound();
                                                    nbt.putFloat("timerScale", val);
                                                    ServerPlayNetworking.send(player, new GSRConfigPayload(nbt));
                                                    context.getSource().sendFeedback(() -> Text.literal("§6[GSR] §7Timer Scale set to: §f" + val), false);
                                                    return 1;
                                                })))
                                .then(literal("locate")
                                        .then(argument("value", FloatArgumentType.floatArg(GSRConfigPlayer.MIN_LOCATE_SCALE, GSRConfigPlayer.MAX_LOCATE_SCALE))
                                                .executes(context -> {
                                                    ServerPlayerEntity player = context.getSource().getPlayer();
                                                    if (player == null) return 0;
                                                    float val = FloatArgumentType.getFloat(context, "value");
                                                    NbtCompound nbt = new NbtCompound();
                                                    nbt.putFloat("locateScale", val);
                                                    ServerPlayNetworking.send(player, new GSRConfigPayload(nbt));
                                                    context.getSource().sendFeedback(() -> Text.literal("§6[GSR] §7Locate Scale set to: §f" + val), false);
                                                    return 1;
                                                }))))
                        .then(literal("reset").executes(context -> {
                            ServerPlayerEntity player = context.getSource().getPlayer();
                            if (player == null) return 0;
                            NbtCompound nbt = new NbtCompound();
                            nbt.putBoolean("resetHud", true);
                            ServerPlayNetworking.send(player, new GSRConfigPayload(nbt));
                            context.getSource().sendFeedback(() -> Text.literal("§6[GSR] §aHUD settings reset to defaults."), false);
                            return 1;
                        })))

                .then(literal("locate")
                        .requires(source -> {
                            var config = GSRMain.CONFIG;
                            boolean isAdmin = CommandManager.ADMINS_CHECK.allows(source.getPermissions());
                            boolean isFinished = (config != null && (config.isFailed || config.isVictorious));
                            return isAdmin || isFinished;
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

    private static void displayStatus(ServerCommandSource source) {
        var worldConfig = GSRMain.CONFIG;
        ServerPlayerEntity player = source.getPlayer();
        if (player == null) return;

        var pConfig = GSRMain.getPlayerConfig(player);

        source.sendFeedback(() -> Text.literal(
                "§6§l[GSR] Status Overview\n" +
                        "§e--- Global Run Settings ---\n" +
                        "§f- Group Death: " + (worldConfig.groupDeathEnabled ? "§aON" : "§cOFF") + "\n" +
                        "§f- Shared HP: " + (worldConfig.sharedHealthEnabled ? "§aON" : "§cOFF") + "\n" +
                        "§f- Max Hearts: §c" + worldConfig.maxHearts + "\n" +
                        "§e--- Your HUD Preferences ---\n" +
                        "§f- Visibility: " + getHudModeName(pConfig.hudMode) + "\n" +
                        "§f- Timer Side: §b" + (pConfig.timerHudOnRight ? "Right" : "Left") + "\n" +
                        "§f- Locate Bar: §b" + (pConfig.locateHudOnTop ? "Top" : "Bottom") + "\n" +
                        "§f- HUD Scale: §b" + pConfig.hudOverallScale + "x §7(Min: " + GSRConfigPlayer.MIN_OVERALL_SCALE + " | Max: " + GSRConfigPlayer.MAX_OVERALL_SCALE + ")"
        ), false);
    }

    /**
     * Helper to turn the integer hudMode into a readable string
     */
    private static String getHudModeName(int mode) {
        return switch (mode) {
            case 0 -> "§aVisible (Full)";
            case 1 -> "§eTimer Only";
            case 2 -> "§eLocate Only";
            case 3 -> "§cHidden";
            default -> "§7Unknown";
        };
    }

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
        }
        GSRMain.saveAndSync(source.getServer());
        return 1;
    }

    private static void updateMaxHearts(MinecraftServer server, float amount) {
        GSRMain.CONFIG.maxHearts = amount;
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            GSREvents.applyMaxHealth(p, amount);
        }
        GSRMain.saveAndSync(server);
    }

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
                if (config == null) return 0;

                switch (type.toLowerCase()) {
                    case "fortress" -> { config.fortressX = pos.getX(); config.fortressZ = pos.getZ(); config.fortressActive = true; }
                    case "bastion" -> { config.bastionX = pos.getX(); config.bastionZ = pos.getZ(); config.bastionActive = true; }
                    case "stronghold" -> { config.strongholdX = pos.getX(); config.strongholdZ = pos.getZ(); config.strongholdActive = true; }
                    case "ship" -> { config.shipX = pos.getX(); config.shipZ = pos.getZ(); config.shipActive = true; }
                }

                GSRMain.saveAndSync(source.getServer());

                // GLOBAL BROADCAST: Notify everyone of the found coordinates
                source.getServer().getPlayerManager().broadcast(
                        Text.literal("§6[GSR] §f" + type + " §7located at §a" + pos.getX() + ", " + pos.getZ() + " §7(HUD Updated)"), false);

                return 1;
            }
        }
        source.sendError(Text.literal("§6[GSR] §cCould not find " + type + " in this dimension."));
        return 0;
    }

    private static void executeReset(MinecraftServer server) {
        net.minecraft.server.world.ServerWorld overworld = server.getOverworld();
        overworld.getGameRules().setValue(net.minecraft.world.rule.GameRules.SEND_COMMAND_FEEDBACK, false, server);

        var config = GSRMain.CONFIG;
        config.startTime = -1;
        config.isTimerFrozen = false;
        config.frozenTime = 0;
        config.isFailed = false;
        config.isVictorious = false;
        GSRSplitManager.resetSplits();

        config.fortressActive = false;
        config.bastionActive = false;
        config.strongholdActive = false;
        config.shipActive = false;

        overworld.setTimeOfDay(0);
        GSRStats.reset();
        GSREvents.resetHealth();

        BlockPos spawnPos = overworld.getSpawnPoint().getPos();
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            p.changeGameMode(net.minecraft.world.GameMode.SURVIVAL);
            p.getInventory().clear();
            p.getHungerManager().setFoodLevel(20);
            p.setHealth(p.getMaxHealth());
            GSREvents.applyMaxHealth(p, config.maxHearts);
            p.clearStatusEffects();
            p.setFireTicks(0);
            p.setExperienceLevel(0);
            p.setExperiencePoints(0);
            p.teleport(overworld, spawnPos.getX() + 0.5, spawnPos.getY(), spawnPos.getZ() + 0.5, java.util.Collections.emptySet(), 0.0f, 0.0f, true);

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

    private static void clearAllLocates(MinecraftServer server) {
        var config = GSRMain.CONFIG;
        if (config == null) return;
        config.fortressActive = false;
        config.bastionActive = false;
        config.strongholdActive = false;
        config.shipActive = false;
        GSRMain.saveAndSync(server);
    }
}