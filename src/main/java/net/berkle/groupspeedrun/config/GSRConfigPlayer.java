package net.berkle.groupspeedrun.config;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.util.math.MathHelper;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory; // New Import
import java.io.*;
import java.util.Properties;

public class GSRConfigPlayer {
    private static final Logger LOGGER = LoggerFactory.getLogger("GSR-Config"); // Initialize Logger
    private static final File FILE = FabricLoader.getInstance().getConfigDir().resolve("groupspeedrun_player.txt").toFile();
    public static final GSRConfigPlayer INSTANCE = load();

    // Overall hud scaling and positioning
    public static final float MIN_HUD_SCALE = 0.5f;
    public static final float MAX_HUD_SCALE = 3.5f;
    public boolean timerHudOnRight = true;
    public boolean locateHudOnTop = true;
    public int hudMode = 1;

    // Timer Constraints
    public float timerHudScale = 1.0f;
    public final float MIN_TIMER_SCALE = 0.5f;
    public final float MAX_TIMER_SCALE = 3.5f;

    // Locate Bar Constraints
    public int barWidth = 100;
    public int barHeight = 4;
    public int maxScaleDistance = 1000;
    public float locateHudScale = 0.95f;

    public final float MIN_ICON_SCALE = 0.5f;
    public final float MAX_ICON_SCALE = 3.5f;
    public final float MIN_LOCATE_SCALE = 0.5f;
    public final float MAX_LOCATE_SCALE = 3.5f;

    public static GSRConfigPlayer load() {
        GSRConfigPlayer config = new GSRConfigPlayer();
        if (!FILE.exists()) {
            LOGGER.info("No player config found at {}, using defaults.", FILE.getName());
            return config;
        }

        Properties p = new Properties();
        try (BufferedReader r = new BufferedReader(new FileReader(FILE))) {
            p.load(r);
            config.timerHudScale = Float.parseFloat(p.getProperty("timerScale", "1.0"));
            config.locateHudScale = Float.parseFloat(p.getProperty("locateScale", "0.95"));
            config.timerHudOnRight = Boolean.parseBoolean(p.getProperty("timerRight", "true"));
            config.locateHudOnTop = Boolean.parseBoolean(p.getProperty("locateTop", "true"));
            config.hudMode = Integer.parseInt(p.getProperty("hudMode", "1"));
            config.barWidth = Integer.parseInt(p.getProperty("barWidth", "100"));
            config.barHeight = Integer.parseInt(p.getProperty("barHeight", "4"));
            config.maxScaleDistance = Integer.parseInt(p.getProperty("maxDist", "1000"));
            LOGGER.info("Successfully loaded player preferences from {}.", FILE.getName());
        } catch (Exception e) {
            LOGGER.error("Failed to load player config!", e); // Now actually reporting the error
        }
        return config;
    }

    public void save() {
        // Enforce bounds during save
        this.timerHudScale = MathHelper.clamp(this.timerHudScale, MIN_TIMER_SCALE, MAX_TIMER_SCALE);
        this.locateHudScale = MathHelper.clamp(this.locateHudScale, MIN_LOCATE_SCALE, MAX_LOCATE_SCALE);

        Properties p = getProperties();
        try (BufferedWriter w = new BufferedWriter(new FileWriter(FILE))) {
            p.store(w, "GSR Individual Player Preferences");
            LOGGER.info("Saved player preferences (Timer: {}x, Locate: {}x, Top: {})",
                    timerHudScale, locateHudScale, locateHudOnTop);
        } catch (Exception e) {
            LOGGER.error("Could not save player config!", e);
        }
    }

    private @NonNull Properties getProperties() {
        Properties p = new Properties();
        p.setProperty("timerScale", String.valueOf(timerHudScale));
        p.setProperty("locateScale", String.valueOf(locateHudScale));
        p.setProperty("timerRight", String.valueOf(timerHudOnRight));
        p.setProperty("locateTop", String.valueOf(locateHudOnTop));
        p.setProperty("hudMode", String.valueOf(hudMode));
        p.setProperty("barWidth", String.valueOf(barWidth));
        p.setProperty("barHeight", String.valueOf(barHeight));
        p.setProperty("maxDist", String.valueOf(maxScaleDistance));
        return p;
    }
}