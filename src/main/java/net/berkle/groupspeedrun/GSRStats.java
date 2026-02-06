package net.berkle.groupspeedrun;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.WorldSavePath;

import java.io.*;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages tracking and persistence of player statistics throughout a run.
 * These stats are used to determine end-of-run awards (e.g., "Dragon Warrior").
 * * Uses ConcurrentHashMaps to ensure thread-safety, as stats can be updated
 * from multiple threads (e.g., block breaking and entity ticking).
 */
public class GSRStats {

    /**
     * Required for GSON to correctly handle Map keys that are objects (UUIDs)
     * rather than simple Strings.
     */
    private static final Gson GSON = new GsonBuilder()
            .enableComplexMapKeySerialization()
            .setPrettyPrinting()
            .create();

    // --- [ STATISTICAL MAPS ] ---
    // Using ConcurrentHashMap to prevent ConcurrentModificationExceptions during autosaves
    public static Map<UUID, Integer> INVENTORIES_OPENED = new ConcurrentHashMap<>();
    public static Map<UUID, Integer> BLOCKS_PLACED = new ConcurrentHashMap<>();
    public static Map<UUID, Integer> BLOCKS_BROKEN = new ConcurrentHashMap<>();
    public static Map<UUID, Float> DAMAGE_HEALED = new ConcurrentHashMap<>();
    public static Map<UUID, Float> DRAGON_DAMAGE_MAP = new ConcurrentHashMap<>();
    public static Map<UUID, Long> LAST_BLAZE_KILL_TIME = new ConcurrentHashMap<>();
    public static Map<UUID, Integer> POG_CHAMP_COUNT = new ConcurrentHashMap<>();
    public static Map<UUID, Integer> POTIONS_DRUNK = new ConcurrentHashMap<>();
    public static Map<UUID, Integer> MAX_ARMOR_RATING = new ConcurrentHashMap<>();

    /**
     * Resets all statistical data to zero/empty.
     * Called by GSRMain when a world starts or by GSRCommands on a manual reset.
     */
    public static void reset() {
        INVENTORIES_OPENED.clear();
        BLOCKS_PLACED.clear();
        BLOCKS_BROKEN.clear();
        DAMAGE_HEALED.clear();
        DRAGON_DAMAGE_MAP.clear();
        LAST_BLAZE_KILL_TIME.clear();
        POG_CHAMP_COUNT.clear();
        POTIONS_DRUNK.clear();
        MAX_ARMOR_RATING.clear();
        GSRMain.LOGGER.info("[GSR] Statistics have been cleared.");
    }

    // --- [ ATOMIC HELPERS ] ---

    /**
     * Increments an integer stat for a player.
     */
    public static void addInt(Map<UUID, Integer> map, UUID uuid, int amount) {
        if (uuid == null || amount == 0) return;
        map.merge(uuid, amount, Integer::sum);
    }

    /**
     * Increments a float stat for a player (used for damage and healing).
     */
    public static void addFloat(Map<UUID, Float> map, UUID uuid, float amount) {
        if (uuid == null || amount <= 0.0f) return;
        map.merge(uuid, amount, Float::sum);
    }

    /**
     * Updates the highest armor value recorded for a player.
     */
    public static void updateMaxArmor(UUID uuid, int currentArmor) {
        if (uuid == null) return;
        MAX_ARMOR_RATING.merge(uuid, currentArmor, Math::max);
    }

    // --- [ PERSISTENCE ] ---

    /**
     * Saves all current stats to 'gsr_stats.json' in the world folder.
     */
    public static void save(MinecraftServer server) {
        if (server == null) return;

        File file = getStatsFile(server);
        try (Writer writer = new FileWriter(file)) {
            // Package maps into a record for clean JSON structure
            StatsContainer container = new StatsContainer(
                    INVENTORIES_OPENED, BLOCKS_PLACED, BLOCKS_BROKEN,
                    DAMAGE_HEALED, DRAGON_DAMAGE_MAP, LAST_BLAZE_KILL_TIME,
                    POG_CHAMP_COUNT, POTIONS_DRUNK, MAX_ARMOR_RATING
            );
            GSON.toJson(container, writer);
        } catch (IOException e) {
            GSRMain.LOGGER.error("[GSR] Failed to save stats file!", e);
        }
    }

    /**
     * Loads stats from the world folder to resume a run's statistics.
     */
    public static void load(MinecraftServer server) {
        if (server == null) return;

        File file = getStatsFile(server);
        if (!file.exists()) return;

        try (Reader reader = new FileReader(file)) {
            StatsContainer container = GSON.fromJson(reader, StatsContainer.class);
            if (container != null) {
                reset(); // Clear memory before populating from file

                if (container.inventoriesOpened != null) INVENTORIES_OPENED.putAll(container.inventoriesOpened);
                if (container.blocksPlaced != null) BLOCKS_PLACED.putAll(container.blocksPlaced);
                if (container.blocksBroken != null) BLOCKS_BROKEN.putAll(container.blocksBroken);
                if (container.damageHealed != null) DAMAGE_HEALED.putAll(container.damageHealed);
                if (container.dragonDamage != null) DRAGON_DAMAGE_MAP.putAll(container.dragonDamage);
                if (container.blazeKills != null) LAST_BLAZE_KILL_TIME.putAll(container.blazeKills);
                if (container.pogCount != null) POG_CHAMP_COUNT.putAll(container.pogCount);
                if (container.potionsDrunk != null) POTIONS_DRUNK.putAll(container.potionsDrunk);
                if (container.maxArmorRating != null) MAX_ARMOR_RATING.putAll(container.maxArmorRating);

                GSRMain.LOGGER.info("[GSR] Successfully loaded stats for {} players.", INVENTORIES_OPENED.size());
            }
        } catch (IOException e) {
            GSRMain.LOGGER.error("[GSR] Failed to load stats file!", e);
        }
    }

    /**
     * Helper to resolve the stats file path.
     */
    private static File getStatsFile(MinecraftServer server) {
        Path path = server.getSavePath(WorldSavePath.ROOT).resolve("gsr_stats.json");
        return path.toFile();
    }

    /**
     * Data transfer object for GSON serialization.
     * Uses names that result in clean, readable JSON keys.
     */
    private record StatsContainer(
            Map<UUID, Integer> inventoriesOpened,
            Map<UUID, Integer> blocksPlaced,
            Map<UUID, Integer> blocksBroken,
            Map<UUID, Float> damageHealed,
            Map<UUID, Float> dragonDamage,
            Map<UUID, Long> blazeKills,
            Map<UUID, Integer> pogCount,
            Map<UUID, Integer> potionsDrunk,
            Map<UUID, Integer> maxArmorRating
    ) {}
}