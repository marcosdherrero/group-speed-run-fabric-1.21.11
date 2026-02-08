package net.berkle.groupspeedrun;

import net.berkle.groupspeedrun.config.GSRConfigWorld;
import net.berkle.groupspeedrun.config.GSRConfigPlayer; // Added
import net.berkle.groupspeedrun.config.GSRConfigPayload;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity; // Added
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap; // Added
import java.util.Map;     // Added
import java.util.UUID;    // Added

public class GSRMain implements ModInitializer {
	public static final String MOD_ID = "groupspeedrun";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	public static GSRConfigWorld CONFIG = new GSRConfigWorld();

	// --- [ NEW: Player Config Storage ] ---
	private static final Map<UUID, GSRConfigPlayer> PLAYER_CONFIGS = new HashMap<>();

	private static boolean isServerActive = false;

	@Override
	public void onInitialize() {
		LOGGER.info("[GSR] Initializing GroupSpeedrun Logic...");

		PayloadTypeRegistry.playS2C().register(GSRConfigPayload.ID, GSRConfigPayload.CODEC);

		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
			GSRCommands.register(dispatcher);
		});

		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			GSRNetworking.syncConfigWithPlayer(handler.getPlayer());
		});

		// Clean up player configs when they leave to prevent memory leaks
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			PLAYER_CONFIGS.remove(handler.getPlayer().getUuid());
		});

		ServerLifecycleEvents.SERVER_STARTING.register(server -> {
			CONFIG = GSRConfigWorld.load(server);
			GSRStats.load(server);

			if (CONFIG.startTime != -1) {
				if (!CONFIG.isTimerFrozen && !CONFIG.isVictorious && !CONFIG.isFailed) {
					CONFIG.startTime = System.currentTimeMillis() - CONFIG.frozenTime;
					LOGGER.info("[GSR] Active run detected: Resuming timer.");
				}
			} else {
				GSRStats.reset();
			}

			GSREvents.resetHealth();
			isServerActive = true;
		});

		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			if (CONFIG.startTime != -1 && !CONFIG.isTimerFrozen && !CONFIG.isVictorious && !CONFIG.isFailed) {
				if (server.getOverworld() != null) {
					CONFIG.lastSplitTime = server.getOverworld().getTime();
				}
			}
			LOGGER.info("[GSR] Server fully loaded. World access safe.");
		});

		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			if (CONFIG.startTime != -1 && !CONFIG.isTimerFrozen && !CONFIG.isFailed && !CONFIG.isVictorious) {
				CONFIG.frozenTime = System.currentTimeMillis() - CONFIG.startTime;
			}

			saveAndSync(server);
			isServerActive = false;
			LOGGER.info("[GSR] State persisted. Server stopping.");
		});

		ServerTickEvents.START_SERVER_TICK.register(this::onServerTick);
	}

	/**
	 * Helper to retrieve or create a player's HUD configuration.
	 * This fixes the "cannot find symbol" error in GSRCommands.
	 */
	public static GSRConfigPlayer getPlayerConfig(ServerPlayerEntity player) {
		return PLAYER_CONFIGS.computeIfAbsent(player.getUuid(), uuid -> new GSRConfigPlayer());
	}

	private void onServerTick(MinecraftServer server) {
		if (!isServerActive || server == null) return;

		GSREvents.onTick(server);

		if (server.getTicks() % 100 == 0) {
			if (CONFIG.startTime != -1 && !CONFIG.isTimerFrozen && !CONFIG.isFailed && !CONFIG.isVictorious) {
				CONFIG.frozenTime = System.currentTimeMillis() - CONFIG.startTime;
			}
			saveAndSync(server);
		}
	}

	public static void saveAndSync(MinecraftServer server) {
		if (CONFIG != null && server != null) {
			CONFIG.save(server);
			GSRStats.save(server);
			GSRNetworking.syncConfigWithAll(server);
		}
	}
}