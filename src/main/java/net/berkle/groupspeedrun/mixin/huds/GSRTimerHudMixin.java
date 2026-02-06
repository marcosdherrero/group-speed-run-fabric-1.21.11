package net.berkle.groupspeedrun.mixin.huds;

import net.berkle.groupspeedrun.GSRMain;
import net.berkle.groupspeedrun.GSREvents;
import net.berkle.groupspeedrun.util.GSRColorHelper;
import net.berkle.groupspeedrun.util.GSRTimerHudState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.render.RenderTickCounter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(InGameHud.class)
public class GSRTimerHudMixin {

    @Inject(method = "render", at = @At("TAIL"))
    private void renderScoreboardTimer(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        MinecraftClient client = MinecraftClient.getInstance();

        // Safety: Prevent rendering in F1 mode, loading screens, or if the player is null
        if (client.player == null || client.options.hudHidden || client.world == null) return;

        var config = GSRMain.CONFIG;
        if (config == null) return;

        // --- VISIBILITY & FADE LOGIC ---
        // These variables help determine if we are in the "Post-Game" state (Victory/Fail)
        boolean isFinished = config.wasVictorious || config.isFailed;
        long ticksSinceEnd = client.world.getTime() - config.frozenTime;

        /**
         * ALPHA CALCULATION:
         * This call to GSRTimerHudState now handles three distinct visibility triggers:
         * 1. The HUD Mode in Config (Always vs Hidden)
         * 2. The User holding the Tab key (Smooth fade-in)
         * 3. The 10-second Split Pop-up (Triggered by config.lastSplitTime)
         */
        float fadeAlpha = GSRTimerHudState.getFadeAlpha(client, config, isFinished, ticksSinceEnd);

        // Efficiency: If the HUD is effectively invisible, skip all text and box math
        if (fadeAlpha <= 0.01f) return;
        // -------------------------------

        // --- TIME CALCULATION ---
        // Get the current run time, accounting for pauses and server synchronization
        long displayTicks = GSREvents.getRunTicks(client.getServer());
        if (client.getServer() == null && config.startTime >= 0) {
            displayTicks = isFinished ?
                    (config.frozenTime - config.startTime) - config.totalPausedTicks :
                    (client.world.getTime() - config.startTime) - config.totalPausedTicks;
        }

        // --- TEXT PREPARATION ---
        TextRenderer tr = client.textRenderer;

        // Update title colors and text based on victory or failure status
        String titleLabel = config.wasVictorious ? "§a§lGSR VICTORY!" : (config.isFailed ? "§c§lGSR FAIL" : "§6§lGSR Time:");
        String titleTime = (config.wasVictorious ? "§a" : (config.isFailed ? "§c" : "§f")) + formatFullTime(displayTicks);

        // Find the most recent split to mark it with a gold star in the UI
        long latestTime = Math.max(config.timeNether, Math.max(config.timeBastion,
                Math.max(config.timeFortress, Math.max(config.timeEnd, config.timeDragon))));

        String[][] splitData = {
                prepareLine("Nether", config.timeNether, latestTime),
                prepareLine("Bastion", config.timeBastion, latestTime),
                prepareLine("Fortress", config.timeFortress, latestTime),
                prepareLine("The End", config.timeEnd, latestTime),
                prepareLine("Dragon", config.timeDragon, latestTime)
        };

        // --- DYNAMIC UI SIZING ---
        // Calculate the required width of the box by measuring the text strings
        int nameColWidth = tr.getWidth(titleLabel);
        int timeColWidth = tr.getWidth(titleTime);
        for (String[] split : splitData) {
            nameColWidth = Math.max(nameColWidth, tr.getWidth(split[2] + split[0]));
            timeColWidth = Math.max(timeColWidth, tr.getWidth(split[1]));
        }

        final int padding = 6;
        final int spacing = 10;
        final int rowHeight = 10;
        final int totalBoxWidth = nameColWidth + spacing + timeColWidth + (padding * 2);
        final int boxHeight = ((splitData.length + 1) * rowHeight) + (padding * 2) + 4;

        // --- MATRIX TRANSFORMATIONS ---
        context.getMatrices().pushMatrix();

        // Position the HUD at the vertical center-top area
        float pivotY = (context.getScaledWindowHeight() / 2f) - (boxHeight / 2f) - (context.getScaledWindowHeight() * 0.15f);
        // Position horizontally based on user preference (Left vs Right side)
        float pivotX = config.timerHudOnRight ? (context.getScaledWindowWidth() - 10) : 10;

        context.getMatrices().translate(pivotX, pivotY);
        context.getMatrices().scale(config.timerHudScale, config.timerHudScale);

        // If anchored to the right, shift the box left so it doesn't bleed off screen
        if (config.timerHudOnRight) {
            context.getMatrices().translate(-totalBoxWidth, 0);
        }

        // --- RENDERING ---

        // 1. Draw the main background box with custom fade transparency
        context.fill(0, 0, totalBoxWidth, boxHeight, GSRColorHelper.getBackgroundWithAlpha(0x90, fadeAlpha));

        // Create the base text color (White) combined with our dynamic alpha
        int mainTextColor = GSRColorHelper.applyAlpha(0xFFFFFF, fadeAlpha);

        // 2. Render Header (Title and Current Time)
        context.drawTextWithShadow(tr, titleLabel, padding, padding, mainTextColor);
        context.drawTextWithShadow(tr, titleTime, totalBoxWidth - padding - tr.getWidth(titleTime), padding, mainTextColor);

        // 3. Render the decorative separator line
        int separatorColor = GSRColorHelper.applyAlpha(0xFFFFFF, 0.31f * fadeAlpha);
        context.fill(2, padding + rowHeight + 1, totalBoxWidth - 2, padding + rowHeight + 2, separatorColor);

        // 4. Render Split rows
        int currentY = padding + rowHeight + 5;
        for (String[] split : splitData) {
            // Split name and status icon
            context.drawTextWithShadow(tr, split[2] + split[0], padding, currentY, mainTextColor);

            // Split time (Right aligned)
            int tW = tr.getWidth(split[1]);
            context.drawTextWithShadow(tr, split[1], totalBoxWidth - padding - tW, currentY, mainTextColor);

            currentY += rowHeight;
        }

        context.getMatrices().popMatrix();
    }

    /**
     * Converts raw split data into UI strings with icons.
     * ✔ = Completed, ○ = Pending, ★ = Most recent split.
     */
    @Unique
    private String[] prepareLine(String name, long ticks, long latest) {
        if (ticks <= 0) return new String[]{"§7○ " + name, "§7--:--", ""};
        return new String[]{"§a✔ " + name, "§f" + formatFullTime(ticks), (ticks == latest) ? "§6★ " : ""};
    }

    /**
     * Converts tick count into a formatted string: MM:SS.CC or H:MM:SS.CC
     */
    @Unique
    private String formatFullTime(long ticks) {
        long totalMs = ticks * 50;
        long h = totalMs / 3600000;
        long m = (totalMs / 60000) % 60;
        long s = (totalMs / 1000) % 60;
        long f = (totalMs % 1000) / 10;

        return h > 0 ?
                String.format("%d:%02d:%02d.%02d", h, m, s, f) :
                String.format("%02d:%02d.%02d", m, s, f);
    }
}