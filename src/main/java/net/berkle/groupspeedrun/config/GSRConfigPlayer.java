package net.berkle.groupspeedrun.config;

import net.minecraft.nbt.NbtCompound;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Client-Side Configuration for GroupSpeedrun.
 * Handles individual player preferences like HUD scaling and positioning.
 */
public class GSRConfigPlayer {
    private static final Logger LOGGER = LoggerFactory.getLogger("GSR-PlayerConfig");

    // --- [ ABSOLUTE SCALE CONSTANTS ] ---
    // These define the hard limits for the /gsr hud scale commands
    public static final float MIN_TIMER_SCALE = 0.5f;
    public static final float MAX_TIMER_SCALE = 3.0f;

    public static final float MIN_LOCATE_SCALE = 0.5f;
    public static final float MAX_LOCATE_SCALE = 2.0f;

    public static final float MIN_OVERALL_SCALE = 0.5f;
    public static final float MAX_OVERALL_SCALE = 2.5f;

    public float MIN_ICON_SCALE = 0.5f;
    public float MAX_ICON_SCALE = 1.2f;

    // --- [ HUD SETTINGS ] ---
    public float hudOverallScale = 1.0f; // Multiplier for all HUD elements
    public float timerHudScale = 1.0f;
    public float locateHudScale = 1.0f;

    // 0 = Both, 1 = Timer Only, 2 = Locate Bar Only, 3 = Hidden
    public int hudMode = 0;

    public boolean timerHudOnRight = true;
    public boolean locateHudOnTop = true;

    // --- [ COMPASS / LOCATE BAR SETTINGS ] ---
    public int barWidth = 180;
    public int barHeight = 3;
    public int maxScaleDistance = 500;

    // These act as the bounds for the "breathing/distance" effect of the icons
    public float minIconScale = 0.5f;
    public float maxIconScale = 1.0f;

    /**
     * Resets player-specific UI settings to defaults.
     */
    public void resetToDefaults() {
        this.hudOverallScale = 1.0f;
        this.timerHudScale = 1.0f;
        this.locateHudScale = 1.0f;
        this.hudMode = 0;
        this.timerHudOnRight = true;
        this.locateHudOnTop = true;
        this.barWidth = 180;
        this.barHeight = 3;
        this.maxScaleDistance = 500;
        this.minIconScale = 0.5f;
        this.maxIconScale = 1.0f;
    }

    /**
     * Serializes player preferences to NBT.
     */
    public void writeNbt(NbtCompound nbt) {
        nbt.putFloat("overallScale", hudOverallScale);
        nbt.putFloat("timerScale", timerHudScale);
        nbt.putFloat("locateScale", locateHudScale);
        nbt.putInt("hudMode", hudMode);
        nbt.putBoolean("timerRight", timerHudOnRight);
        nbt.putBoolean("locateTop", locateHudOnTop);
        nbt.putInt("barWidth", barWidth);
        nbt.putInt("maxDist", maxScaleDistance);
        nbt.putFloat("minIconS", minIconScale);
        nbt.putFloat("maxIconS", maxIconScale);
    }

    /**
     * Deserializes player preferences from NBT.
     * Uses the 1.21 pattern for retrieving values.
     */
    public void readNbt(NbtCompound nbt) {
        if (nbt == null) return;

        this.hudOverallScale = nbt.getFloat("overallScale", 1.0f);
        this.timerHudScale = nbt.getFloat("timerScale", 1.0f);
        this.locateHudScale = nbt.getFloat("locateScale", 1.0f);
        this.hudMode = nbt.getInt("hudMode", 0);
        this.timerHudOnRight = nbt.getBoolean("timerRight", true);
        this.locateHudOnTop = nbt.getBoolean("locateTop", true);
        this.barWidth = nbt.getInt("barWidth", 180);
        this.maxScaleDistance = nbt.getInt("maxDist", 500);
        this.minIconScale = nbt.getFloat("minIconS", 0.5f);
        this.maxIconScale = nbt.getFloat("maxIconS", 1.0f);

        LOGGER.debug("GSR Player Config updated via NBT.");
    }

    public void cycleHudMode() {
        this.hudMode = (this.hudMode + 1) % 4;
    }
}