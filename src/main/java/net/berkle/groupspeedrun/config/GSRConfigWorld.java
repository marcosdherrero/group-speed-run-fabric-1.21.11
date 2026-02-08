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
 */
public class GSRConfigWorld {
    private static final Logger LOGGER = LoggerFactory.getLogger("GSR-Config");

    // --- [ SHARED GAMEPLAY STATE ] ---
    public boolean groupDeathEnabled = true;
    public boolean sharedHealthEnabled = false;
    public float maxHearts = 10.0f;

    // --- [ SHARED RUN STATE ] ---
    public long startTime = -1;
    public boolean isFailed = false, wasVictorious = false, isTimerFrozen = false;
    public long frozenTime = 0, lastSplitTime = -1, victoryTimer = 0;
    public long timeNether = 0, timeBastion = 0, timeFortress = 0, timeEnd = 0, timeDragon = 0;

    // --- [ SHARED STRUCTURES ] ---
    public int fortressX = 0, fortressZ = 0, bastionX = 0, bastionZ = 0, strongholdX = 0, strongholdZ = 0, shipX = 0, shipZ = 0;
    public boolean fortressActive = false, bastionActive = false, strongholdActive = false, shipActive = false;

    // Visual fields used by Mixins
    public int hudMode = 0;
    public boolean locateHudOnTop = true;
    public float locateHudScale = 1.0f;

    // Added Missing HUD properties that were causing Mixin failures
    public int barWidth = 100;
    public int barHeight = 4;
    public int maxScaleDistance = 1000;

    // --- [ CONSTANTS ] ---
    public static final float MIN_TIMER_HUD_SCALE = 0.3f;
    public static final float MAX_TIMER_HUD_SCALE = 3.5f;
    public static final float MIN_LOCATE_HUD_SCALE = 0.3f;
    public static final float MAX_LOCATE_HUD_SCALE = 3.5f;

    public String fortressColor = "#511515", bastionColor = "#3C3947", strongholdColor = "#97d16b", shipColor = "#A6638C";

    public void resetRunData() {
        this.startTime = -1;
        this.isFailed = false;
        this.wasVictorious = false;
        this.isTimerFrozen = false;
        this.frozenTime = 0;
        this.lastSplitTime = -1;
        this.fortressActive = false;
        this.bastionActive = false;
        this.strongholdActive = false;
        this.shipActive = false;
        this.victoryTimer = 0;
    }

    public long getElapsedTime() {
        if (startTime <= 0) return 0;
        if (isTimerFrozen) return frozenTime;
        return System.currentTimeMillis() - startTime;
    }

    public void writeNbt(NbtCompound nbt) {
        nbt.putLong("startTime", startTime);
        nbt.putBoolean("isFailed", isFailed);
        nbt.putBoolean("wasVictorious", wasVictorious);
        nbt.putBoolean("isTimerFrozen", isTimerFrozen);
        nbt.putLong("frozenTime", frozenTime);
        nbt.putLong("timeDragon", timeDragon);
        nbt.putInt("fortX", fortressX); nbt.putInt("fortZ", fortressZ); nbt.putBoolean("fortActive", fortressActive);
        nbt.putInt("bastX", bastionX); nbt.putInt("bastZ", bastionZ); nbt.putBoolean("bastActive", bastionActive);
        nbt.putInt("strongX", strongholdX); nbt.putInt("strongZ", strongholdZ); nbt.putBoolean("strongActive", strongholdActive);
        nbt.putInt("shipX", shipX); nbt.putInt("shipZ", shipZ); nbt.putBoolean("shipActive", shipActive);
        nbt.putInt("shipX", shipX); nbt.putInt("shipZ", shipZ); nbt.putBoolean("shipActive", shipActive);
    }

    /**
     * READ NBT - Corrected for Minecraft 1.21 Optional API.
     * Uses overloads with fallback values to return primitive types directly.
     */
    public void readNbt(NbtCompound nbt) {
        if (nbt == null) return;

        // Use the (key, defaultValue) overload to get a primitive long/int/boolean
        this.startTime = nbt.getLong("startTime", -1L);
        this.isFailed = nbt.getBoolean("isFailed", false);
        this.wasVictorious = nbt.getBoolean("wasVictorious", false);
        this.isTimerFrozen = nbt.getBoolean("isTimerFrozen", false);
        this.frozenTime = nbt.getLong("frozenTime", 0L);
        this.timeDragon = nbt.getLong("timeDragon", 0L);

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

    public static GSRConfigWorld load(MinecraftServer server) {
        GSRConfigWorld config = new GSRConfigWorld();
        File worldFile = getWorldConfigFile(server);

        if (!worldFile.exists()) {
            LOGGER.info("No world-specific GSR config found for '{}'. Creating new defaults.", server.getSaveProperties().getLevelName());
            config.save(server);
            return config;
        }

        Properties p = new Properties();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(new FileInputStream(worldFile), StandardCharsets.UTF_8))) {
            p.load(r);
            config.startTime = Long.parseLong(p.getProperty("startTime", "-1"));
            config.groupDeathEnabled = Boolean.parseBoolean(p.getProperty("groupDeathEnabled", "true"));

            LOGGER.info("Successfully loaded GSR world data for '{}' (Start Time: {})",
                    server.getSaveProperties().getLevelName(), config.startTime);
        } catch (Exception e) {
            LOGGER.error("Failed to load world-specific GSR config!", e);
        }
        return config;
    }

    public void save(MinecraftServer server) {
        File worldFile = getWorldConfigFile(server);
        worldFile.getParentFile().mkdirs();

        Properties p = new Properties();
        p.setProperty("startTime", String.valueOf(startTime));
        p.setProperty("groupDeathEnabled", String.valueOf(groupDeathEnabled));

        try (BufferedWriter w = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(worldFile), StandardCharsets.UTF_8))) {
            p.store(w, "GSR World Data");

            // Log with specific details to help debugging "ghost" timer issues
            LOGGER.info("Saved GSR world data. [Active: {}, Start: {}, GroupDeath: {}]",
                    (startTime > 0), startTime, groupDeathEnabled);
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