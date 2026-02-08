package net.berkle.groupspeedrun.mixin.huds;

import net.berkle.groupspeedrun.GSRMain;
import net.berkle.groupspeedrun.GSRClient;
import net.berkle.groupspeedrun.config.GSRConfigPlayer;
import net.berkle.groupspeedrun.mixin.accessors.BossBarHudAccessor;
import net.berkle.groupspeedrun.util.GSRColorHelper;
import net.berkle.groupspeedrun.util.GSRAlphaUtil;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.math.MathHelper;
import net.minecraft.registry.RegistryKey;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
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

        GSRConfigPlayer pConfig = GSRClient.PLAYER_CONFIG;

        boolean isFinished = config.isVictorious || config.isFailed;
        long currentTime = client.world.getTime();
        long ticksSinceEnd = currentTime - config.lastSplitTime;
        float fadeAlpha = GSRAlphaUtil.getFadeAlpha(client, config, isFinished, ticksSinceEnd);

        if (fadeAlpha <= 0.01f) return;

        RegistryKey<World> currentDim = client.world.getRegistryKey();

        boolean showFortress = config.fortressActive && currentDim == World.NETHER;
        boolean showBastion = config.bastionActive && currentDim == World.NETHER;
        boolean showStronghold = config.strongholdActive && currentDim == World.OVERWORLD;
        boolean showShip = config.shipActive && currentDim == World.END;

        if (!showFortress && !showBastion && !showStronghold && !showShip) return;

        int centerX = context.getScaledWindowWidth() / 2;
        int y = pConfig.locateHudOnTop ? 15 : context.getScaledWindowHeight() - 70;

        if (pConfig.locateHudOnTop) {
            var bossBarHud = client.inGameHud.getBossBarHud();
            var activeBars = ((BossBarHudAccessor) bossBarHud).getGSRBossBars();
            if (!activeBars.isEmpty()) y += (activeBars.size() * 19);
        }

        renderTrackingBar(context, pConfig, centerX, y, fadeAlpha);

        if (showFortress) renderIcon(context, client, pConfig, centerX, y, config.fortressX, config.fortressZ, new ItemStack(Items.BLAZE_ROD), config.getFortressColorInt(), fadeAlpha);
        if (showBastion) renderIcon(context, client, pConfig, centerX, y, config.bastionX, config.bastionZ, new ItemStack(Items.PIGLIN_HEAD), config.getBastionColorInt(), fadeAlpha);
        if (showStronghold) renderIcon(context, client, pConfig, centerX, y, config.strongholdX, config.strongholdZ, new ItemStack(Items.ENDER_EYE), config.getStrongholdColorInt(), fadeAlpha);
        if (showShip) renderIcon(context, client, pConfig, centerX, y, config.shipX, config.shipZ, new ItemStack(Items.ELYTRA), config.getShipColorInt(), fadeAlpha);
    }

    @Unique
    private void renderTrackingBar(DrawContext context, GSRConfigPlayer pConfig, int centerX, int y, float alpha) {
        float scale = pConfig.locateHudScale;
        int halfW = (int) ((pConfig.barWidth / 2.0) * scale);
        int barH = (int) (pConfig.barHeight * scale);
        int x1 = centerX - halfW;
        int x2 = centerX + halfW;
        int y1 = y + 7;

        context.fill(x1, y1, x2, y1 + barH, GSRColorHelper.applyAlpha(0x000000, 0.5f * alpha));
        renderHorizontalGradient(context, x1, y1, x2, y1 + barH, GSRColorHelper.applyAlpha(0x333333, alpha), GSRColorHelper.applyAlpha(0x444444, alpha));

        // Tick Marks
        for (int i = 0; i < 5; i++) {
            float rel = (i / 4.0f) * 2 - 1;
            int tx = centerX + (int)(rel * halfW);
            int tTall = (i % 2 == 0) ? 3 : 2;
            context.fill(tx, y1 - tTall, tx + 1, y1, GSRColorHelper.applyAlpha(0xCCCCCC, alpha * 0.8f));
        }

        context.fill(x1, y1 - 1, x2, y1, GSRColorHelper.applyAlpha(0xAAAAAA, alpha));
        context.fill(x1, y1 + barH, x2, y1 + barH + 1, GSRColorHelper.applyAlpha(0x444444, alpha));
    }

    @Unique
    private void renderIcon(DrawContext context, MinecraftClient client, GSRConfigPlayer pConfig, int centerX, int y, int tX, int tZ, ItemStack stack, int themeColor, float alpha) {
        float hScale = pConfig.locateHudScale;
        double dX = tX - client.player.getX();
        double dZ = tZ - client.player.getZ();
        double distance = Math.sqrt(dX * dX + dZ * dZ);

        float angle = MathHelper.wrapDegrees((float) Math.toDegrees(Math.atan2(dZ, dX)) - 90.0f - client.player.getYaw());
        float maxOff = ((pConfig.barWidth / 2.0f) * hScale) - (9.0f * hScale);
        float xOff = MathHelper.clamp((angle * (maxOff / 90.0f)), -maxOff, maxOff);

        float drawX = centerX + xOff;
        float drawY = y + (8.5f * hScale);

        boolean targeting = Math.abs(angle) < 5.0f;
        float pulse = targeting ? (float)(Math.sin(client.world.getTime() * 0.3f) * 0.2f + 0.8f) : 0.0f;

        // 1. Box Background
        context.getMatrices().pushMatrix(); // Explicit JOML call
        context.getMatrices().translate(drawX, drawY);
        context.getMatrices().scale(hScale, hScale);

        if (targeting) {
            context.fill(-10, -10, 10, 10, GSRColorHelper.applyAlpha(0xFFFFFF, alpha * pulse));
        }

        context.fill(-9, -9, 9, 9, GSRColorHelper.applyAlpha(themeColor, alpha));
        context.fill(-8, -8, 8, 8, GSRColorHelper.applyAlpha(0x000000, 0.45f * alpha));
        context.getMatrices().popMatrix(); // Explicit JOML call

        // 2. Icon Scaling
        float distFactor = MathHelper.clamp((float) (1.0 - (distance / pConfig.maxScaleDistance)), 0.0f, 1.0f);
        float iconScale = MathHelper.lerp(distFactor, pConfig.MIN_ICON_SCALE, pConfig.MAX_ICON_SCALE) * hScale;

        context.getMatrices().pushMatrix();
        context.getMatrices().translate(drawX, drawY);
        context.getMatrices().scale(iconScale, iconScale);
        context.drawItem(stack, -8, -8);
        context.getMatrices().popMatrix();

        // 3. Distance Text (New feature)
        if (targeting) {
            TextRenderer tr = client.textRenderer;
            String distText = (int)distance + "m";
            float textY = drawY + (10 * hScale);
            context.getMatrices().pushMatrix();
            context.getMatrices().translate(drawX, textY);
            context.getMatrices().scale(hScale * 0.8f, hScale * 0.8f);
            context.drawTextWithShadow(tr, distText, -tr.getWidth(distText) / 2, 0, GSRColorHelper.applyAlpha(0xFFFFFF, alpha));
            context.getMatrices().popMatrix();
        }
    }

    @Unique
    private void renderHorizontalGradient(DrawContext context, int x1, int y1, int x2, int y2, int colorStart, int colorEnd) {
        for (int i = x1; i < x2; i++) {
            float ratio = (float) (i - x1) / (x2 - x1);
            context.fill(i, y1, i + 1, y2, interpolateColor(colorStart, colorEnd, ratio));
        }
    }

    @Unique
    private int interpolateColor(int color1, int color2, float ratio) {
        int a = (int) MathHelper.lerp(ratio, (color1 >> 24) & 0xFF, (color2 >> 24) & 0xFF);
        int r = (int) MathHelper.lerp(ratio, (color1 >> 16) & 0xFF, (color2 >> 16) & 0xFF);
        int g = (int) MathHelper.lerp(ratio, (color1 >> 8) & 0xFF, (color2 >> 8) & 0xFF);
        int b = (int) MathHelper.lerp(ratio, color1 & 0xFF, color2 & 0xFF);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }
}