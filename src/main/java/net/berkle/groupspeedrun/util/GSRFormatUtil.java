package net.berkle.groupspeedrun.util;

public class GSRFormatUtil {
    // Private constructor prevents instantiation
    private GSRFormatUtil() {}
    /**
     * Converts tick count into a formatted string.
     * Logic:
     * - If hours exist: H:MM:SS.CC
     * - If only minutes exist: MM:SS.CC
     * - If under a minute: SS.CC
     * */
    public static String formatTime(long ticks) {
        long totalMs = ticks * 50;
        long h = totalMs / 3600000;
        long m = (totalMs / 60000) % 60;
        long s = (totalMs / 1000) % 60;
        long f = (totalMs % 1000) / 10;

        // 1. Handle Hours (H:MM:SS.CC)
        if (h > 0) {
            return String.format("%d:%02d:%02d.%02d", h, m, s, f);
        }
        // 2. Handle Minutes (MM:SS.CC)
        if (m > 0) {
            return String.format("%02d:%02d.%02d", m, s, f);
        }
        // 3. Handle Seconds only (SS.CC)
        return String.format("%02d.%02d", s, f);
    }
}