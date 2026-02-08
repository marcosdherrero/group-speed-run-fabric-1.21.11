package net.berkle.groupspeedrun.util;

import net.berkle.groupspeedrun.config.GSRConfigWorld;
import net.berkle.groupspeedrun.config.GSRConfigPlayer;
import net.minecraft.client.MinecraftClient;

/**
 * Utility to manage the smooth opacity (alpha) of the Speedrun HUD.
 * This handles the transitions for Tab-key fading, split pop-ups, and victory screens.
 */
public class GSRAlphaUtil {

    // Persistent progress tracker for the Tab key animation.
    // Lives here so it persists between render frames.
    private static float tabFadeProgress = 0f;

    /**
     * Calculates the current alpha transparency for the HUD.
     * * @param client The Minecraft client instance.
     * @param worldConfig The server-side run configuration.
     * @param isFinished Whether the run has ended (Victory or Fail).
     * @param ticksSinceEnd Ticks elapsed since the last major status change (split/end).
     * @return A float between 0.0f (invisible) and 1.0f (fully opaque).
     */
    public static float getFadeAlpha(MinecraftClient client, GSRConfigWorld worldConfig, boolean isFinished, long ticksSinceEnd) {
        GSRConfigPlayer playerConfig = GSRConfigPlayer.INSTANCE;

        // Constants for animation timing (20 ticks = 1 second at standard TPS)
        final int FADE_TICKS = 20;         // How long the fade-in/out animation lasts
        final int END_STAY_TICKS = 600;    // Victory screen stays for 30 seconds
        final int SPLIT_STAY_TICKS = 200;  // Split pop-up stays for 10 seconds

        // --- 1. TAB KEY ANIMATION STATE ---
        // We increment/decrement every frame to create a smooth sliding effect.
        float fadeSpeed = 0.10f; // Roughly 10 frames to reach full visibility
        if (client.options.playerListKey.isPressed()) {
            tabFadeProgress = Math.min(1.0f, tabFadeProgress + fadeSpeed);
        } else {
            tabFadeProgress = Math.max(0.0f, tabFadeProgress - fadeSpeed);
        }

        // --- 2. OVERLAY LOGIC (SPLITS & VICTORY) ---
        // This alpha is triggered by game events, independent of user input.
        float overlayAlpha = 0.0f;

        if (isFinished && ticksSinceEnd >= 0 && ticksSinceEnd < END_STAY_TICKS) {
            // Priority 1: Victory/Fail screen visibility
            overlayAlpha = calculateLinearFade(ticksSinceEnd, END_STAY_TICKS, FADE_TICKS);
        } else if (client.world != null && worldConfig.lastSplitTime > 0) {
            // Priority 2: Split Pop-up visibility
            long ticksSinceSplit = client.world.getTime() - worldConfig.lastSplitTime;
            if (ticksSinceSplit >= 0 && ticksSinceSplit < SPLIT_STAY_TICKS) {
                overlayAlpha = calculateLinearFade(ticksSinceSplit, SPLIT_STAY_TICKS, FADE_TICKS);
            }
        }

        // --- 3. BASE VISIBILITY MODES ---
        if (playerConfig.hudMode == 2) return 0.0f; // Mode: Hidden

        // If Mode is 1 (Tab-Only), use the animated progress.
        // If Mode is 0 (Always), lock base visibility to 100%.
        float baseAlpha = (playerConfig.hudMode == 1) ? tabFadeProgress : 1.0f;

        // --- 4. FINAL COMBINATION ---
        // We return whichever is higher: the user-requested visibility (Tab/Always)
        // or the event-requested visibility (Split/Victory).
        return Math.max(baseAlpha, overlayAlpha);
    }

    /**
     * Helper to calculate a 0.0-1.0-0.0 fade curve over a specific duration.
     */
    private static float calculateLinearFade(long current, int total, int fade) {
        if (current < 0) return 0f;

        // Fade In phase
        if (current < fade) {
            return current / (float) fade;
        }
        // Fade Out phase (occurs at the very end of the total duration)
        else if (current > (total - fade)) {
            return Math.max(0f, (total - current) / (float) fade);
        }

        // Solid phase (middle of the duration)
        return 1.0f;
    }
}