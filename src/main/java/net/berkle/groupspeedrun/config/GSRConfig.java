package net.berkle.groupspeedrun.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.util.math.MathHelper;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.awt.Color;

/**
 * GSRConfig handles all persistent data for the Group Speedrun mod.
 * Data is categorized into Visuals, Gameplay, Run State, and Persistence.
 */
public class GSRConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    // --- [ SECTION 1: VISUALS & HUD ] ---
    public int hudMode = 1;                 // 0: Always, 1: Tab-Only, 2: Hidden
    public boolean timerHudOnRight = true;
    public boolean locateHudOnTop = true;
    public float timerHudScale = 1.0f;
    public float locateHudScale = 0.95f;

    public static final float MIN_TIMER_HUD_SCALE = 0.3f;
    public static final float MAX_TIMER_HUD_SCALE = 3.5f;
    public static final float MIN_LOCATE_HUD_SCALE = 0.3f;
    public static final float MAX_LOCATE_HUD_SCALE = 3.5f;

    // --- [ SECTION 2: GAMEPLAY RULES ] ---
    public boolean groupDeathEnabled = true;
    public boolean sharedHealthEnabled = false;
    public float maxHearts = 10.0f;

    // --- [ SECTION 3: RUN STATE ] ---
    public long startTime = -1;
    public boolean isFailed = false;
    public boolean wasVictorious = false;
    public boolean isTimerFrozen = false;

    /** * Snapshot of world time when the run ends or is paused.
     * Used as a reference point for post-run fade logic.
     */
    public long frozenTime = 0;

    /** Cumulative count of ticks spent while the game was paused to keep timer accurate. */
    public long totalPausedTicks = 0;

    /** * NEW: Tracks the exact world time when the most recent split was achieved.
     * Used by GSRTimerHudState to trigger the 10-second "pop-up" visibility.
     */
    public long lastSplitTime = -1;

    public long victoryTimer = 100;

    // --- [ SECTION 4: SPLITS & TIMING ] ---
    // Stores the total ticks elapsed at the moment of each milestone
    public long timeNether = 0, timeBastion = 0, timeFortress = 0, timeEnd = 0, timeDragon = 0;

    // --- [ SECTION 5: STRUCTURE TRACKING ] ---
    public int fortressX = 0, fortressZ = 0;
    public boolean fortressActive = false;
    public int bastionX = 0, bastionZ = 0;
    public boolean bastionActive = false;
    public int strongholdX = 0, strongholdZ = 0;
    public boolean strongholdActive = false;
    public int shipX = 0, shipZ = 0;
    public boolean shipActive = false;

    // --- [ SECTION 6: LOCATE HUD STYLING ] ---
    public int maxScaleDistance = 1000;
    public int barWidth = 100;
    public int barHeight = 4;
    public String fortressColor = "#511515";
    public String bastionColor = "#3C3947";
    public String strongholdColor = "#97d16b";
    public String shipColor = "#A6638C";
    public String barColor = "#AAAAAA";

    /**
     * Resets run-specific data while keeping global user preferences (like scale and colors).
     * This is called when a run is reset or a new world is created.
     */
    public void resetRunData() {
        // Reset Timings and States
        this.startTime = -1;
        this.isFailed = false;
        this.wasVictorious = false;
        this.isTimerFrozen = false;
        this.frozenTime = 0;
        this.totalPausedTicks = 0;
        this.lastSplitTime = -1; // Ensure the pop-up doesn't appear immediately on reset
        this.victoryTimer = 100;

        // Clear split records
        this.timeNether = 0;
        this.timeBastion = 0;
        this.timeFortress = 0;
        this.timeEnd = 0;
        this.timeDragon = 0;

        // Reset Structure Trackers
        this.fortressActive = false;
        this.bastionActive = false;
        this.strongholdActive = false;
        this.shipActive = false;
    }

    // --- [ COLOR CONVERTERS ] ---

    /**
     * Converts Hex strings (e.g. "#FFFFFF") to Minecraft-compatible Integer colors.
     */
    private int hexToInt(String hex) {
        try {
            return Color.decode(hex).getRGB();
        } catch (Exception e) {
            return 0xFFFFFFFF; // Default to White on error
        }
    }

    public int getFortressColorInt() { return hexToInt(fortressColor); }
    public int getBastionColorInt() { return hexToInt(bastionColor); }
    public int getStrongholdColorInt() { return hexToInt(strongholdColor); }
    public int getShipColorInt() { return hexToInt(shipColor); }
    public int getBarColorInt() { return hexToInt(barColor); }

    /**
     * Converts seconds to ticks and adds it to the frozen time reference.
     */
    public void addFrozenTime(float seconds) {
        this.frozenTime += (long) (seconds * 20L);
    }

    // --- [ PERSISTENCE ] ---

    /**
     * Loads the config from the world directory.
     * If no file exists, returns a new config with default values.
     */
    public static GSRConfig load(File worldDir) {
        File configFile = new File(worldDir, "groupspeedrun.json");
        if (configFile.exists()) {
            try (FileReader reader = new FileReader(configFile)) {
                return GSON.fromJson(reader, GSRConfig.class);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return new GSRConfig();
    }

    /**
     * Saves the current config state to a JSON file in the world folder.
     * Includes bounds-checking for scale values to prevent UI glitches.
     */
    public void save(File worldDir) {
        // Sanitize scale values before saving
        this.timerHudScale = MathHelper.clamp(this.timerHudScale, MIN_TIMER_HUD_SCALE, MAX_TIMER_HUD_SCALE);
        this.locateHudScale = MathHelper.clamp(this.locateHudScale, MIN_LOCATE_HUD_SCALE, MAX_LOCATE_HUD_SCALE);

        if (!worldDir.exists()) worldDir.mkdirs();
        try (FileWriter writer = new FileWriter(new File(worldDir, "groupspeedrun.json"))) {
            GSON.toJson(this, writer);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Internal helper similar to Minecraft's MathHelper to keep the code clean.
     */
    private static float clamp(float value, float min, float max) {
        return value < min ? min : (Math.min(value, max));
    }
}