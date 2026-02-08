package net.berkle.groupspeedrun.config;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.WorldSavePath;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Color;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

/**
 * World-Specific Configuration for GroupSpeedrun.
 * This class handles the persistence of a speedrun's state within a specific world's data folder.
 */
public class GSRConfigWorld {
    private static final Logger LOGGER = LoggerFactory.getLogger("GSR-Config");

    // --- [ SHARED GAMEPLAY STATE ] ---
    public boolean groupDeathEnabled = true;
    public boolean sharedHealthEnabled = false;
    public float maxHearts = 10.0f;

    // --- [ SHARED RUN STATE ] ---
    public long startTime = -1;
    public boolean isFailed = false, isVictorious = false, isTimerFrozen = false;
    public long frozenTime = 0, lastSplitTime = -1, victoryTimer = 0;
    public long timeNether = 0, timeBastion = 0, timeFortress = 0, timeEnd = 0, timeDragon = 0;

    // --- [ SHARED STRUCTURES ] ---
    public int fortressX = 0, fortressZ = 0, bastionX = 0, bastionZ = 0, strongholdX = 0, strongholdZ = 0, shipX = 0, shipZ = 0;
    public boolean fortressActive = false, bastionActive = false, strongholdActive = false, shipActive = false;

    // Visual fields used by Mixins (HUD defaults)
    public int hudMode = 0;
    public boolean locateHudOnTop = true;
    public float locateHudScale = 1.0f;
    public int barWidth = 100;
    public int barHeight = 4;
    public int maxScaleDistance = 1000;

    // --- [ CONSTANTS ] ---
    public static final float MIN_TIMER_HUD_SCALE = 0.3f;
    public static final float MAX_TIMER_HUD_SCALE = 3.5f;
    public static final float MIN_LOCATE_HUD_SCALE = 0.3f;
    public static final float MAX_LOCATE_HUD_SCALE = 3.5f;

    public String fortressColor = "#511515", bastionColor = "#3C3947", strongholdColor = "#97d16b", shipColor = "#A6638C";

    /**
     * Resets all data back to a "New Run" state.
     */
    public void resetRunData() {
        this.startTime = -1;
        this.isFailed = false;
        this.isVictorious = false;
        this.isTimerFrozen = false;
        this.frozenTime = 0;
        this.lastSplitTime = -1;
        this.fortressActive = false;
        this.bastionActive = false;
        this.strongholdActive = false;
        this.shipActive = false;
        this.victoryTimer = 0;
    }

    /**
     * Calculates current run time, accounting for pauses.
     */
    public long getElapsedTime() {
        if (startTime <= 0) return 0;
        if (isTimerFrozen) return frozenTime;
        return System.currentTimeMillis() - startTime;
    }

    /**
     * Writes current state to NBT (Used for network syncing and internal MC data).
     */
    public void writeNbt(NbtCompound nbt) {
        // --- Run State ---
        nbt.putLong("startTime", startTime);
        nbt.putBoolean("isFailed", isFailed);
        nbt.putBoolean("wasVictorious", isVictorious);
        nbt.putBoolean("isTimerFrozen", isTimerFrozen);
        nbt.putLong("frozenTime", frozenTime);

        // --- Split Times ---
        nbt.putLong("timeNether", timeNether);
        nbt.putLong("timeBastion", timeBastion);
        nbt.putLong("timeFortress", timeFortress);
        nbt.putLong("timeEnd", timeEnd);
        nbt.putLong("timeDragon", timeDragon);

        // --- Structures ---
        nbt.putInt("fortX", fortressX); nbt.putInt("fortZ", fortressZ); nbt.putBoolean("fortActive", fortressActive);
        nbt.putInt("bastX", bastionX); nbt.putInt("bastZ", bastionZ); nbt.putBoolean("bastActive", bastionActive);
        nbt.putInt("strongX", strongholdX); nbt.putInt("strongZ", strongholdZ); nbt.putBoolean("strongActive", strongholdActive);
        nbt.putInt("shipX", shipX); nbt.putInt("shipZ", shipZ); nbt.putBoolean("shipActive", shipActive);
    }

    /**
     * Reads state from NBT. Corrected for Minecraft 1.21 primitive fallbacks.
     */
    public void readNbt(NbtCompound nbt) {
        if (nbt == null) return;

        // --- Run State ---
        this.startTime = nbt.getLong("startTime", -1L);
        this.isFailed = nbt.getBoolean("isFailed", false);
        this.isVictorious = nbt.getBoolean("wasVictorious", false);
        this.isTimerFrozen = nbt.getBoolean("isTimerFrozen", false);
        this.frozenTime = nbt.getLong("frozenTime", 0L);

        // --- Split Times ---
        this.timeNether = nbt.getLong("timeNether", 0L);
        this.timeBastion = nbt.getLong("timeBastion", 0L);
        this.timeFortress = nbt.getLong("timeFortress", 0L);
        this.timeEnd = nbt.getLong("timeEnd", 0L);
        this.timeDragon = nbt.getLong("timeDragon", 0L);

        // --- Structures ---
        this.fortressX = nbt.getInt("fortX", 0);
        this.fortressZ = nbt.getInt("fortZ", 0);
        this.fortressActive = nbt.getBoolean("fortActive", false);

        this.bastionX = nbt.getInt("bastX", 0);
        this.bastionZ = nbt.getInt("bastZ", 0);
        this.bastionActive = nbt.getBoolean("bastActive", false);

        this.strongholdX = nbt.getInt("strongX", 0);
        this.strongholdZ = nbt.getInt("strongZ", 0);
        this.strongholdActive = nbt.getBoolean("strongActive", false);

        this.shipX = nbt.getInt("shipX", 0);
        this.shipZ = nbt.getInt("shipZ", 0);
        this.shipActive = nbt.getBoolean("shipActive", false);
    }

    public static File getWorldConfigFile(MinecraftServer server) {
        return server.getSavePath(WorldSavePath.ROOT).resolve("data").resolve("groupspeedrun.txt").toFile();
    }

    /**
     * Loads the world-specific config. Includes ALL state and splits to prevent resets on restart.
     */
    public static GSRConfigWorld load(MinecraftServer server) {
        GSRConfigWorld config = new GSRConfigWorld();
        File worldFile = getWorldConfigFile(server);

        if (!worldFile.exists()) {
            LOGGER.info("No world-specific GSR config found. Creating new defaults.");
            config.save(server);
            return config;
        }

        Properties p = new Properties();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(new FileInputStream(worldFile), StandardCharsets.UTF_8))) {
            p.load(r);

            // --- Load Run State ---
            config.startTime = Long.parseLong(p.getProperty("startTime", "-1"));
            config.isFailed = Boolean.parseBoolean(p.getProperty("isFailed", "false"));
            config.isVictorious = Boolean.parseBoolean(p.getProperty("wasVictorious", "false"));
            config.isTimerFrozen = Boolean.parseBoolean(p.getProperty("isTimerFrozen", "false"));
            config.frozenTime = Long.parseLong(p.getProperty("frozenTime", "0"));

            // --- Load Split Times ---
            config.timeNether = Long.parseLong(p.getProperty("timeNether", "0"));
            config.timeBastion = Long.parseLong(p.getProperty("timeBastion", "0"));
            config.timeFortress = Long.parseLong(p.getProperty("timeFortress", "0"));
            config.timeEnd = Long.parseLong(p.getProperty("timeEnd", "0"));
            config.timeDragon = Long.parseLong(p.getProperty("timeDragon", "0"));

            // --- Load Gameplay Settings ---
            config.groupDeathEnabled = Boolean.parseBoolean(p.getProperty("groupDeathEnabled", "true"));
            config.sharedHealthEnabled = Boolean.parseBoolean(p.getProperty("sharedHealthEnabled", "false"));
            config.maxHearts = Float.parseFloat(p.getProperty("maxHearts", "10.0"));

            // --- Load Structures ---
            config.fortressActive = Boolean.parseBoolean(p.getProperty("fortActive", "false"));
            config.fortressX = Integer.parseInt(p.getProperty("fortX", "0"));
            config.fortressZ = Integer.parseInt(p.getProperty("fortZ", "0"));

            config.bastionActive = Boolean.parseBoolean(p.getProperty("bastActive", "false"));
            config.bastionX = Integer.parseInt(p.getProperty("bastX", "0"));
            config.bastionZ = Integer.parseInt(p.getProperty("bastZ", "0"));

            config.strongholdActive = Boolean.parseBoolean(p.getProperty("strongActive", "false"));
            config.strongholdX = Integer.parseInt(p.getProperty("strongX", "0"));
            config.strongholdZ = Integer.parseInt(p.getProperty("strongZ", "0"));

            config.shipActive = Boolean.parseBoolean(p.getProperty("shipActive", "false"));
            config.shipX = Integer.parseInt(p.getProperty("shipX", "0"));
            config.shipZ = Integer.parseInt(p.getProperty("shipZ", "0"));

            LOGGER.info("Successfully loaded GSR world data.");
        } catch (Exception e) {
            LOGGER.error("Failed to load world-specific GSR config!", e);
        }
        return config;
    }

    /**
     * Saves all current run data, split milestones, and settings to the world's data folder.
     */
    public void save(MinecraftServer server) {
        File worldFile = getWorldConfigFile(server);
        if (worldFile.getParentFile() != null) worldFile.getParentFile().mkdirs();

        Properties p = new Properties();

        // --- Save Run State ---
        p.setProperty("startTime", String.valueOf(startTime));
        p.setProperty("isFailed", String.valueOf(isFailed));
        p.setProperty("wasVictorious", String.valueOf(isVictorious));
        p.setProperty("isTimerFrozen", String.valueOf(isTimerFrozen));
        p.setProperty("frozenTime", String.valueOf(frozenTime));

        // --- Save Split Times ---
        p.setProperty("timeNether", String.valueOf(timeNether));
        p.setProperty("timeBastion", String.valueOf(timeBastion));
        p.setProperty("timeFortress", String.valueOf(timeFortress));
        p.setProperty("timeEnd", String.valueOf(timeEnd));
        p.setProperty("timeDragon", String.valueOf(timeDragon));

        // --- Save Gameplay Settings ---
        p.setProperty("groupDeathEnabled", String.valueOf(groupDeathEnabled));
        p.setProperty("sharedHealthEnabled", String.valueOf(sharedHealthEnabled));
        p.setProperty("maxHearts", String.valueOf(maxHearts));

        // --- Save Structures ---
        p.setProperty("fortActive", String.valueOf(fortressActive));
        p.setProperty("fortX", String.valueOf(fortressX));
        p.setProperty("fortZ", String.valueOf(fortressZ));

        p.setProperty("bastActive", String.valueOf(bastionActive));
        p.setProperty("bastX", String.valueOf(bastionX));
        p.setProperty("bastZ", String.valueOf(bastionZ));

        p.setProperty("strongActive", String.valueOf(strongholdActive));
        p.setProperty("strongX", String.valueOf(strongholdX));
        p.setProperty("strongZ", String.valueOf(strongholdZ));

        p.setProperty("shipActive", String.valueOf(shipActive));
        p.setProperty("shipX", String.valueOf(shipX));
        p.setProperty("shipZ", String.valueOf(shipZ));

        try (BufferedWriter w = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(worldFile), StandardCharsets.UTF_8))) {
            p.store(w, "GSR World Data");
            LOGGER.info("Saved GSR world data.");
        } catch (IOException e) {
            LOGGER.error("Failed to save world-specific GSR config!", e);
        }
    }

    private int hexToInt(String h) { try { return Color.decode(h).getRGB(); } catch (Exception e) { return -1; } }
    public int getFortressColorInt() { return hexToInt(fortressColor); }
    public int getBastionColorInt() { return hexToInt(bastionColor); }
    public int getStrongholdColorInt() { return hexToInt(strongholdColor); }
    public int getShipColorInt() { return hexToInt(shipColor); }
}