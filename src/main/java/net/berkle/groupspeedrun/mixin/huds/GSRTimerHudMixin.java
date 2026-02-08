package net.berkle.groupspeedrun.mixin.huds;

import net.berkle.groupspeedrun.GSRMain;
import net.berkle.groupspeedrun.GSRClient; // Import the client to get the config
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

@Mixin(InGameHud.class)
public class GSRTimerHudMixin {

    @Inject(method = "render", at = @At("TAIL"))
    private void renderScoreboardTimer(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        MinecraftClient client = MinecraftClient.getInstance();

        if (client.player == null || client.options.hudHidden || client.world == null) return;

        GSRConfigWorld worldConfig = GSRMain.CONFIG;
        // FIX: Access the PLAYER_CONFIG stored in GSRClient
        GSRConfigPlayer playerConfig = GSRClient.PLAYER_CONFIG;

        if (worldConfig == null || playerConfig == null) return;

        // --- 2. ALPHA & VISIBILITY LOGIC ---
        boolean isFinished = worldConfig.isVictorious || worldConfig.isFailed;
        long currentTime = client.world.getTime();
        long ticksSinceEnd = currentTime - worldConfig.lastSplitTime;

        float fadeAlpha = GSRAlphaUtil.getFadeAlpha(client, worldConfig, isFinished, ticksSinceEnd);
        if (fadeAlpha <= 0.001f) return;

        // --- 3. PREPARE DATA & STRINGS ---
        TextRenderer tr = client.textRenderer;
        long displayTicks = worldConfig.getElapsedTime() / 50;

        String titleLabel = worldConfig.isVictorious ? "§a§lGSR VICTORY!" : (worldConfig.isFailed ? "§c§lGSR FAIL" : "§6§lGSR Time:");
        String timeColor = worldConfig.isVictorious ? "§a" : (worldConfig.isFailed ? "§c" : "§f");
        String pauseTag = worldConfig.isTimerFrozen && !isFinished ? " §7[PAUSED]" : "";
        String titleTime = timeColor + GSRFormatUtil.formatTime(displayTicks) + pauseTag;

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
        int nameColWidth = tr.getWidth(titleLabel);
        int timeColWidth = tr.getWidth(titleTime);
        for (String[] split : splitData) {
            nameColWidth = Math.max(nameColWidth, tr.getWidth(split[0]));
            timeColWidth = Math.max(timeColWidth, tr.getWidth(split[1]));
        }

        final int padding = 6;
        final int rowHeight = 10;
        final int totalBoxWidth = nameColWidth + 15 + timeColWidth + (padding * 2);
        final int boxHeight = ((splitData.length + 1) * rowHeight) + (padding * 2) + 4;

        // --- 5. TRANSFORMATIONS ---
        context.getMatrices().pushMatrix();

        float scale = playerConfig.timerHudScale;

        // Pivot calculations
        float scaledWidth = totalBoxWidth * scale;
        float scaledHeight = boxHeight * scale;

        float x = playerConfig.timerHudOnRight ? (context.getScaledWindowWidth() - 10 - scaledWidth) : 10;
        float y = (context.getScaledWindowHeight() / 2f) - (scaledHeight / 2f) - (context.getScaledWindowHeight() * 0.15f);

        // Move to position and THEN scale
        context.getMatrices().translate(x, y);
        context.getMatrices().scale(scale, scale);

        // --- 6. FINAL RENDERING ---
        // Render background
        context.fill(0, 0, totalBoxWidth, boxHeight, GSRColorHelper.getBackgroundWithAlpha(0x90, fadeAlpha));

        int mainTextColor = GSRColorHelper.applyAlpha(0xFFFFFF, fadeAlpha);
        int sepCol = GSRColorHelper.applyAlpha(0xFFFFFF, 0.31f * fadeAlpha);

        // Draw Headers
        context.drawTextWithShadow(tr, titleLabel, padding, padding, mainTextColor);
        context.drawTextWithShadow(tr, titleTime, totalBoxWidth - padding - tr.getWidth(titleTime), padding, mainTextColor);

        // Draw Separator Line
        context.fill(2, padding + rowHeight + 2, totalBoxWidth - 2, padding + rowHeight + 3, sepCol);

        // Draw Split Rows
        int currentY = padding + rowHeight + 6;
        for (String[] split : splitData) {
            context.drawTextWithShadow(tr, split[0], padding, currentY, mainTextColor);
            context.drawTextWithShadow(tr, split[1], totalBoxWidth - padding - tr.getWidth(split[1]), currentY, mainTextColor);
            currentY += rowHeight;
        }

        context.getMatrices().popMatrix();
    }

    @Unique
    private String[] prepareLine(String name, long ticks, long latest) {
        if (ticks <= 0) return new String[]{"§7○ " + name, "§7--:--"};
        String icon = (ticks == latest) ? "§6★ " : "§a✔ ";
        return new String[]{icon + name, "§f" + GSRFormatUtil.formatTime(ticks)};
    }
}