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
import java.util.concurrent.CompletableFuture;

/**
 * Manages tracking and persistence of player statistics.
 * WORLD-SPECIFIC: Stats are stored inside each world's save folder (data/gsr_stats.json).
 * ASYNC: Snapshotting happens on the main thread, while I/O happens on a background thread.
 */
public class GSRStats {

    private static final Gson GSON = new GsonBuilder()
            .enableComplexMapKeySerialization()
            .setPrettyPrinting()
            .create();

    private static volatile boolean isDirty = false;

    // --- [ STATS MAPS ] ---
    public static Map<UUID, Integer> INVENTORIES_OPENED = new ConcurrentHashMap<>();
    public static Map<UUID, Integer> BLOCKS_PLACED = new ConcurrentHashMap<>();
    public static Map<UUID, Integer> BLOCKS_BROKEN = new ConcurrentHashMap<>();
    public static Map<UUID, Float> DAMAGE_HEALED = new ConcurrentHashMap<>();
    public static Map<UUID, Float> DRAGON_DAMAGE_MAP = new ConcurrentHashMap<>();
    public static Map<UUID, Long> LAST_BLAZE_KILL_TIME = new ConcurrentHashMap<>();
    public static Map<UUID, Integer> POG_CHAMP_COUNT = new ConcurrentHashMap<>();
    public static Map<UUID, Integer> POTIONS_DRUNK = new ConcurrentHashMap<>();
    public static Map<UUID, Integer> MAX_ARMOR_RATING = new ConcurrentHashMap<>();

    // High-precision trackers that replace unreliable vanilla stats
    public static Map<UUID, Float> TOTAL_DAMAGE_DEALT = new ConcurrentHashMap<>();
    public static Map<UUID, Float> TOTAL_DAMAGE_TAKEN = new ConcurrentHashMap<>();
    public static Map<UUID, Float> DISTANCE_MOVED = new ConcurrentHashMap<>();

    /**
     * Clears all tracking maps. Called when a new run starts.
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
        TOTAL_DAMAGE_DEALT.clear();
        TOTAL_DAMAGE_TAKEN.clear();
        DISTANCE_MOVED.clear();

        isDirty = false;
        GSRMain.LOGGER.info("[GSR] Statistics have been reset.");
    }

    // --- [ STAT UPDATE HELPERS ] ---

    public static void addInt(Map<UUID, Integer> map, UUID uuid, int amount) {
        if (uuid == null || amount == 0) return;
        map.merge(uuid, amount, Integer::sum);
        isDirty = true;
    }

    public static void addFloat(Map<UUID, Float> map, UUID uuid, float amount) {
        if (uuid == null || amount <= 0.0f) return;
        map.merge(uuid, amount, Float::sum);
        isDirty = true;
        // System.out.println("[GSR-Debug] Stat Updated. New Total: " + map.get(uuid));
    }

    public static void updateMaxArmor(UUID uuid, int currentArmor) {
        if (uuid == null) return;
        MAX_ARMOR_RATING.merge(uuid, currentArmor, Math::max);
        isDirty = true;
    }

    // --- [ PERSISTENCE LOGIC ] ---

    public static void save(MinecraftServer server) {
        if (server == null || !isDirty) return;

        // Snapshot data on main thread to prevent ConcurrentModificationException during save
        StatsContainer snapshot = new StatsContainer(
                Map.copyOf(INVENTORIES_OPENED),
                Map.copyOf(BLOCKS_PLACED),
                Map.copyOf(BLOCKS_BROKEN),
                Map.copyOf(DAMAGE_HEALED),
                Map.copyOf(DRAGON_DAMAGE_MAP),
                Map.copyOf(LAST_BLAZE_KILL_TIME),
                Map.copyOf(POG_CHAMP_COUNT),
                Map.copyOf(POTIONS_DRUNK),
                Map.copyOf(MAX_ARMOR_RATING),
                Map.copyOf(TOTAL_DAMAGE_DEALT),
                Map.copyOf(TOTAL_DAMAGE_TAKEN),
                Map.copyOf(DISTANCE_MOVED)
        );

        File file = getStatsFile(server);
        isDirty = false;

        CompletableFuture.runAsync(() -> {
            if (file.getParentFile() != null) file.getParentFile().mkdirs();

            try (Writer writer = new OutputStreamWriter(new FileOutputStream(file), "UTF-8")) {
                GSON.toJson(snapshot, writer);
            } catch (IOException e) {
                GSRMain.LOGGER.error("[GSR] Async stats save failed!", e);
                isDirty = true; // Retry on next save tick
            }
        });
    }

    public static void load(MinecraftServer server) {
        if (server == null) return;

        File file = getStatsFile(server);
        if (!file.exists()) {
            reset();
            return;
        }

        try (Reader reader = new InputStreamReader(new FileInputStream(file), "UTF-8")) {
            StatsContainer container = GSON.fromJson(reader, StatsContainer.class);
            if (container != null) {
                reset();
                if (container.inventoriesOpened != null) INVENTORIES_OPENED.putAll(container.inventoriesOpened);
                if (container.blocksPlaced != null) BLOCKS_PLACED.putAll(container.blocksPlaced);
                if (container.blocksBroken != null) BLOCKS_BROKEN.putAll(container.blocksBroken);
                if (container.damageHealed != null) DAMAGE_HEALED.putAll(container.damageHealed);
                if (container.dragonDamage != null) DRAGON_DAMAGE_MAP.putAll(container.dragonDamage);
                if (container.blazeKills != null) LAST_BLAZE_KILL_TIME.putAll(container.blazeKills);
                if (container.pogCount != null) POG_CHAMP_COUNT.putAll(container.pogCount);
                if (container.potionsDrunk != null) POTIONS_DRUNK.putAll(container.potionsDrunk);
                if (container.maxArmorRating != null) MAX_ARMOR_RATING.putAll(container.maxArmorRating);
                if (container.totalDamageDealt != null) TOTAL_DAMAGE_DEALT.putAll(container.totalDamageDealt);
                if (container.totalDamageTaken != null) TOTAL_DAMAGE_TAKEN.putAll(container.totalDamageTaken);
                if (container.distanceMoved != null) DISTANCE_MOVED.putAll(container.distanceMoved);

                isDirty = false;
                GSRMain.LOGGER.info("[GSR] Stats successfully loaded.");
            }
        } catch (Exception e) {
            GSRMain.LOGGER.error("[GSR] Failed to load statistics!", e);
        }
    }

    private static File getStatsFile(MinecraftServer server) {
        Path path = server.getSavePath(WorldSavePath.ROOT).resolve("data").resolve("gsr_stats.json");
        return path.toFile();
    }

    private record StatsContainer(
            Map<UUID, Integer> inventoriesOpened,
            Map<UUID, Integer> blocksPlaced,
            Map<UUID, Integer> blocksBroken,
            Map<UUID, Float> damageHealed,
            Map<UUID, Float> dragonDamage,
            Map<UUID, Long> blazeKills,
            Map<UUID, Integer> pogCount,
            Map<UUID, Integer> potionsDrunk,
            Map<UUID, Integer> maxArmorRating,
            Map<UUID, Float> totalDamageDealt,
            Map<UUID, Float> totalDamageTaken,
            Map<UUID, Float> distanceMoved
    ) {}
}