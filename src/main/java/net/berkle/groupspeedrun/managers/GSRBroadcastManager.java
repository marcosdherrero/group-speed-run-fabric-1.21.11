package net.berkle.groupspeedrun.managers;

import com.google.gson.JsonObject;
import net.berkle.groupspeedrun.GSREvents;
import net.berkle.groupspeedrun.GSRMain;
import net.berkle.groupspeedrun.util.GSRFormatUtil;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.PlayerManager;
import net.minecraft.text.Text;

public class GSRBroadcastManager {

    private static final int BAR_LENGTH = 25;

    /**
     * Entry point for the live stats command.
     * Uses the existing calculation logic in GSRRunHistoryManager.
     */
    public static void broadcastLiveStats(MinecraftServer server) {
        PlayerManager pm = server.getPlayerManager();
        long currentTicks = GSREvents.getRunTicks(server);

        // We use your existing logic!
        // Passing "LIVE" ensures that the "Roast Logic" in RunHistoryManager is triggered.
        JsonObject awards = GSRRunHistoryManager.calculateAwards(server, "LIVE", "");

        broadcastStats(pm, currentTicks, awards, "COMMAND");
    }

    public static void broadcastVictory(MinecraftServer server, long ticks, JsonObject awards) {
        PlayerManager pm = server.getPlayerManager();
        pm.broadcast(Text.literal(getSeparator('a', BAR_LENGTH)), false);
        pm.broadcast(Text.literal("§a§l          VICTORY!          "), false);
        broadcastStats(pm, ticks, awards, "VICTORY");
        pm.broadcast(Text.literal(getSeparator('a', BAR_LENGTH)), false);
    }

    public static void broadcastFailure(MinecraftServer server, long ticks, String loser, String msg, JsonObject awards) {
        PlayerManager pm = server.getPlayerManager();
        pm.broadcast(Text.literal(getSeparator('4', BAR_LENGTH)), false);
        pm.broadcast(Text.literal("§4💀 Disgrace: §c" + loser), false);
        pm.broadcast(Text.literal("§7\"" + msg + "\""), false);
        broadcastStats(pm, ticks, awards, "FAILURE");
        pm.broadcast(Text.literal(getSeparator('4', BAR_LENGTH)), false);
    }

    public static void broadcastStats(PlayerManager pm, long ticks, JsonObject awards, String mode) {
        boolean isFullList = mode.equalsIgnoreCase("COMMAND");
        boolean isVictory = mode.equalsIgnoreCase("VICTORY");
        boolean isFailure = mode.equalsIgnoreCase("FAILURE");

        if (isFullList) {
            pm.broadcast(Text.literal("§8§l» §6§lCURRENT RUN PREVIEW §8§l«"), false);
            if (isFullList) pm.broadcast(Text.literal(getSeparator('8', BAR_LENGTH)), false);
        }

        // Changed label from "Final Time" to "Current Time" for the command preview
        String timeLabel = (GSRMain.CONFIG.isFailed || GSRMain.CONFIG.isVictorious)
                ? "§6Final Time: "
                : "§6Current Time: ";
        pm.broadcast(Text.literal(timeLabel + "§f" + GSRFormatUtil.formatTime(ticks)), false);

        // --- SECTION 1: PERFORMANCE ---
        if (isVictory || isFullList) {
            if (hasAnyData(awards, "dragon_warrior", "adc", "killer", "tank", "defender", "healer", "brew_master", "pog_champ", "builder", "sightseer")) {
                pm.broadcast(Text.literal("§8§o-- Performance Awards --"), false);

                display(pm, awards, "dragon_warrior", "§5🐉 Dragon Warrior", " dragon damage");
                display(pm, awards, "adc", "§6🏹 ADC", " most damage");
                display(pm, awards, "killer", "§4💀 Serial Killer", " most kills");
                display(pm, awards, "tank", "§4❈ Tank", " damage taken");
                display(pm, awards, "defender", "§b🛡 Defender", " armor");
                display(pm, awards, "healer", "§d❤ Healer", " HP healed");
                display(pm, awards, "brew_master", "§b🧪 Brew Master", " potions drank");
                display(pm, awards, "pog_champ", "§e🔥 Pog Champ", " rods");
                display(pm, awards, "builder", "§2🔨 Builder", " blocks");
                display(pm, awards, "sightseer", "§f👣 Sightseer", " blocks moved");
            }
        }

        // --- SECTION 2: THE ROASTS ---
        if (isFailure || isFullList) {
            if (hasAnyData(awards, "shuffler", "coward", "weakling", "good_for_nothing")) {
                pm.broadcast(Text.literal("§8§o-- Current Hall of Shame --"), false);

                display(pm, awards, "shuffler", "§3🗃 Shuffler", " most inventories opened");
                display(pm, awards, "coward", "§e🏃 Coward", " least damage taken");
                display(pm, awards, "weakling", "§f🍼 Weakling", " least damage done");
                display(pm, awards, "good_for_nothing", "§8⚖ Carried", " least advancements");
            }
        }

        if (isFullList) pm.broadcast(Text.literal(getSeparator('8', BAR_LENGTH)), false);
    }

    private static boolean hasAnyData(JsonObject awards, String... keys) {
        if (awards == null) return false;
        for (String key : keys) {
            String valKey = key + "_v";
            if (awards.has(valKey) && awards.get(valKey).getAsDouble() > 0.001) {
                return true;
            }
        }
        return false;
    }

    public static void display(PlayerManager pm, JsonObject awards, String key, String label, String unit) {
        String valKey = key + "_v";

        if (awards != null && awards.has(key) && awards.has(valKey)) {
            try {
                double val = awards.get(valKey).getAsDouble();
                String name = awards.get(key).getAsString();

                if (val <= 0.001 || name.equalsIgnoreCase("None") || name.isEmpty()) return;

                // Format: Integers stay as integers, Floats get 1 decimal point
                String fVal = (val == (int) val) ? String.valueOf((int) val) : String.format("%.1f", val);
                pm.broadcast(Text.literal(label + ": §b" + name + " §f(" + fVal + unit + ")"), false);

            } catch (Exception e) {
                System.out.println("[GSR] Display error for key: " + key);
            }
        }
    }

    private static String getSeparator(char colorCode, int length) {
        return "§" + colorCode + "§l" + "━".repeat(length);
    }
}