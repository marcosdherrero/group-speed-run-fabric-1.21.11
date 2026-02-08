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

public class GSRMain implements ModInitializer {
	public static final String MOD_ID = "groupspeedrun";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	public static GSRConfigWorld CONFIG = new GSRConfigWorld();
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

		ServerLifecycleEvents.SERVER_STARTING.register(server -> {
			CONFIG = GSRConfigWorld.load(server);
			GSRStats.load(server);

			if (CONFIG.startTime != -1) {
				// GATE: Only resume if the run was actually in progress
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

		// 6. SERVER SHUTDOWN LOGIC
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			// FIX: Only update the 'frozen' snapshot if the run hasn't finished/failed yet
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
	 * Called every tick (~50ms).
	 */
	private void onServerTick(MinecraftServer server) {
		if (!isServerActive || server == null) return;

		GSREvents.onTick(server);

		// Periodic Save (Every 5 seconds / 100 ticks)
		if (server.getTicks() % 100 == 0) {
			// FIX: Ensure the timer snapshot stops moving when the run ends
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