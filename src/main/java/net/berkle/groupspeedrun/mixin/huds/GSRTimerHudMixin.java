package net.berkle.groupspeedrun.mixin.huds;

import net.berkle.groupspeedrun.GSRMain;
import net.berkle.groupspeedrun.config.GSRConfigPlayer;
import net.berkle.groupspeedrun.config.GSRConfigWorld;
import net.berkle.groupspeedrun.util.GSRColorHelper;
import net.berkle.groupspeedrun.util.GSRFormatUtil;
import net.berkle.groupspeedrun.util.GSRAlphaUtil;
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

/**
 * Mixin to inject the Speedrun Timer into the Minecraft In-Game HUD.
 * Handles the layout, scaling, and dynamic transparency of the timer box.
 */
@Mixin(InGameHud.class)
public class GSRTimerHudMixin {

    @Inject(method = "render", at = @At("TAIL"))
    private void renderScoreboardTimer(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        MinecraftClient client = MinecraftClient.getInstance();

        // 1. LIFECYCLE SAFETY CHECKS
        // Prevent rendering if the player is dead, the HUD is hidden (F1), or world is loading.
        if (client.player == null || client.options.hudHidden || client.world == null) return;

        GSRConfigWorld worldConfig = GSRMain.CONFIG;
        GSRConfigPlayer playerConfig = GSRConfigPlayer.INSTANCE;

        if (worldConfig == null) return;

        // --- 2. ALPHA & VISIBILITY LOGIC ---
        // Note: We do NOT check hudMode or Tab-press here. We delegate that to the AlphaUtil.
        // This ensures the "Fade Out" animation can finish even after the user releases the Tab key.
        boolean isFinished = worldConfig.isVictorious || worldConfig.isFailed;
        long currentTime = client.world.getTime();
        long ticksSinceEnd = currentTime - worldConfig.lastSplitTime;

        // Calculate the smooth alpha based on Tab press, split pop-ups, and victory state.
        float fadeAlpha = GSRAlphaUtil.getFadeAlpha(client, worldConfig, isFinished, ticksSinceEnd);

        // If the alpha is effectively zero, stop execution to save performance.
        if (fadeAlpha <= 0.001f) return;

        // --- 3. PREPARE DATA & STRINGS ---
        TextRenderer tr = client.textRenderer;
        long displayTicks = worldConfig.getElapsedTime() / 50;

        // Dynamic Header Strings
        String titleLabel = worldConfig.isVictorious ? "§a§lGSR VICTORY!" : (worldConfig.isFailed ? "§c§lGSR FAIL" : "§6§lGSR Time:");
        String timeColor = worldConfig.isVictorious ? "§a" : (worldConfig.isFailed ? "§c" : "§f");
        String pauseTag = worldConfig.isTimerFrozen && !isFinished ? " §7[PAUSED]" : "";
        String titleTime = timeColor + GSRFormatUtil.formatTime(displayTicks) + pauseTag;

        // Determine which split is the "latest" to show the gold star icon
        long latestTime = Math.max(worldConfig.timeNether, Math.max(worldConfig.timeBastion,
                Math.max(worldConfig.timeFortress, Math.max(worldConfig.timeEnd, worldConfig.timeDragon))));

        String[][] splitData = {
                prepareLine("Nether", worldConfig.timeNether, latestTime),
                prepareLine("Bastion", worldConfig.timeBastion, latestTime),
                prepareLine("Fortress", worldConfig.timeFortress, latestTime),
                prepareLine("The End", worldConfig.timeEnd, latestTime),
                prepareLine("Dragon", worldConfig.timeDragon, latestTime)
        };

        // --- 4. DYNAMIC UI SIZING ---
        // Calculate the box width based on the longest string to prevent text overflow.
        int nameColWidth = tr.getWidth(titleLabel);
        int timeColWidth = tr.getWidth(titleTime);
        for (String[] split : splitData) {
            nameColWidth = Math.max(nameColWidth, tr.getWidth(split[2] + split[0]));
            timeColWidth = Math.max(timeColWidth, tr.getWidth(split[1]));
        }

        final int padding = 6;
        final int rowHeight = 10;
        final int totalBoxWidth = nameColWidth + 10 + timeColWidth + (padding * 2);
        final int boxHeight = ((splitData.length + 1) * rowHeight) + (padding * 2) + 4;

        // --- 5. TRANSFORMATIONS (POSITION & SCALE) ---
        context.getMatrices().pushMatrix();

        float scale = playerConfig.timerHudScale;

        // 1. Calculate the actual width/height on screen after scaling
        float scaledWidth = totalBoxWidth * scale;
        float scaledHeight = boxHeight * scale;

        // 2. Determine Pivot X (The corner point)
        float pivotX = playerConfig.timerHudOnRight ? (context.getScaledWindowWidth() - 10 - scaledWidth) : 10;

        // 3. Determine Pivot Y (Centered vertically with 15% upward offset)
        float pivotY = (context.getScaledWindowHeight() / 2f) - (scaledHeight / 2f) - (context.getScaledWindowHeight() * 0.15f);

        // 4. Apply transformations
        // We translate to the final screen position FIRST, then scale.
        context.getMatrices().translate(pivotX, pivotY);
        context.getMatrices().scale(scale, scale);

        // --- 6. FINAL RENDERING ---
        // Note: Because the matrix is now scaled, we draw from (0,0) to (totalBoxWidth, boxHeight)
        // and OpenGL handles the size reduction for us.

        // --- 6. FINAL RENDERING ---
        // Render the semi-transparent background box
        context.fill(0, 0, totalBoxWidth, boxHeight, GSRColorHelper.getBackgroundWithAlpha(0x90, fadeAlpha));

        // Prepare colors with applied alpha
        int mainTextColor = GSRColorHelper.applyAlpha(0xFFFFFF, fadeAlpha);
        int sepCol = GSRColorHelper.applyAlpha(0xFFFFFF, 0.31f * fadeAlpha);

        // Draw Headers
        context.drawTextWithShadow(tr, titleLabel, padding, padding, mainTextColor);
        context.drawTextWithShadow(tr, titleTime, totalBoxWidth - padding - tr.getWidth(titleTime), padding, mainTextColor);

        // Draw Separator Line
        context.fill(2, padding + rowHeight + 1, totalBoxWidth - 2, padding + rowHeight + 2, sepCol);

        // Draw Split Rows
        int currentY = padding + rowHeight + 5;
        for (String[] split : splitData) {
            context.drawTextWithShadow(tr, split[2] + split[0], padding, currentY, mainTextColor);
            context.drawTextWithShadow(tr, split[1], totalBoxWidth - padding - tr.getWidth(split[1]), currentY, mainTextColor);
            currentY += rowHeight;
        }

        context.getMatrices().popMatrix();
    }

    @Unique
    private String[] prepareLine(String name, long ticks, long latest) {
        // 1. Not Started State
        if (ticks <= 0) {
            return new String[]{"§7○ " + name, "§7--:--", ""};
        }
        // 2. Completed State: Decide between Star (Latest) or Checkmark (Finished)
        String icon = (ticks == latest) ? "§6★ " : "§a✔ ";
        // Return the name with the chosen icon and the time
        // The third element is now empty because the icon is integrated into the first
        return new String[]{icon + name, "§f" + GSRFormatUtil.formatTime(ticks), ""};
    }
}