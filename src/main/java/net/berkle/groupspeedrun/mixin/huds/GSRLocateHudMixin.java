package net.berkle.groupspeedrun.mixin.huds;

import net.berkle.groupspeedrun.config.GSRConfig;
import net.berkle.groupspeedrun.GSRMain;
import net.berkle.groupspeedrun.util.GSRColorHelper;
import net.berkle.groupspeedrun.util.GSRTimerHudState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.math.MathHelper;
import net.minecraft.registry.RegistryKey;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(InGameHud.class)
public class GSRLocateHudMixin {

    @Inject(method = "render", at = @At("TAIL"))
    private void renderGSRHud(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.options.hudHidden || client.world == null) return;

        var config = GSRMain.CONFIG;
        if (config == null || config.startTime < 0) return;

        // --- SHARED FADE LOGIC ---
        // This line pulls the alpha value which includes:
        // 1. The 10-second Split Pop-up
        // 2. The Tab-key held animation
        // 3. The Victory/Fail end-of-run display
        boolean isFinished = config.wasVictorious || config.isFailed;
        long ticksSinceEnd = client.world.getTime() - config.frozenTime;
        float fadeAlpha = GSRTimerHudState.getFadeAlpha(client, config, isFinished, ticksSinceEnd);

        // If the state manager says we're invisible, stop immediately
        if (fadeAlpha <= 0.01f) return;
        // -------------------------

        RegistryKey<World> currentDim = client.world.getRegistryKey();

        // Check if any structures are currently active in the player's current dimension
        boolean showFortress = config.fortressActive && currentDim == World.NETHER;
        boolean showBastion = config.bastionActive && currentDim == World.NETHER;
        boolean showStronghold = config.strongholdActive && currentDim == World.OVERWORLD;
        boolean showShip = config.shipActive && currentDim == World.END;

        if (!showFortress && !showBastion && !showStronghold && !showShip) return;

        // Calculate screen positions
        int centerX = context.getScaledWindowWidth() / 2;
        int screenH = context.getScaledWindowHeight();
        int y = config.locateHudOnTop ? 15 : (screenH - 70);

        // Draw the background horizontal compass bar
        renderTrackingBar(context, centerX, y, fadeAlpha);

        // Render each active icon on the bar
        if (showFortress) {
            renderIcon(context, client, centerX, y, config.fortressX, config.fortressZ,
                    new ItemStack(Items.BLAZE_ROD), config.getFortressColorInt(), fadeAlpha);
        }
        if (showBastion) {
            renderIcon(context, client, centerX, y, config.bastionX, config.bastionZ,
                    new ItemStack(Items.PIGLIN_HEAD), config.getBastionColorInt(), fadeAlpha);
        }
        if (showStronghold) {
            renderIcon(context, client, centerX, y, config.strongholdX, config.strongholdZ,
                    new ItemStack(Items.ENDER_EYE), config.getStrongholdColorInt(), fadeAlpha);
        }
        if (showShip) {
            renderIcon(context, client, centerX, y, config.shipX, config.shipZ,
                    new ItemStack(Items.ELYTRA), config.getShipColorInt(), fadeAlpha);
        }
    }

    /**
     * Draws the main horizontal bar that acts as a compass track.
     */
    private void renderTrackingBar(DrawContext context, int centerX, int y, float alpha) {
        var config = GSRMain.CONFIG;
        float hudScale = config.locateHudScale;
        int halfWidth = (int) ((config.barWidth / 2.0) * hudScale);
        int barHeight = (int) (config.barHeight * hudScale);

        int x1 = centerX - halfWidth;
        int x2 = centerX + halfWidth;
        int y1 = y + 7;
        int y2 = y1 + barHeight;

        // Main background and gradient center
        context.fill(x1, y1, x2, y2, GSRColorHelper.applyAlpha(0x000000, alpha));
        renderHorizontalGradient(context, x1, y1, x2, y2,
                GSRColorHelper.applyAlpha(0x333333, alpha),
                GSRColorHelper.applyAlpha(0x444444, alpha));

        // Dimensional borders (Gray tones)
        context.fill(x1, y1 - 1, x2, y1, GSRColorHelper.applyAlpha(0xAAAAAA, alpha));
        context.fill(x1, y2, x2, y2 + 1, GSRColorHelper.applyAlpha(0x444444, alpha));
        context.fill(x1 - 1, y1 - 1, x1, y2 + 1, GSRColorHelper.applyAlpha(0xAAAAAA, alpha));
        context.fill(x2, y1 - 1, x2 + 1, y2 + 1, GSRColorHelper.applyAlpha(0x444444, alpha));
    }

    /**
     * Calculates the position of a structure icon on the bar based on player rotation (Yaw).
     */
    private void renderIcon(DrawContext context, MinecraftClient client, int centerX, int y, int targetX, int targetZ, ItemStack stack, int themeColor, float alpha) {
        var config = GSRMain.CONFIG;
        float hudScale = config.locateHudScale;

        // 1. Position Math (Clamped to bar interior)
        double deltaX = targetX - client.player.getX();
        double deltaZ = targetZ - client.player.getZ();
        double distance = Math.sqrt(deltaX * deltaX + deltaZ * deltaZ);

        float angleToTarget = (float) Math.toDegrees(Math.atan2(deltaZ, deltaX)) - 90.0f;
        float relativeAngle = MathHelper.wrapDegrees(angleToTarget - client.player.getYaw());

        float frameHalfWidth = 9.0f * hudScale;
        float maxOffset = ((config.barWidth / 2.0f) * hudScale) - frameHalfWidth;
        float xOffset = MathHelper.clamp((relativeAngle * (maxOffset / 90.0f)), -maxOffset, maxOffset);

        float iconX = (float) centerX + xOffset;
        float iconY = (float) y + (8.5f * hudScale);

        // --- Render Background Frame (Fixed Size) ---
        context.getMatrices().pushMatrix();
        context.getMatrices().translate(iconX, iconY);
        context.getMatrices().scale(hudScale, hudScale);

        int themed = GSRColorHelper.applyAlpha(themeColor, alpha);
        int bg = GSRColorHelper.applyAlpha(0x000000, 0.25f * alpha);

        context.fill(-9, -9, 9, 9, themed);
        context.fill(-8, -8, 8, 8, bg);
        context.getMatrices().popMatrix();

        // --- Render Scaled Item (Proportional to Frame) ---
        // distFactor: 0.0 at max distance, 1.0 at 0 blocks
        float distFactor = MathHelper.clamp((float) (1.0 - (distance / (double)config.maxScaleDistance)), 0.0f, 1.0f);

        /**
         * Scaling Logic:
         * Min scale: 0.5f (Small icon inside the box)
         * Max scale: 1.0f (Standard size, fits perfectly in the 18x18 frame)
         * We multiply by hudScale at the end to respect the user's global settings.
         */
        float minInternalScale = 0.5f;
        float maxInternalScale = 1.0f;
        float finalIconScale = MathHelper.lerp(distFactor, minInternalScale, maxInternalScale) * hudScale;

        context.getMatrices().pushMatrix();
        context.getMatrices().translate(iconX, iconY);
        context.getMatrices().scale(finalIconScale, finalIconScale);
        context.getMatrices().translate(-8.0f, -8.0f); // Centers the 16x16 item

        context.drawItem(stack, 0, 0);
        context.getMatrices().popMatrix();
    }

    /**
     * Loops through pixels to create a custom horizontal gradient since DrawContext
     * default gradients are usually vertical.
     */
    private void renderHorizontalGradient(DrawContext context, int x1, int y1, int x2, int y2, int colorStart, int colorEnd) {
        for (int i = x1; i < x2; i++) {
            float ratio = (float) (i - x1) / (x2 - x1);
            int color = interpolateColor(colorStart, colorEnd, ratio);
            context.fill(i, y1, i + 1, y2, color);
        }
    }

    /**
     * Color interpolation helper to blend two ARGB colors.
     */
    private int interpolateColor(int color1, int color2, float ratio) {
        int a = (int) MathHelper.lerp(ratio, (color1 >> 24) & 0xFF, (color2 >> 24) & 0xFF);
        int r = (int) MathHelper.lerp(ratio, (color1 >> 16) & 0xFF, (color2 >> 16) & 0xFF);
        int g = (int) MathHelper.lerp(ratio, (color1 >> 8) & 0xFF, (color2 >> 8) & 0xFF);
        int b = (int) MathHelper.lerp(ratio, color1 & 0xFF, color2 & 0xFF);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }
}