package net.berkle.groupspeedrun.util;

import net.berkle.groupspeedrun.GSRClient;
import net.berkle.groupspeedrun.config.GSRConfigWorld;
import net.berkle.groupspeedrun.config.GSRConfigPlayer;
import net.minecraft.client.MinecraftClient;

/**
 * Utility to manage the smooth opacity (alpha) of the Speedrun HUD.
 * Handles transitions for Tab-key fading, split pop-ups, and victory screens.
 */
public class GSRAlphaUtil {

    // Persistent progress tracker for the Tab key animation.
    private static float tabFadeProgress = 0f;

    /**
     * Calculates the current alpha transparency for the HUD.
     * @return A float between 0.0f (invisible) and 1.0f (fully opaque).
     */
    public static float getFadeAlpha(MinecraftClient client, GSRConfigWorld worldConfig, boolean isFinished, long ticksSinceEnd) {
        // FIX: Access the static instance from your client class
        GSRConfigPlayer playerConfig = GSRClient.PLAYER_CONFIG;

        if (playerConfig == null) return 0.0f;

        // Constants for animation timing
        final int FADE_TICKS = 20;
        final int END_STAY_TICKS = 600;
        final int SPLIT_STAY_TICKS = 200;

        // --- 1. TAB KEY ANIMATION STATE ---
        float fadeSpeed = 0.10f;
        if (client.options.playerListKey.isPressed()) {
            tabFadeProgress = Math.min(1.0f, tabFadeProgress + fadeSpeed);
        } else {
            tabFadeProgress = Math.max(0.0f, tabFadeProgress - fadeSpeed);
        }

        // --- 2. OVERLAY LOGIC (SPLITS & VICTORY) ---
        float overlayAlpha = 0.0f;

        if (isFinished && ticksSinceEnd >= 0 && ticksSinceEnd < END_STAY_TICKS) {
            overlayAlpha = calculateLinearFade(ticksSinceEnd, END_STAY_TICKS, FADE_TICKS);
        } else if (client.world != null && worldConfig.lastSplitTime > 0) {
            long ticksSinceSplit = client.world.getTime() - worldConfig.lastSplitTime;
            if (ticksSinceSplit >= 0 && ticksSinceSplit < SPLIT_STAY_TICKS) {
                overlayAlpha = calculateLinearFade(ticksSinceSplit, SPLIT_STAY_TICKS, FADE_TICKS);
            }
        }

        // --- 3. BASE VISIBILITY MODES ---
        // 0 = Always On, 1 = Tab Only, 3 = Hidden (Mode 2 is usually for the Locate Bar)
        if (playerConfig.hudMode == 3) return 0.0f;

        float baseAlpha;
        if (playerConfig.hudMode == 1) {
            baseAlpha = tabFadeProgress;
        } else {
            baseAlpha = 1.0f; // Always On
        }

        // --- 4. FINAL COMBINATION ---
        return Math.max(baseAlpha, overlayAlpha);
    }

    /**
     * Helper to calculate a 0.0 -> 1.0 -> 0.0 fade curve.
     */
    private static float calculateLinearFade(long current, int total, int fade) {
        if (current < 0) return 0f;

        // Fade In
        if (current < fade) {
            return current / (float) fade;
        }
        // Fade Out
        else if (current > (total - fade)) {
            return Math.max(0f, (total - current) / (float) fade);
        }

        // Fully Visible
        return 1.0f;
    }
}