package net.berkle.groupspeedrun;

import net.berkle.groupspeedrun.config.GSRConfigWorld;
import net.berkle.groupspeedrun.managers.GSRRunHistoryManager;
import net.berkle.groupspeedrun.managers.GSRSplitManager;
import net.minecraft.advancement.AdvancementProgress;
import net.minecraft.advancement.PlayerAdvancementTracker;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.projectile.FireworkRocketEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.GameMode;
import java.util.List;

/**
 * Main event handler for the Group Speedrun mod.
 * Uses System.currentTimeMillis() for real-world time accuracy.
 */
public class GSREvents {
    // Tracks the group health from the previous tick to calculate delta changes
    private static float lastGroupHealth = -1f;

    /**
     * Core tick logic. Called 20 times per second.
     */
    public static void onTick(MinecraftServer server) {
        var config = GSRMain.CONFIG;
        if (config == null) return;

        /*
         * 1. AUTO-START DETECTION
         * Only runs if startTime is -1 (meaning no run is currently active OR resumed).
         */
        if (config.startTime == -1 && !server.getPlayerManager().getPlayerList().isEmpty()) {
            handleAutoStart(server, config);
        }

        /*
         * 2. PAUSE STATE PROTECTION
         * If the timer is frozen, we maintain player vitals but skip game logic.
         */
        if (config.isTimerFrozen) {
            handlePauseMaintenance(server);
            return;
        }

        /*
         * 3. SHARED HEALTH ENGINE
         */
        if (config.sharedHealthEnabled && !config.isFailed && !config.wasVictorious) {
            handleSharedHealth(server);
        }

        /*
         * 4. PERIODIC SPLIT CHECKS
         */
        if (server.getTicks() % 10 == 0 && config.startTime > 0) {
            GSRSplitManager.checkSplits(server);
        }

        /*
         * 5. VICTORY CELEBRATIONS
         */
        if (config.wasVictorious && config.victoryTimer > 0) {
            if (config.victoryTimer % 10 == 0) {
                for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) spawnFirework(p);
            }
            config.victoryTimer--;
        }
    }

    /**
     * Converts the current real-world duration into game ticks.
     * Required by GSRRunHistoryManager and GSRSplitManager.
     * * @param server The MinecraftServer instance (kept for compatibility)
     * @return Total active run time in ticks (1 tick = 50ms).
     */
    public static long getRunTicks(MinecraftServer server) {
        var config = GSRMain.CONFIG;
        if (config == null || config.startTime < 0) return 0;

        // getElapsedTime() correctly handles active vs paused states
        long elapsedMs = config.getElapsedTime();
        return elapsedMs / 50;
    }

    /**
     * Records exactly how much time has passed to 'frozenTime' and pauses logic.
     */
    public static void pauseRun(MinecraftServer server) {
        var config = GSRMain.CONFIG;
        if (config != null && !config.isTimerFrozen && config.startTime > 0) {
            config.isTimerFrozen = true;
            config.frozenTime = System.currentTimeMillis() - config.startTime;

            server.getPlayerManager().broadcast(Text.literal("§6[GSR] §cTimer Paused!"), false);
            for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
                p.playSound(SoundEvents.UI_BUTTON_CLICK.value(), 1.0f, 1.0f);
            }
            GSRMain.saveAndSync(server);
        }
    }

    /**
     * Resumes a paused run and triggers a HUD pop-up.
     */
    public static void resumeRun(MinecraftServer server) {
        var config = GSRMain.CONFIG;
        if (config != null && config.isTimerFrozen) {
            config.isTimerFrozen = false;
            config.startTime = System.currentTimeMillis() - config.frozenTime;

            // Trigger HUD pop-up on resume
            config.lastSplitTime = server.getOverworld().getTime();

            server.getPlayerManager().broadcast(Text.literal("§6[GSR] §aTimer Resumed!"), false);
            for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
                p.playSound(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
            }
            GSRMain.saveAndSync(server);
        }
    }

    /**
     * Keeps players from starving or dying while the run is paused.
     */
    private static void handlePauseMaintenance(MinecraftServer server) {
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            if (player.isSpectator() || player.isCreative()) continue;
            player.getHungerManager().setFoodLevel(20);
            if (lastGroupHealth > 0) player.setHealth(lastGroupHealth);
        }
    }

    /**
     * Handles Group Death logic.
     */
    public static void handlePlayerDeath(ServerPlayerEntity deadPlayer, MinecraftServer server) {
        var config = GSRMain.CONFIG;

        // 1. STAT TRACKING
        // We track the death even if Group Death is disabled, as long as a run is active.
        if (config != null && config.startTime > 0) {
            // This ensures the death is recorded in the JSON for the post-run summary
            GSRStats.addInt(GSRStats.BLOCKS_BROKEN, deadPlayer.getUuid(), 0); // Example of calling addInt
            // Assuming you add a DEATHS map to GSRStats:
            // GSRStats.addInt(GSRStats.TOTAL_DEATHS, deadPlayer.getUuid(), 1);
        }

        // 2. FAIL CONDITION (Group Death Logic)
        if (config != null && config.startTime > 0 && config.groupDeathEnabled && !config.isFailed && !config.wasVictorious) {
            config.isFailed = true;
            config.isTimerFrozen = true;
            config.frozenTime = System.currentTimeMillis() - config.startTime;

            // Trigger HUD pop-up for the final time showing the death time
            config.lastSplitTime = server.getOverworld().getTime();

            GSRRunHistoryManager.saveRun(
                    server,
                    "FAILURE",
                    deadPlayer.getName().getString(),
                    deadPlayer.getDamageTracker().getDeathMessage().getString()
            );

            for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
                p.changeGameMode(GameMode.SPECTATOR);
                p.playSound(SoundEvents.ENTITY_LIGHTNING_BOLT_THUNDER, 1.0f, 1.0f);
            }

            GSRMain.saveAndSync(server);
        }
    }

    /**
     * Starts the run when player movement is detected.
     */
    private static void handleAutoStart(MinecraftServer server, GSRConfigWorld config) {
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            // Check for movement, item use, or swinging hands
            if (player.squaredDistanceTo(player.lastRenderX, player.lastRenderY, player.lastRenderZ) > 0.0001
                    || player.isUsingItem() || player.handSwinging) {

                config.startTime = System.currentTimeMillis();
                config.isTimerFrozen = false;
                config.frozenTime = 0;

                // Trigger HUD pop-up
                config.lastSplitTime = server.getOverworld().getTime();

                GSRMain.saveAndSync(server);

                for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
                    p.playSound(SoundEvents.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
                }

                server.getPlayerManager().broadcast(Text.literal("§6§l[GSR] §rTimer Started! Good luck."), false);
                break;
            }
        }
    }

    /**
     * Shared Health Engine.
     */
    private static void handleSharedHealth(MinecraftServer server) {
        List<ServerPlayerEntity> players = server.getPlayerManager().getPlayerList().stream()
                .filter(p -> p.isAlive() && !p.isSpectator() && !p.isCreative()).toList();

        if (players.isEmpty()) {
            lastGroupHealth = -1f;
            return;
        }

        if (lastGroupHealth <= 0) lastGroupHealth = players.get(0).getHealth();

        float min = Float.MAX_VALUE;
        float max = -1f;

        for (ServerPlayerEntity p : players) {
            float h = p.getHealth();
            if (h < min) min = h;
            if (h > max) max = h;
        }

        float target = (min < lastGroupHealth) ? min : (Math.max(max, lastGroupHealth));

        if (Math.abs(target - lastGroupHealth) > 0.01f) {
            for (ServerPlayerEntity p : players) p.setHealth(target);
            lastGroupHealth = target;
        }
    }

    public static void applyMaxHealth(ServerPlayerEntity player, float hearts) {
        EntityAttributeInstance attr = player.getAttributeInstance(EntityAttributes.MAX_HEALTH);
        if (attr != null) {
            attr.setBaseValue(hearts * 2.0);
            player.setHealth(player.getMaxHealth());
        }
    }

    public static void updateMaxHearts(MinecraftServer server, float hearts) {
        GSRMain.CONFIG.maxHearts = hearts;
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) applyMaxHealth(p, hearts);
        GSRMain.saveAndSync(server);
    }

    private static void spawnFirework(ServerPlayerEntity player) {
        if (player.getEntityWorld() instanceof ServerWorld world) {
            world.spawnEntity(new FireworkRocketEntity(world, player.getX(), player.getY() + 1.5, player.getZ(), new ItemStack(Items.FIREWORK_ROCKET)));
        }
    }

    public static void resetHealth() { lastGroupHealth = -1f; }
}