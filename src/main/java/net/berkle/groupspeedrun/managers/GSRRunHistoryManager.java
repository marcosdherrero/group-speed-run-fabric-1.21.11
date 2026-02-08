package net.berkle.groupspeedrun.managers;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import net.berkle.groupspeedrun.GSREvents;
import net.berkle.groupspeedrun.GSRStats;
import net.berkle.groupspeedrun.util.GSRFormatUtil; // Centralized formatting
import net.minecraft.advancement.AdvancementEntry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.stat.Stats;
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
 * Manages the end-of-run lifecycle: calculating awards, broadcasting results,
 * and saving run data to permanent JSON storage.
 */
public class GSRRunHistoryManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Logger LOGGER = LoggerFactory.getLogger("GSR-History");

    /**
     * Triggers the finalization of a speedrun.
     * * @param status "SUCCESS" for dragon kills, "FAILURE" for player deaths.
     */
    public static void saveRun(MinecraftServer server, String status, String loserName, String deathMsg) {
        try {
            // Use the unified event system to get final ticks
            long totalTicks = GSREvents.getRunTicks(server);

            // Generate the stats JSON
            JsonObject awards = calculateAwards(server, status, loserName);

            // Handle the chat announcements
            if (status.equalsIgnoreCase("SUCCESS")) {
                GSRBroadcastManager.broadcastVictory(server, totalTicks, awards);
            } else {
                GSRBroadcastManager.broadcastFailure(server, totalTicks, loserName, deathMsg, awards);
            }

            // Write to disk in config/groupspeedrun/history/
            saveToFile(server, status, totalTicks, awards, loserName);

            // Reset internal maps for the next run
            GSRStats.reset();
        } catch (Exception e) {
            LOGGER.error("Failed to finalize run history!", e);
        }
    }

    /**
     * Calculates which players earned which titles based on their stats.
     */
    public static JsonObject calculateAwards(MinecraftServer server, String status, String loserName) {
        List<ServerPlayerEntity> players = server.getPlayerManager().getPlayerList();
        JsonObject awards = new JsonObject();
        Set<UUID> assigned = new HashSet<>();

        // 1. Dragon Warrior (Priority #1)
        if (!GSRStats.DRAGON_DAMAGE_MAP.isEmpty()) {
            setStat(players, "dragon_warrior", p -> (double) GSRStats.DRAGON_DAMAGE_MAP.getOrDefault(p.getUuid(), 0f), awards, assigned, true);
        } else if (status.equalsIgnoreCase("SUCCESS")) {
            awards.addProperty("dragon_warrior", "Environmental Damage");
            awards.addProperty("dragon_warrior_v", 0.0);
        }

        // Exclude the player who died from receiving positive awards
        players.stream()
                .filter(p -> p.getName().getString().equalsIgnoreCase(loserName))
                .findFirst()
                .ifPresent(p -> assigned.add(p.getUuid()));

        // 2. Main Stat Mapping - NOW USING GSRStats INSTEAD OF VANILLA STATS
        LinkedHashMap<String, ToDoubleFunction<ServerPlayerEntity>> statMap = new LinkedHashMap<>();

        // CRITICAL FIX: Use TOTAL_DAMAGE_DEALT (GSRStats) instead of getStat()
        statMap.put("adc", p -> (double) GSRStats.TOTAL_DAMAGE_DEALT.getOrDefault(p.getUuid(), 0f));

        statMap.put("brew_master", p -> (double) GSRStats.POTIONS_DRUNK.getOrDefault(p.getUuid(), 0));
        statMap.put("builder", p -> (double) (GSRStats.BLOCKS_BROKEN.getOrDefault(p.getUuid(), 0) + GSRStats.BLOCKS_PLACED.getOrDefault(p.getUuid(), 0)));
        statMap.put("healer", p -> (double) GSRStats.DAMAGE_HEALED.getOrDefault(p.getUuid(), 0f));

        // Killer can stay vanilla as it's usually reliable, or use a custom tracker if needed
        statMap.put("killer", p -> (double) p.getStatHandler().getStat(Stats.CUSTOM.getOrCreateStat(Stats.MOB_KILLS)));

        statMap.put("pog_champ", p -> (double) GSRStats.POG_CHAMP_COUNT.getOrDefault(p.getUuid(), 0));
        statMap.put("defender", p -> (double) GSRStats.MAX_ARMOR_RATING.getOrDefault(p.getUuid(), 0));

        // CRITICAL FIX: Use DISTANCE_MOVED (GSRStats)
        statMap.put("sightseer", p -> (double) GSRStats.DISTANCE_MOVED.getOrDefault(p.getUuid(), 0f));

        // CRITICAL FIX: Use TOTAL_DAMAGE_TAKEN (GSRStats)
        statMap.put("tank", p -> (double) GSRStats.TOTAL_DAMAGE_TAKEN.getOrDefault(p.getUuid(), 0f));

        // 3. Assignment Passes
        for (var entry : statMap.entrySet()) setStat(players, entry.getKey(), entry.getValue(), awards, assigned, true);
        for (var entry : statMap.entrySet()) {
            if (!awards.has(entry.getKey())) setStat(players, entry.getKey(), entry.getValue(), awards, assigned, false);
        }

        // 4. Roast Logic
        if (status.equalsIgnoreCase("FAILURE") || status.equalsIgnoreCase("LIVE")) {
            // Roast Fix: Use GSRStats for Coward (least damage taken) and Weakling (least damage done)
            setMinStat(players, "coward", p -> (double) GSRStats.TOTAL_DAMAGE_TAKEN.getOrDefault(p.getUuid(), 0f), awards);

            setMinStat(players, "good_for_nothing", p -> {
                int count = 0;
                for (AdvancementEntry entry : server.getAdvancementLoader().getAdvancements()) {
                    if (entry.value().display().isPresent() && p.getAdvancementTracker().getProgress(entry).isDone()) count++;
                }
                return (double) count;
            }, awards);

            setStat(players, "shuffler", p -> (double) GSRStats.INVENTORIES_OPENED.getOrDefault(p.getUuid(), 0), awards, assigned, false);

            // Roast Fix: Use TOTAL_DAMAGE_DEALT
            setMinStat(players, "weakling", p -> (double) GSRStats.TOTAL_DAMAGE_DEALT.getOrDefault(p.getUuid(), 0f), awards);
        }

        System.out.println("[GSR-Debug] Final Awards JSON: " + awards.toString());
        return awards;
    }

    private static void setStat(List<ServerPlayerEntity> players, String key, ToDoubleFunction<ServerPlayerEntity> extractor, JsonObject awards, Set<UUID> assigned, boolean uniqueOnly) {
        players.stream()
                .filter(p -> !uniqueOnly || !assigned.contains(p.getUuid()))
                .max(Comparator.comparingDouble(extractor))
                .ifPresent(p -> {
                    double val = extractor.applyAsDouble(p);
                    if (val > 0.001) { // Increased precision check
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

    /**
     * Saves the final run data to config/groupspeedrun/history/
     */
    private static void saveToFile(MinecraftServer server, String status, long ticks, JsonObject awards, String loser) {
        try {
            // Change the path here. "GSR_History" will be at the same level as your "world" and "config" folders.
            File dir = new File(server.getRunDirectory().toFile(), "GSR_History");
            if (!dir.exists()) {
                if (dir.mkdirs()) {
                    LOGGER.info("Created history directory: " + dir.getAbsolutePath());
                }
            }

            boolean isWin = status.equalsIgnoreCase("SUCCESS");
            String datePart = LocalDateTime.now().format(DateTimeFormatter.ofPattern("MM-dd_HHmm"));
            String worldName = server.getSaveProperties().getLevelName();

            // Select the most prominent name for the filename
            String primaryName = isWin ?
                    (awards.has("dragon_warrior") ? awards.get("dragon_warrior").getAsString() : "Champions") :
                    ((loser != null && !loser.isEmpty()) ? loser : "Nobody");

            String fileName = String.format("%s_%s_%s_%s.json", worldName, isWin ? "W" : "L", datePart, primaryName)
                    .replaceAll("[^a-zA-Z0-9._-]", "_");

            JsonObject root = new JsonObject();
            root.addProperty("status", status);

            // --- UPDATED: Using GSRFormatUtil for dynamic time formatting in logs ---
            root.addProperty("final_time_formatted", GSRFormatUtil.formatTime(ticks));
            root.add("awards", awards);

            try (FileWriter writer = new FileWriter(new File(dir, fileName), StandardCharsets.UTF_8)) {
                GSON.toJson(root, writer);
            }
        } catch (Exception e) {
            LOGGER.error("History save failed", e);
        }
    }
}