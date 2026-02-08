package net.berkle.groupspeedrun;

import net.berkle.groupspeedrun.config.GSRConfigWorld;
import net.berkle.groupspeedrun.config.GSRConfigPayload;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Common entrypoint for GroupSpeedrun.
 * Handles the persistence of run states within the specific world save folder.
 */
public class GSRMain implements ModInitializer {
	public static final String MOD_ID = "groupspeedrun";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	// The central source of truth for the current run
	public static GSRConfigWorld CONFIG = new GSRConfigWorld();
	private static boolean isServerActive = false;

	@Override
	public void onInitialize() {
		LOGGER.info("[GSR] Initializing GroupSpeedrun Logic...");

		// 1. NETWORKING REGISTRATION
		PayloadTypeRegistry.playS2C().register(GSRConfigPayload.ID, GSRConfigPayload.CODEC);

		// 2. COMMAND REGISTRATION
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
			GSRCommands.register(dispatcher);
		});

		// 3. CLIENT SYNC ON JOIN
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			GSRNetworking.syncConfigWithPlayer(handler.getPlayer());
		});

		// 4. SERVER STARTUP LOGIC (Part A: Load Config)
		ServerLifecycleEvents.SERVER_STARTING.register(server -> {
			// Load data from disk
			CONFIG = GSRConfigWorld.load(server);
			GSRStats.load(server);

			// Resume Time Calculation (Safe, doesn't use world object)
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

		// 5. SERVER STARTED LOGIC (Part B: HUD & World Interaction)
		// This fires AFTER the overworld is loaded, preventing the NullPointerException
		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			if (CONFIG.startTime != -1 && !CONFIG.isTimerFrozen && !CONFIG.isVictorious && !CONFIG.isFailed) {
				// Now it is safe to call getOverworld()
				if (server.getOverworld() != null) {
					CONFIG.lastSplitTime = server.getOverworld().getTime();
				}
			}
			LOGGER.info("[GSR] Server fully loaded. World access safe.");
		});

		// 6. SERVER SHUTDOWN LOGIC
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			if (CONFIG.startTime != -1 && !CONFIG.isTimerFrozen) {
				CONFIG.frozenTime = System.currentTimeMillis() - CONFIG.startTime;
			}

			saveAndSync(server);
			isServerActive = false;
			LOGGER.info("[GSR] State persisted. Server stopping.");
		});

		// 7. CORE TICK LOOP
		ServerTickEvents.START_SERVER_TICK.register(this::onServerTick);
	}

	/**
	 * Called every tick (~50ms) to update game logic and periodic saving.
	 */
	private void onServerTick(MinecraftServer server) {
		if (!isServerActive || server == null) return;

		GSREvents.onTick(server);

		// Periodic Save (Every 5 seconds / 100 ticks)
		if (server.getTicks() % 100 == 0) {
			if (CONFIG.startTime != -1 && !CONFIG.isTimerFrozen) {
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