package net.berkle.groupspeedrun.util;

/**
 * Utility for manipulating ARGB color integers used by Minecraft's DrawContext.
 * Handles bit-shifting to apply transparency to HUD elements.
 */
public class GSRColorHelper {

    /**
     * Injects an alpha value into a standard RGB color.
     * @param hexColor The base color (e.g., 0xFFFFFF for white)
     * @param alpha The transparency level (0.0f = invisible, 1.0f = fully opaque)
     * @return A 32-bit ARGB integer (e.g., 0xFFFFFFFF)
     */
    public static int applyAlpha(int hexColor, float alpha) {
        // Clamp alpha to ensure it stays between 0 and 255 to prevent bit-overflow
        int alphaInt = (int) (Math.max(0.0f, Math.min(1.0f, alpha)) * 255);

        // 0xFFFFFF mask removes bits 24-31 (the alpha channel)
        // alphaInt << 24 moves our alpha value into those bits
        return (hexColor & 0x00FFFFFF) | (alphaInt << 24);
    }

    /**
     * Generates a transparent black/dark color specifically for HUD backgrounds.
     * @param baseOpacity The maximum desired opacity (e.g., 0x90 for standard semi-transparent)
     * @param fadeProgress The current fade-in/out multiplier (0.0f to 1.0f)
     * @return A 32-bit ARGB integer with 0 red, green, and blue components
     */
    public static int getBackgroundWithAlpha(int baseOpacity, float fadeProgress) {
        // Multiplies the base background opacity (like 144/255) by the current fade progress
        int alpha = (int) (baseOpacity * Math.max(0.0f, Math.min(1.0f, fadeProgress)));

        // Returns the alpha bits. Since R, G, and B are 0, this creates a black tint.
        return (alpha << 24);
    }
}