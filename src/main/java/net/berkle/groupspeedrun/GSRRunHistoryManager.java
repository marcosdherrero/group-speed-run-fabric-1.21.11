package net.berkle.groupspeedrun;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import net.minecraft.advancement.AdvancementEntry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.stat.Stats;
import net.minecraft.text.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileWriter;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.ToDoubleFunction;

/**
 * Manages the conclusion of a speedrun.
 * Calculates player "Awards," broadcasts results to chat, and saves a JSON report.
 */
public class GSRRunHistoryManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Logger LOGGER = LoggerFactory.getLogger("GSR-History");

    /**
     * Finalizes the run, calculates awards, and saves the history file.
     */
    public static void saveRun(MinecraftServer server, String status, String loserName, String deathMsg) {
        try {
            // Ensure the history directory exists
            File dir = new File(server.getRunDirectory().toFile(), "config/groupspeedrun/history");
            if (!dir.exists()) dir.mkdirs();

            List<ServerPlayerEntity> players = server.getPlayerManager().getPlayerList();

            // Use the established helper for accurate run time
            long totalTicks = GSREvents.getRunTicks(server);

            JsonObject awards = new JsonObject();
            Set<UUID> assigned = new HashSet<>();

            // 1. MANDATORY ASSIGNMENTS
            // Dragon Warrior: Priority award for most damage to the dragon
            // Efficiency: Only iterate if damage was actually recorded to prevent potential null-pointer crashes
            if (!GSRStats.DRAGON_DAMAGE_MAP.isEmpty()) {
                setStat(players, "dragon_warrior", p -> (double) GSRStats.DRAGON_DAMAGE_MAP.getOrDefault(p.getUuid(), 0f), awards, assigned, true);
            } else {
                // Fallback for environmental kills (e.g., bed explosion with missing attribution)
                awards.addProperty("dragon_warrior", "Environmental Damage");
                awards.addProperty("dragon_warrior_v", 0.0);
            }
            // Ensure the loser is marked so they don't accidentally get positive awards
            players.stream()
                    .filter(p -> p.getName().getString().equalsIgnoreCase(loserName))
                    .findFirst()
                    .ifPresent(p -> assigned.add(p.getUuid()));

            // 2. DEFINE MAIN STAT MAP (Ordered by importance)
            LinkedHashMap<String, ToDoubleFunction<ServerPlayerEntity>> statMap = new LinkedHashMap<>();
            statMap.put("adc", p -> (double) p.getStatHandler().getStat(Stats.CUSTOM.getOrCreateStat(Stats.DAMAGE_DEALT)) / 10.0);
            statMap.put("brew_master", p -> (double) GSRStats.POTIONS_DRUNK.getOrDefault(p.getUuid(), 0));
            statMap.put("builder", p -> (double) (GSRStats.BLOCKS_BROKEN.getOrDefault(p.getUuid(), 0) + GSRStats.BLOCKS_PLACED.getOrDefault(p.getUuid(), 0)));
            statMap.put("healer", p -> (double) GSRStats.DAMAGE_HEALED.getOrDefault(p.getUuid(), 0f) / 2.0);
            statMap.put("killer", p -> (double) p.getStatHandler().getStat(Stats.CUSTOM.getOrCreateStat(Stats.MOB_KILLS)));
            statMap.put("pog_champ", p -> (double) GSRStats.POG_CHAMP_COUNT.getOrDefault(p.getUuid(), 0));
            statMap.put("defender", p -> (double) GSRStats.MAX_ARMOR_RATING.getOrDefault(p.getUuid(), 0));
            statMap.put("sightseer", p -> (double) p.getStatHandler().getStat(Stats.CUSTOM.getOrCreateStat(Stats.WALK_ONE_CM)) / 100.0);
            statMap.put("tank", p -> (double) p.getStatHandler().getStat(Stats.CUSTOM.getOrCreateStat(Stats.DAMAGE_TAKEN)) / 10.0);

            // 3. UNIQUE PLAYER PASS (One award per person where possible)
            for (var entry : statMap.entrySet()) {
                setStat(players, entry.getKey(), entry.getValue(), awards, assigned, true);
            }

            // 4. FILLER PASS (Fill remaining awards with best fits)
            for (var entry : statMap.entrySet()) {
                if (!awards.has(entry.getKey())) {
                    setStat(players, entry.getKey(), entry.getValue(), awards, assigned, false);
                }
            }

            // 5. LOSS STATS (Specific roasting for failed runs)
            if (status.equalsIgnoreCase("FAILURE")) {
                setMinStat(players, "coward", p -> (double) p.getStatHandler().getStat(Stats.CUSTOM.getOrCreateStat(Stats.DAMAGE_TAKEN)), awards);
                setStat(players, "good_for_nothing", p -> {
                    int count = 0;
                    for (AdvancementEntry entry : server.getAdvancementLoader().getAdvancements()) {
                        if (entry.value().display().isPresent() && p.getAdvancementTracker().getProgress(entry).isDone()) count++;
                    }
                    return (double) count;
                }, awards, assigned, false);
                setStat(players, "shuffler", p -> (double) GSRStats.INVENTORIES_OPENED.getOrDefault(p.getUuid(), 0), awards, assigned, false);
                setMinStat(players, "weakling", p -> (double) p.getStatHandler().getStat(Stats.CUSTOM.getOrCreateStat(Stats.DAMAGE_DEALT)), awards);
            }

            // 6. BROADCAST & SAVE
            if (status.equalsIgnoreCase("SUCCESS")) {
                broadcastVictory(server, totalTicks, awards);
            } else {
                broadcastFailure(server, totalTicks, loserName, deathMsg, awards);
            }

            saveToFile(server, status, totalTicks, awards, loserName);

            // Clean up stats for the next potential world/run
            GSRStats.reset();

        } catch (Exception e) {
            LOGGER.error("Failed to finalize run history!", e);
        }
    }

    private static void setStat(List<ServerPlayerEntity> players, String key, ToDoubleFunction<ServerPlayerEntity> extractor, JsonObject awards, Set<UUID> assigned, boolean uniqueOnly) {
        players.stream()
                .filter(p -> !uniqueOnly || !assigned.contains(p.getUuid()))
                .max(Comparator.comparingDouble(extractor))
                .ifPresent(p -> {
                    double val = extractor.applyAsDouble(p);
                    if (val > 0) {
                        awards.addProperty(key, p.getName().getString());
                        awards.addProperty(key + "_v", val);
                        assigned.add(p.getUuid());
                    }
                });
    }

    private static void setMinStat(List<ServerPlayerEntity> players, String key, ToDoubleFunction<ServerPlayerEntity> extractor, JsonObject awards) {
        players.stream()
                .min(Comparator.comparingDouble(extractor))
                .ifPresent(p -> {
                    awards.addProperty(key, p.getName().getString());
                    awards.addProperty(key + "_v", extractor.applyAsDouble(p));
                });
    }

    private static void broadcastVictory(MinecraftServer server, long ticks, JsonObject awards) {
        var pm = server.getPlayerManager();
        pm.broadcast(Text.literal("§a§l━━━━━━━━━ VICTORY ━━━━━━━━━"), false);
        pm.broadcast(Text.literal("§6Final Time: §f" + formatTime(ticks)), false);

        display(pm, awards, "dragon_warrior", "§5🐉 Dragon Warrior", " dmg");
        display(pm, awards, "adc", "§6🏹 ADC", " HP");
        display(pm, awards, "defender", "§b🛡 Defender", " armor");
        display(pm, awards, "brew_master", "§b🧪 Brew Master", " pots");
        display(pm, awards, "builder", "§2🔨 Builder", " blocks");
        display(pm, awards, "healer", "§d❤ Healer", " hearts");
        display(pm, awards, "killer", "§4💀 Serial Killer", " kills");
        display(pm, awards, "pog_champ", "§e🔥 Pog Champ", " rods");
        display(pm, awards, "tank", "§9🛡 Tank", " dmg taken");

        pm.broadcast(Text.literal("§a§l━━━━━━━━━━━━━━━━━━━━━━━━━━"), false);
    }

    private static void broadcastFailure(MinecraftServer server, long ticks, String loser, String msg, JsonObject awards) {
        var pm = server.getPlayerManager();
        pm.broadcast(Text.literal("§4§l━━━━━━━━━ RUN RUINED ━━━━━━━━━"), false);
        pm.broadcast(Text.literal("§6Final Time: §f" + formatTime(ticks)), false);
        pm.broadcast(Text.literal("§4💀 Disgrace: §c" + loser), false);
        pm.broadcast(Text.literal("§7\"" + msg + "\""), false);

        display(pm, awards, "coward", "§e🏃 Coward", " dmg taken");
        display(pm, awards, "good_for_nothing", "§a🌟 Good for Nothing", " adv");
        display(pm, awards, "shuffler", "§3🗃 Professional Shuffler", " opens");
        display(pm, awards, "weakling", "§f🍼 Weakling", " dmg");

        pm.broadcast(Text.literal("§4§l━━━━━━━━━━━━━━━━━━━━━━━━━━"), false);
    }

    private static void display(net.minecraft.server.PlayerManager pm, JsonObject awards, String key, String label, String unit) {
        if (awards.has(key)) {
            double val = awards.get(key + "_v").getAsDouble();
            String fVal = (val == (long) val) ? String.format("%d", (long) val) : String.format("%.1f", val);
            pm.broadcast(Text.literal(label + ": §b" + awards.get(key).getAsString() + " §f(" + fVal + unit + ")"), false);
        }
    }

    private static void saveToFile(MinecraftServer server, String status, long ticks, JsonObject awards, String loser) {
        try {
            boolean isWin = status.equalsIgnoreCase("SUCCESS");
            String indicator = isWin ? "W" : "L";
            String primaryName = isWin ? (awards.has("dragon_warrior") ? awards.get("dragon_warrior").getAsString() : "Champions")
                    : ((loser != null && !loser.isEmpty()) ? loser : "Nobody");

            String datePart = LocalDateTime.now().format(DateTimeFormatter.ofPattern("MM-dd_HHmm"));
            String worldName = server.getSaveProperties().getLevelName();

            String fileName = String.format("%s_%s_%s_%s.json", indicator, datePart, primaryName, worldName)
                    .replaceAll("[^a-zA-Z0-9._-]", "_");

            JsonObject root = new JsonObject();
            root.addProperty("status", status);
            root.addProperty("timestamp", LocalDateTime.now().toString());
            root.addProperty("world", worldName);
            root.addProperty("final_time_ticks", ticks);
            root.addProperty("final_time_formatted", formatTime(ticks));
            root.add("awards", awards);

            File historyDir = new File(server.getRunDirectory().toFile(), "config/groupspeedrun/history");
            if (!historyDir.exists()) historyDir.mkdirs();

            File historyFile = new File(historyDir, fileName);
            try (FileWriter writer = new FileWriter(historyFile, StandardCharsets.UTF_8)) {
                GSON.toJson(root, writer);
            }
        } catch (Exception e) {
            LOGGER.error("Failed to write history file", e);
        }
    }

    public static String formatTime(long ticks) {
        long ms = ticks * 50;
        long h = ms / 3600000;
        long m = (ms / 60000) % 60;
        long s = (ms / 1000) % 60;
        long f = (ms % 1000) / 10;
        return h > 0 ? String.format("%d:%02d:%02d.%02d", h, m, s, f) : String.format("%02d:%02d.%02d", m, s, f);
    }
}