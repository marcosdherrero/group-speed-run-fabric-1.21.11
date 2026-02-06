package net.berkle.groupspeedrun;

import net.berkle.groupspeedrun.config.GSRConfig;
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
import net.minecraft.world.GameMode;
import java.util.List;

/**
 * Main event handler for the Group Speedrun mod.
 * Manages run lifecycle, shared health, auto-starting, and victory celebrations.
 */
public class GSREvents {
    private static float lastGroupHealth = -1f;

    /**
     * Called every server tick. Handles core mod logic.
     */
    public static void onTick(MinecraftServer server) {
        var config = GSRMain.CONFIG;
        if (config == null) return;

        // 1. AUTO-START: Detects first movement/action to kick off the run timer
        if (config.startTime < 0 && !server.getPlayerManager().getPlayerList().isEmpty()) {
            handleAutoStart(server, config);
        }

        // 2. PAUSE & VICTORY TIME SNAPSHOTTING
        if (config.startTime >= 0) {
            // While the timer is manually frozen (but run not over), we track paused ticks
            // to subtract them from the total world time in getRunTicks()
            if (config.isTimerFrozen && !config.wasVictorious && !config.isFailed) {
                config.totalPausedTicks++;
            }
        }

        // 3. SHARED HEALTH: Syncs health across all active players
        if (config.sharedHealthEnabled && !config.isFailed && !config.wasVictorious) {
            handleSharedHealth(server);
        }

        // 4. PERIODIC SPLIT CHECKS: Scans for milestones every 0.5 seconds (10 ticks)
        if (server.getTicks() % 10 == 0 && config.startTime >= 0 && !config.isTimerFrozen) {
            GSRSplitManager.checkSplits(server);
        }

        // 5. VICTORY CELEBRATION: Spawns fireworks periodically after the dragon dies
        if (config.wasVictorious && config.victoryTimer > 0) {
            if (config.victoryTimer % 10 == 0) {
                for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) spawnFirework(p);
            }
            config.victoryTimer--;
        }
    }

    /**
     * Utility to modify a player's max heart count and heal them.
     */
    public static void applyMaxHealth(ServerPlayerEntity player, float hearts) {
        EntityAttributeInstance attr = player.getAttributeInstance(EntityAttributes.MAX_HEALTH);
        if (attr != null) {
            attr.setBaseValue(hearts * 2.0); // 1 Heart = 2.0 points
            player.setHealth(player.getMaxHealth());
        }
    }

    /**
     * Returns the current speedrun time in ticks.
     * Formula: (Current/End Time - Start Time) - Accumulated Paused Ticks
     */
    public static long getRunTicks(MinecraftServer server) {
        var config = GSRMain.CONFIG;
        if (config == null || config.startTime < 0) return 0;

        long endTime = (config.wasVictorious || config.isFailed)
                ? config.frozenTime
                : server.getOverworld().getTime();

        return Math.max(0, (endTime - config.startTime) - config.totalPausedTicks);
    }

    /**
     * Handles run failure when a player dies (if Group Death is enabled).
     */
    public static void handlePlayerDeath(ServerPlayerEntity deadPlayer, MinecraftServer server) {
        var config = GSRMain.CONFIG;
        if (config != null && config.groupDeathEnabled && !config.isFailed && !config.wasVictorious) {
            config.isFailed = true;
            config.isTimerFrozen = true;
            config.frozenTime = server.getOverworld().getTime();

            // Wake up the HUD to show the final "Fail" time
            config.lastSplitTime = config.frozenTime;

            server.getPlayerManager().broadcast(
                    Text.literal("§c§l[GSR] " + deadPlayer.getName().getString() + " died! Run Failed."),
                    false
            );

            for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
                p.changeGameMode(GameMode.SPECTATOR);
            }
            GSRMain.saveAndSync(server);
        }
    }

    /**
     * Monitors players for movement or interaction to start the timer.
     */
    private static void handleAutoStart(MinecraftServer server, GSRConfig config) {
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            // Check for movement (distance > 0), item usage, or arm swinging
            if (player.squaredDistanceTo(player.lastRenderX, player.lastRenderY, player.lastRenderZ) > 0.0001
                    || player.isUsingItem() || player.handSwinging) {

                long currentTime = server.getOverworld().getTime();

                // Setup Initial Run State
                config.startTime = currentTime;
                config.isTimerFrozen = false;
                config.totalPausedTicks = 0;
                config.frozenTime = 0;

                /**
                 * TRIGGER HUD POP-UP:
                 * Setting lastSplitTime triggers the 10-second fade-in logic
                 * in GSRTimerHudState, showing the timer as soon as movement starts.
                 */
                config.lastSplitTime = currentTime;

                // Sync and Notify
                GSRMain.saveAndSync(server);

                // Play Level-Up sound for all players to signal the start
                for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
                    p.playSound(SoundEvents.ENTITY_PLAYER_LEVELUP);
                }

                server.getPlayerManager().broadcast(Text.literal("§6§l[GSR] §rTimer Started!"), false);
                break;
            }
        }
    }

    /**
     * Logic for calculating and applying shared health across the group.
     */
    private static void handleSharedHealth(MinecraftServer server) {
        List<ServerPlayerEntity> players = server.getPlayerManager().getPlayerList().stream()
                .filter(p -> p.isAlive() && !p.isSpectator() && !p.isCreative()).toList();

        if (players.isEmpty()) { lastGroupHealth = -1f; return; }
        if (lastGroupHealth <= 0) lastGroupHealth = players.get(0).getHealth();

        float min = Float.MAX_VALUE;
        float max = -1f;
        for (ServerPlayerEntity p : players) {
            float h = p.getHealth();
            if (h < min) min = h;
            if (h > max) max = h;
        }

        // If someone took damage, drop everyone to that level. If someone healed, raise everyone.
        float target = (min < lastGroupHealth) ? min : (max > lastGroupHealth ? max : lastGroupHealth);

        if (Math.abs(target - lastGroupHealth) > 0.01f) {
            for (ServerPlayerEntity p : players) p.setHealth(target);
            lastGroupHealth = target;
        }
    }

    /**
     * Spawns a firework at the player's location.
     */
    private static void spawnFirework(ServerPlayerEntity player) {
        if (player.getEntityWorld() != null) {
            ServerWorld world = player.getEntityWorld();
            world.spawnEntity(new FireworkRocketEntity(world, player.getX(), player.getY() + 1.5, player.getZ(), new ItemStack(Items.FIREWORK_ROCKET)));
        }
    }

    public static void resetHealth() { lastGroupHealth = -1f; }
}