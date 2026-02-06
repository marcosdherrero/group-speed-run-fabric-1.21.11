package net.berkle.groupspeedrun;

import net.berkle.groupspeedrun.config.GSRConfig;
import net.berkle.groupspeedrun.config.GSRConfigPayload;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.WorldSavePath;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.File;

/**
 * Main Entrypoint for GroupSpeedrun.
 * Handles configuration persistence, statistics management, and server event hooks.
 */
public class GSRMain implements ModInitializer {
	public static final String MOD_ID = "groupspeedrun";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	// Global configuration instance accessible across the mod
	public static GSRConfig CONFIG = new GSRConfig();
	private static File worldDir;

	@Override
	public void onInitialize() {
		LOGGER.info("[GSR] Initializing GroupSpeedrun Logic...");

		// 1. Networking Registration (Minecraft 1.21 Standard)
		PayloadTypeRegistry.playS2C().register(GSRConfigPayload.ID, GSRConfigPayload.CODEC);

		// 2. Command Registration
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
			GSRCommands.register(dispatcher);
		});

		// 3. Connection Events: Sync data when a player joins
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			GSRNetworking.syncConfigWithPlayer(handler.getPlayer());
		});

		// 4. Server Lifecycle Hooks
		ServerLifecycleEvents.SERVER_STARTING.register(server -> {
			worldDir = server.getSavePath(WorldSavePath.ROOT).toFile();

			// A. PREFERENCE BRIDGE: Capture current session rules before overwriting
			int currentHud = CONFIG.hudMode;
			boolean currentDeath = CONFIG.groupDeathEnabled;
			boolean currentShared = CONFIG.sharedHealthEnabled;
			float currentMaxHp = CONFIG.maxHearts;

			// B. LOAD CONFIG: Load settings from the world folder
			GSRConfig loaded = GSRConfig.load(worldDir);

			// C. NEW WORLD LOGIC: If fresh run, apply preferences & reset stats
			if (loaded.startTime == -1) {
				loaded.hudMode = currentHud;
				loaded.groupDeathEnabled = currentDeath;
				loaded.sharedHealthEnabled = currentShared;
				loaded.maxHearts = currentMaxHp;

				GSRStats.reset(); // Clear old stats for a brand new world
				LOGGER.info("[GSR] Fresh world detected. Resetting statistics and bridging preferences.");
			} else {
				// D. RESUME LOGIC: If run in progress, load existing stats from disk
				GSRStats.load(server);
				LOGGER.info("[GSR] Resuming run. Statistics reloaded.");
			}

			CONFIG = loaded;
			GSREvents.resetHealth(); // Reset the shared health baseline for the new world
			LOGGER.info("[GSR] Environment ready for world: {}", server.getSaveProperties().getLevelName());
		});

		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			LOGGER.info("[GSR] Server stopping. Finalizing save...");
			saveAndSync(server);
			worldDir = null; // Clear path reference for the next load
		});

		// 5. Tick Events
		ServerTickEvents.START_SERVER_TICK.register(this::onServerTick);
	}

	/**
	 * Handles logic that needs to run every server tick (20 times per second).
	 */
	private void onServerTick(MinecraftServer server) {
		// Only run logic if a world is actually loaded
		if (worldDir == null) return;

		// Execute core run logic (Auto-start, Shared Health, Split checks)
		GSREvents.onTick(server);

		// Periodic autosave every 5 seconds (100 ticks)
		// This saves both the Run Config (timers/splits) and Player Stats (awards)
		if (server.getTicks() % 100 == 0) {
			saveAndSync(server);
		}
	}

	/**
	 * Persists all data to disk and synchronizes the configuration with all clients.
	 */
	public static void saveAndSync(MinecraftServer server) {
		if (CONFIG != null && worldDir != null) {
			// 1. Save Run Configuration (splits, timer state, etc.)
			CONFIG.save(worldDir);

			// 2. Save Statistics (dragon damage, inventory opens, etc.)
			GSRStats.save(server);

			// 3. Update all client-side HUDs via packet
			GSRNetworking.syncConfigWithAll(server);
		}
	}
}