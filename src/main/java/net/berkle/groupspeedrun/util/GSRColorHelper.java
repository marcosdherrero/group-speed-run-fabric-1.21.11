package net.berkle.groupspeedrun.util;

public class GSRColorHelper {

    /**
     * Injects an alpha value into a standard RGB color.
     * @param hexColor The base color (e.g., 0xFFFFFF for white)
     * @param alpha 0.0f to 1.0f
     * @return ARGB integer for Minecraft rendering
     */
    public static int applyAlpha(int hexColor, float alpha) {
        int alphaInt = (int) (alpha * 255);
        // Strips existing alpha and applies the new one at the front (bits 24-31)
        return (hexColor & 0xFFFFFF) | (alphaInt << 24);
    }
    /**
     * Specifically for backgrounds that should be darker even at full opacity.
     * @param baseOpacity The maximum alpha (e.g., 0x90 for standard dark HUDs)
     * @param fadeProgress The current fade (0.0f to 1.0f)
     * @return ARGB integer
     */
    public static int getBackgroundWithAlpha(int baseOpacity, float fadeProgress) {
        int alpha = (int) (baseOpacity * fadeProgress);
        return (alpha << 24);
    }
}