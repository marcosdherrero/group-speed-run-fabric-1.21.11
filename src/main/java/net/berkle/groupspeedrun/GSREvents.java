package net.berkle.groupspeedrun;

import net.berkle.groupspeedrun.config.GSRConfigWorld;
import net.berkle.groupspeedrun.managers.GSRRunHistoryManager;
import net.berkle.groupspeedrun.managers.GSRSplitManager;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Main event handler for the Group Speedrun mod.
 */
public class GSREvents {
    // FIX: Added the missing LOGGER variable
    private static final Logger LOGGER = LoggerFactory.getLogger("GSR-Events");

    private static float lastGroupHealth = -1f;

    public static void onTick(MinecraftServer server) {
        var config = GSRMain.CONFIG;
        if (config == null) return;

        // 1. AUTO-START DETECTION
        if (config.startTime == -1 && !server.getPlayerManager().getPlayerList().isEmpty()) {
            handleAutoStart(server, config);
        }

        // 2. PAUSE STATE PROTECTION
        if (config.isTimerFrozen) {
            handlePauseMaintenance(server);
            return;
        }

        // 3. SHARED HEALTH ENGINE
        if (config.sharedHealthEnabled && !config.isFailed && !config.isVictorious) {
            handleSharedHealth(server);
        }

        // 4. PERIODIC SPLIT CHECKS
        if (server.getTicks() % 10 == 0 && config.startTime > 0) {
            GSRSplitManager.checkSplits(server);
        }

        // 5. VICTORY CELEBRATIONS
        if (config.isVictorious && config.victoryTimer > 0) {
            if (config.victoryTimer % 10 == 0) {
                for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) spawnFirework(p);
            }
            config.victoryTimer--;
        }
    }

    public static long getRunTicks(MinecraftServer server) {
        var config = GSRMain.CONFIG;
        if (config == null || config.startTime < 0) return 0;
        return config.getElapsedTime() / 50;
    }

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

    public static void resumeRun(MinecraftServer server) {
        var config = GSRMain.CONFIG;
        if (config != null && config.isTimerFrozen) {
            config.isTimerFrozen = false;
            config.startTime = System.currentTimeMillis() - config.frozenTime;
            config.lastSplitTime = server.getOverworld().getTime();

            server.getPlayerManager().broadcast(Text.literal("§6[GSR] §aTimer Resumed!"), false);
            for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
                p.playSound(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
            }
            GSRMain.saveAndSync(server);
        }
    }

    private static void handlePauseMaintenance(MinecraftServer server) {
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            if (player.isSpectator() || player.isCreative()) continue;
            player.getHungerManager().setFoodLevel(20);
            if (lastGroupHealth > 0) player.setHealth(lastGroupHealth);
        }
    }

    private static void handleAutoStart(MinecraftServer server, GSRConfigWorld config) {
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            // Check if player moved or acted
            if (player.squaredDistanceTo(player.lastRenderX, player.lastRenderY, player.lastRenderZ) > 0.0001
                    || player.isUsingItem() || player.handSwinging) {

                config.startTime = System.currentTimeMillis();
                config.isTimerFrozen = false;
                config.frozenTime = 0;
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

    public static void handlePlayerDeath(ServerPlayerEntity deadPlayer, MinecraftServer server) {
        var config = GSRMain.CONFIG;
        if (config == null || config.startTime <= 0) return;

        // 1. Check exclusion list
        if (config.excludedPlayers.contains(deadPlayer.getUuid())) {
            LOGGER.info("GSR: {} died but is excluded from group death.", deadPlayer.getName().getString());
            return;
        }

        // 2. Trigger Group Death
        if (config.groupDeathEnabled && !config.isFailed && !config.isVictorious) {
            config.isFailed = true;
            config.isTimerFrozen = true;
            config.frozenTime = System.currentTimeMillis() - config.startTime;
            config.lastSplitTime = server.getOverworld().getTime();

            // CAPTURE: The specific death message for this player
            Text deathMsg = deadPlayer.getDamageTracker().getDeathMessage();
            String deathString = deathMsg.getString();

            // LOG: Save history with the actual player name and cause
            GSRRunHistoryManager.saveRun(server, "FAILURE", deadPlayer.getName().getString(), deathString);

            // BROADCAST: Send the specific death message to everyone in red
            server.getPlayerManager().broadcast(
                    Text.literal("§6[GSR] §cGroup Failed! ").append(deathMsg),
                    false
            );

            // SYNC: Put everyone in spectator and play thunder sound
            for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
                p.changeGameMode(GameMode.SPECTATOR);
                p.playSound(SoundEvents.ENTITY_LIGHTNING_BOLT_THUNDER, 1.0f, 1.0f);
            }

            GSRMain.saveAndSync(server);
        }
    }

    private static void handleSharedHealth(MinecraftServer server) {
        var config = GSRMain.CONFIG;
        List<ServerPlayerEntity> participants = server.getPlayerManager().getPlayerList().stream()
                .filter(p -> p.isAlive() && !p.isSpectator() && !p.isCreative())
                .filter(p -> !config.excludedPlayers.contains(p.getUuid()))
                .toList();

        if (participants.isEmpty()) {
            lastGroupHealth = -1f;
            return;
        }

        if (lastGroupHealth <= 0) lastGroupHealth = participants.get(0).getHealth();

        float min = Float.MAX_VALUE;
        ServerPlayerEntity damagedPlayer = null;

        for (ServerPlayerEntity p : participants) {
            if (p.getHealth() < min) {
                min = p.getHealth();
                damagedPlayer = p;
            }
        }

        if (min < lastGroupHealth - 0.01f) {
            float heartsLost = (lastGroupHealth - min) / 2.0f;
            String name = (damagedPlayer != null) ? damagedPlayer.getName().getString() : "Someone";
            Text msg = Text.literal("§6[GSR] §f" + name + " §ctook " + String.format("%.1f", heartsLost) + " damage!");

            for (ServerPlayerEntity p : participants) {
                p.setHealth(min);
                p.playSound(SoundEvents.ENTITY_PLAYER_HURT, 1.0f, 1.0f);
                p.sendMessage(msg, true);
            }
            lastGroupHealth = min;
        } else {
            float max = -1f;
            for (ServerPlayerEntity p : participants) if (p.getHealth() > max) max = p.getHealth();

            if (max > lastGroupHealth + 0.01f) {
                for (ServerPlayerEntity p : participants) p.setHealth(max);
                lastGroupHealth = max;
            }
        }
    }

    public static void applyMaxHealth(ServerPlayerEntity player, float hearts) {
        // Updated to use RegistryEntry logic for EntityAttributes in 1.21
        EntityAttributeInstance attr = player.getAttributeInstance(EntityAttributes.MAX_HEALTH);
        if (attr != null) {
            attr.setBaseValue(hearts * 2.0);
            player.setHealth(player.getMaxHealth());
        }
    }

    public static void updateMaxHearts(MinecraftServer server, float hearts) {
        if (GSRMain.CONFIG != null) {
            GSRMain.CONFIG.maxHearts = hearts;
            for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
                applyMaxHealth(p, hearts);
            }
            GSRMain.saveAndSync(server);
        }
    }

    private static void spawnFirework(ServerPlayerEntity player) {
        if (player.getEntityWorld() instanceof ServerWorld world) {
            world.spawnEntity(new FireworkRocketEntity(world, player.getX(), player.getY() + 1.5, player.getZ(), new ItemStack(Items.FIREWORK_ROCKET)));
        }
    }

    public static void resetHealth() { lastGroupHealth = -1f; }
}