package net.berkle.groupspeedrun.util;

import net.berkle.groupspeedrun.config.GSRConfig;
import net.minecraft.client.MinecraftClient;

/**
 * GSRTimerHudState manages the transparency (Alpha) of the HUD elements.
 * It handles the smooth fading for Tab-key presses, game completion,
 * and temporary split notifications.
 */
public class GSRTimerHudState {

    // Persistent progress tracker for the Tab-key fade effect
    private static float tabFadeProgress = 0f;

    /**
     * Calculates the current alpha (0.0 to 1.0) for the HUD.
     * * @param client The Minecraft client instance for key-press checks.
     * @param config The GSR config containing split and run state data.
     * @param isFinished Whether the run has ended (Victory or Fail).
     * @param ticksSinceEnd Ticks elapsed since the frozenTime was set.
     * @return A float representing the desired transparency.
     */
    public static float getFadeAlpha(MinecraftClient client, GSRConfig config, boolean isFinished, long ticksSinceEnd) {
        final int FADE_TICKS = 20;  // 1 second (20 ticks) for fade in/out
        final int END_STAY_TICKS = 600; // 30 seconds visibility after a run ends
        final int SPLIT_STAY_TICKS = 200; // 10 seconds visibility after a split is achieved

        // 1. Update Tab-Press Fading
        // This ensures the HUD slides into visibility smoothly when holding Tab.
        float fadeSpeed = 0.15f;
        if (client.options.playerListKey.isPressed()) {
            tabFadeProgress = Math.min(1.0f, tabFadeProgress + fadeSpeed);
        } else {
            tabFadeProgress = Math.max(0.0f, tabFadeProgress - fadeSpeed);
        }

        // 2. PRIORITY 1: Post-Game Visibility (Victory/Fail)
        // If the run is over, show the HUD for 30 seconds regardless of other settings.
        if (isFinished && ticksSinceEnd >= 0 && ticksSinceEnd < END_STAY_TICKS) {
            return calculateLinearFade(ticksSinceEnd, END_STAY_TICKS, FADE_TICKS);
        }

        // 3. PRIORITY 2: Split Pop-up Logic
        // If a split was just achieved (lastSplitTime updated), show HUD for 10 seconds.
        if (client.world != null && config.lastSplitTime > 0) {
            long ticksSinceSplit = client.world.getTime() - config.lastSplitTime;

            if (ticksSinceSplit >= 0 && ticksSinceSplit < SPLIT_STAY_TICKS) {
                return calculateLinearFade(ticksSinceSplit, SPLIT_STAY_TICKS, FADE_TICKS);
            }
        }

        // 4. PRIORITY 3: Standard HUD Modes
        // If no special event is happening, respect the user's config settings.
        if (config.hudMode == 2) return 0.0f; // Mode: Hidden
        if (config.hudMode == 1) return tabFadeProgress; // Mode: Tab-Only (Smooth fade)

        return 1.0f; // Mode: Always Visible
    }

    /**
     * Helper method to calculate a standard "Fade In -> Stay -> Fade Out" curve.
     * * @param current The current elapsed ticks for the event.
     * @param total The total duration the event should last.
     * @param fade The duration of the fade-in and fade-out segments.
     * @return Calculated alpha float.
     */
    private static float calculateLinearFade(long current, int total, int fade) {
        if (current < fade) {
            return current / (float) fade; // Fading In
        } else if (current > (total - fade)) {
            return (total - current) / (float) fade; // Fading Out
        }
        return 1.0f; // Full visibility
    }
}