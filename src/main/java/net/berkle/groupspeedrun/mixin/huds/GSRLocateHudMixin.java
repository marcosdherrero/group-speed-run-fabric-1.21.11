package net.berkle.groupspeedrun.mixin.huds;

import net.berkle.groupspeedrun.GSRMain;
import net.berkle.groupspeedrun.config.GSRConfigPlayer;
import net.berkle.groupspeedrun.mixin.accessors.BossBarHudAccessor; // NEW IMPORT
import net.berkle.groupspeedrun.util.GSRColorHelper;
import net.berkle.groupspeedrun.util.GSRAlphaUtil;
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

        var pConfig = GSRConfigPlayer.INSTANCE;

        // --- FADE & VISIBILITY LOGIC ---
        boolean isFinished = config.wasVictorious || config.isFailed;
        long ticksSinceEnd = client.world.getTime() - config.frozenTime;
        float fadeAlpha = GSRAlphaUtil.getFadeAlpha(client, config, isFinished, ticksSinceEnd);

        if (fadeAlpha <= 0.01f) return;

        RegistryKey<World> currentDim = client.world.getRegistryKey();

        boolean showFortress = config.fortressActive && currentDim == World.NETHER;
        boolean showBastion = config.bastionActive && currentDim == World.NETHER;
        boolean showStronghold = config.strongholdActive && currentDim == World.OVERWORLD;
        boolean showShip = config.shipActive && currentDim == World.END;

        if (!showFortress && !showBastion && !showStronghold && !showShip) return;

        // --- UI POSITIONING ---
        int centerX = context.getScaledWindowWidth() / 2;
        int screenH = context.getScaledWindowHeight();

        int y;
        if (pConfig.locateHudOnTop) {
            // Start with a base 15px margin from the top
            int topOffset = 15;

            // Cast the BossBarHud to our Accessor interface
            var bossBarHud = client.inGameHud.getBossBarHud();
            var activeBars = ((BossBarHudAccessor) bossBarHud).getGSRBossBars();

            if (!activeBars.isEmpty()) {
                // Each vanilla boss bar is roughly 19px high including spacing
                // We shift down based on the number of bars + extra 5px buffer
                topOffset += (activeBars.size() * 19);
            }
            y = topOffset;
        } else {
            // Bottom position (above hotbar/experience bar)
            y = screenH - 70;
        }

        // Render the base tracking bar
        renderTrackingBar(context, centerX, y, fadeAlpha);

        // Render active icons
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
     * Renders the horizontal compass bar background and borders.
     */
    private void renderTrackingBar(DrawContext context, int centerX, int y, float alpha) {
        var pConfig = GSRConfigPlayer.INSTANCE;
        float hudScale = pConfig.locateHudScale;
        int halfWidth = (int) ((pConfig.barWidth / 2.0) * hudScale);
        int barHeight = (int) (pConfig.barHeight * hudScale);

        int x1 = centerX - halfWidth;
        int x2 = centerX + halfWidth;
        int y1 = y + 7;
        int y2 = y1 + barHeight;

        context.fill(x1, y1, x2, y2, GSRColorHelper.applyAlpha(0x000000, alpha));
        renderHorizontalGradient(context, x1, y1, x2, y2,
                GSRColorHelper.applyAlpha(0x333333, alpha),
                GSRColorHelper.applyAlpha(0x444444, alpha));

        context.fill(x1, y1 - 1, x2, y1, GSRColorHelper.applyAlpha(0xAAAAAA, alpha));
        context.fill(x1, y2, x2, y2 + 1, GSRColorHelper.applyAlpha(0x444444, alpha));
        context.fill(x1 - 1, y1 - 1, x1, y2 + 1, GSRColorHelper.applyAlpha(0xAAAAAA, alpha));
        context.fill(x2, y1 - 1, x2 + 1, y2 + 1, GSRColorHelper.applyAlpha(0x444444, alpha));
    }

    /**
     * Handles rotation math and dynamic scaling for structure icons.
     */
    private void renderIcon(DrawContext context, MinecraftClient client, int centerX, int y, int targetX, int targetZ, ItemStack stack, int themeColor, float alpha) {
        var pConfig = GSRConfigPlayer.INSTANCE;
        float hudScale = pConfig.locateHudScale;

        double deltaX = targetX - client.player.getX();
        double deltaZ = targetZ - client.player.getZ();
        double distance = Math.sqrt(deltaX * deltaX + deltaZ * deltaZ);

        float angleToTarget = (float) Math.toDegrees(Math.atan2(deltaZ, deltaX)) - 90.0f;
        float relativeAngle = MathHelper.wrapDegrees(angleToTarget - client.player.getYaw());

        float frameHalfWidth = 9.0f * hudScale;
        float maxOffset = ((pConfig.barWidth / 2.0f) * hudScale) - frameHalfWidth;
        float xOffset = MathHelper.clamp((relativeAngle * (maxOffset / 90.0f)), -maxOffset, maxOffset);

        float iconX = (float) centerX + xOffset;
        float iconY = (float) y + (8.5f * hudScale);

        context.getMatrices().pushMatrix();
        context.getMatrices().translate(iconX, iconY);
        context.getMatrices().scale(hudScale, hudScale);
        context.fill(-9, -9, 9, 9, GSRColorHelper.applyAlpha(themeColor, alpha));
        context.fill(-8, -8, 8, 8, GSRColorHelper.applyAlpha(0x000000, 0.25f * alpha));
        context.getMatrices().popMatrix();

        float distFactor = MathHelper.clamp((float) (1.0 - (distance / (double)pConfig.maxScaleDistance)), 0.0f, 1.0f);
        float finalIconScale = MathHelper.lerp(distFactor, pConfig.MIN_ICON_SCALE, pConfig.MAX_ICON_SCALE) * hudScale;

        context.getMatrices().pushMatrix();
        context.getMatrices().translate(iconX, iconY);
        context.getMatrices().scale(finalIconScale, finalIconScale);
        context.getMatrices().translate(-8.0f, -8.0f);
        context.drawItem(stack, 0, 0);
        context.getMatrices().popMatrix();
    }

    private void renderHorizontalGradient(DrawContext context, int x1, int y1, int x2, int y2, int colorStart, int colorEnd) {
        for (int i = x1; i < x2; i++) {
            float ratio = (float) (i - x1) / (x2 - x1);
            int color = interpolateColor(colorStart, colorEnd, ratio);
            context.fill(i, y1, i + 1, y2, color);
        }
    }

    private int interpolateColor(int color1, int color2, float ratio) {
        int a = (int) MathHelper.lerp(ratio, (color1 >> 24) & 0xFF, (color2 >> 24) & 0xFF);
        int r = (int) MathHelper.lerp(ratio, (color1 >> 16) & 0xFF, (color2 >> 16) & 0xFF);
        int g = (int) MathHelper.lerp(ratio, (color1 >> 8) & 0xFF, (color2 >> 8) & 0xFF);
        int b = (int) MathHelper.lerp(ratio, color1 & 0xFF, color2 & 0xFF);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }
}