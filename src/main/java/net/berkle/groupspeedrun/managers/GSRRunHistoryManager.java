package net.berkle.groupspeedrun.managers;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import net.berkle.groupspeedrun.GSREvents;
import net.berkle.groupspeedrun.GSRStats;
import net.berkle.groupspeedrun.util.GSRFormatUtil;
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
 * Manages the end-of-run lifecycle: calculating awards based on tracked stats,
 * broadcasting results to chat, and saving run data to permanent JSON storage.
 */
public class GSRRunHistoryManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Logger LOGGER = LoggerFactory.getLogger("GSR-History");

    /**
     * Finalizes the speedrun data.
     * * @param status "SUCCESS" for dragon kills, "FAILURE" for player deaths.
     * @param loserName The name of the player who died (if any).
     * @param deathMsg The death message to broadcast.
     */
    public static void saveRun(MinecraftServer server, String status, String loserName, String deathMsg) {
        try {
            // Retrieve total ticks elapsed from the unified event system
            long totalTicks = GSREvents.getRunTicks(server);

            // Generate the final awards and statistics JSON
            JsonObject awards = calculateAwards(server, status, loserName);

            // Announce results in the server chat
            if (status.equalsIgnoreCase("SUCCESS")) {
                GSRBroadcastManager.broadcastVictory(server, totalTicks, awards);
            } else {
                GSRBroadcastManager.broadcastFailure(server, totalTicks, loserName, deathMsg, awards);
            }

            // Persist the data to a JSON file for future reference/leaderboards
            saveToFile(server, status, totalTicks, awards, loserName);

            // Wipe internal stat maps to prepare for a clean new run
            GSRStats.reset();
        } catch (Exception e) {
            LOGGER.error("Failed to finalize run history!", e);
        }
    }

    /**
     * Determines which players earn which titles.
     * Logic ensures a player generally only gets one "Main Stat" award.
     */
    public static JsonObject calculateAwards(MinecraftServer server, String status, String loserName) {
        List<ServerPlayerEntity> players = server.getPlayerManager().getPlayerList();
        JsonObject awards = new JsonObject();
        Set<UUID> assigned = new HashSet<>(); // Tracks players who already received a high-priority award

        // --- 1. PRIORITY #1: DRAGON WARRIOR ---
        // This is calculated first as it is the most prestigious award.
        if (!GSRStats.DRAGON_DAMAGE_MAP.isEmpty()) {
            setStat(players, "dragon_warrior", p -> (double) GSRStats.DRAGON_DAMAGE_MAP.getOrDefault(p.getUuid(), 0f), awards, assigned, true);
        } else if (status.equalsIgnoreCase("SUCCESS")) {
            awards.addProperty("dragon_warrior", "Environmental Damage");
            awards.addProperty("dragon_warrior_v", 0.0);
        }

        // Exclude the player who failed the run (died) from receiving positive awards
        players.stream()
                .filter(p -> p.getName().getString().equalsIgnoreCase(loserName))
                .findFirst()
                .ifPresent(p -> assigned.add(p.getUuid()));

        // --- 2. MAIN STAT MAPPING ---
        // LinkedHashMap maintains the order of priority for these awards.
        LinkedHashMap<String, ToDoubleFunction<ServerPlayerEntity>> statMap = new LinkedHashMap<>();

        // Highest damage dealt to all entities
        statMap.put("adc", p -> (double) GSRStats.TOTAL_DAMAGE_DEALT.getOrDefault(p.getUuid(), 0f));

        // NEW: Most Ender Pearls picked up during the run
        statMap.put("pearl_hoarder", p -> (double) GSRStats.ENDER_PEARLS_COLLECTED.getOrDefault(p.getUuid(), 0));

        // Most potions consumed
        statMap.put("brew_master", p -> (double) GSRStats.POTIONS_DRUNK.getOrDefault(p.getUuid(), 0));

        // Total blocks broken and placed
        statMap.put("builder", p -> (double) (GSRStats.BLOCKS_BROKEN.getOrDefault(p.getUuid(), 0) + GSRStats.BLOCKS_PLACED.getOrDefault(p.getUuid(), 0)));

        // Total HP restored
        statMap.put("healer", p -> (double) GSRStats.DAMAGE_HEALED.getOrDefault(p.getUuid(), 0f));

        // Total mobs killed (using vanilla stats)
        statMap.put("killer", p -> (double) p.getStatHandler().getStat(Stats.CUSTOM.getOrCreateStat(Stats.MOB_KILLS)));

        // Blaze Rod pickups during "Pog" moments (quickly after a kill)
        statMap.put("pog_champ", p -> (double) GSRStats.POG_CHAMP_COUNT.getOrDefault(p.getUuid(), 0));

        // Highest armor value achieved at any point
        statMap.put("defender", p -> (double) GSRStats.MAX_ARMOR_RATING.getOrDefault(p.getUuid(), 0));

        // Total blocks traveled
        statMap.put("sightseer", p -> (double) GSRStats.DISTANCE_MOVED.getOrDefault(p.getUuid(), 0f));

        // Total damage taken from any source
        statMap.put("tank", p -> (double) GSRStats.TOTAL_DAMAGE_TAKEN.getOrDefault(p.getUuid(), 0f));

        // --- 3. ASSIGNMENT PASSES ---
        // Pass 1: Assign awards to players who haven't received one yet (Unique Only)
        for (var entry : statMap.entrySet()) setStat(players, entry.getKey(), entry.getValue(), awards, assigned, true);

        // Pass 2: If a category is still empty (e.g., everyone already has an award), assign to the top player regardless
        for (var entry : statMap.entrySet()) {
            if (!awards.has(entry.getKey())) setStat(players, entry.getKey(), entry.getValue(), awards, assigned, false);
        }

        // --- 4. ROAST LOGIC ---
        // These awards are typically given during a failure to highlight "less-than-optimal" playstyles.
        if (status.equalsIgnoreCase("FAILURE") || status.equalsIgnoreCase("LIVE")) {
            // Coward: The person who took the least amount of damage
            setMinStat(players, "coward", p -> (double) GSRStats.TOTAL_DAMAGE_TAKEN.getOrDefault(p.getUuid(), 0f), awards);

            // Good For Nothing: The person with the fewest advancements completed
            setMinStat(players, "good_for_nothing", p -> {
                int count = 0;
                for (AdvancementEntry entry : server.getAdvancementLoader().getAdvancements()) {
                    if (entry.value().display().isPresent() && p.getAdvancementTracker().getProgress(entry).isDone()) count++;
                }
                return (double) count;
            }, awards);

            // Shuffler: The person who spent the most time looking in chests/inventories
            setStat(players, "shuffler", p -> (double) GSRStats.INVENTORIES_OPENED.getOrDefault(p.getUuid(), 0), awards, assigned, false);

            // Weakling: The person who dealt the least amount of total damage
            setMinStat(players, "weakling", p -> (double) GSRStats.TOTAL_DAMAGE_DEALT.getOrDefault(p.getUuid(), 0f), awards);
        }

        System.out.println("[GSR-Debug] Final Awards JSON: " + awards.toString());
        return awards;
    }

    /**
     * Utility to find the player with the MAXIMUM value for a stat and add them to the JSON.
     */
    private static void setStat(List<ServerPlayerEntity> players, String key, ToDoubleFunction<ServerPlayerEntity> extractor, JsonObject awards, Set<UUID> assigned, boolean uniqueOnly) {
        players.stream()
                .filter(p -> !uniqueOnly || !assigned.contains(p.getUuid())) // Respect priority/uniqueness
                .max(Comparator.comparingDouble(extractor))
                .ifPresent(p -> {
                    double val = extractor.applyAsDouble(p);
                    if (val > 0.001) { // Ensure the stat isn't empty/zero
                        awards.addProperty(key, p.getName().getString());
                        awards.addProperty(key + "_v", val);
                        assigned.add(p.getUuid());
                    }
                });
    }

    /**
     * Utility to find the player with the MINIMUM value for a stat. Used for "Roast" awards.
     */
    private static void setMinStat(List<ServerPlayerEntity> players, String key, ToDoubleFunction<ServerPlayerEntity> extractor, JsonObject awards) {
        players.stream()
                .min(Comparator.comparingDouble(extractor))
                .ifPresent(p -> {
                    awards.addProperty(key, p.getName().getString());
                    awards.addProperty(key + "_v", extractor.applyAsDouble(p));
                });
    }

    /**
     * Writes the run results into the /GSR_History folder as a JSON file.
     */
    private static void saveToFile(MinecraftServer server, String status, long ticks, JsonObject awards, String loser) {
        try {
            // Locate the "GSR_History" folder in the root directory
            File dir = new File(server.getRunDirectory().toFile(), "GSR_History");
            if (!dir.exists()) {
                if (dir.mkdirs()) {
                    LOGGER.info("Created history directory: " + dir.getAbsolutePath());
                }
            }

            boolean isWin = status.equalsIgnoreCase("SUCCESS");
            String datePart = LocalDateTime.now().format(DateTimeFormatter.ofPattern("MM-dd_HHmm"));
            String worldName = server.getSaveProperties().getLevelName();

            // Determine the primary identifier for the filename (Dragon Killer or Loser)
            String primaryName = isWin ?
                    (awards.has("dragon_warrior") ? awards.get("dragon_warrior").getAsString() : "Champions") :
                    ((loser != null && !loser.isEmpty()) ? loser : "Nobody");

            // Clean filename to prevent illegal characters
            String fileName = String.format("%s_%s_%s_%s.json", worldName, isWin ? "W" : "L", datePart, primaryName)
                    .replaceAll("[^a-zA-Z0-9._-]", "_");

            JsonObject root = new JsonObject();
            root.addProperty("status", status);
            root.addProperty("final_time_formatted", GSRFormatUtil.formatTime(ticks));
            root.add("awards", awards);

            // Write the JSON object to the file
            try (FileWriter writer = new FileWriter(new File(dir, fileName), StandardCharsets.UTF_8)) {
                GSON.toJson(root, writer);
            }
        } catch (Exception e) {
            LOGGER.error("History save failed", e);
        }
    }
}