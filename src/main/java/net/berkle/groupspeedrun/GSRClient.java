package net.berkle.groupspeedrun;

import net.berkle.groupspeedrun.config.GSRConfigPayload;
import net.berkle.groupspeedrun.config.GSRConfigPlayer;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

/**
 * Client-side entrypoint.
 * Receives the NBT payload and updates the global config instantly.
 */
public class GSRClient implements ClientModInitializer {

    // This instance will store the local player's UI preferences
    public static final GSRConfigPlayer PLAYER_CONFIG = new GSRConfigPlayer();

    @Override
    public void onInitializeClient() {
        ClientPlayNetworking.registerGlobalReceiver(GSRConfigPayload.ID, (payload, context) -> {
            var nbt = payload.nbt();

            context.client().execute(() -> {
                // 1. Sync World Data (Timer, Structures, Run State)
                // We check for a world-specific key like "startTime"
                if (nbt.contains("startTime") && GSRMain.CONFIG != null) {
                    GSRMain.CONFIG.readNbt(nbt);
                }

                // 2. Sync Player Data (Scale, HUD Mode, Positions)
                // These are the values you fixed in the Player Config
                if (nbt.contains("timerScale") || nbt.contains("hudMode")) {
                    PLAYER_CONFIG.readNbt(nbt);
                }
            });
        });
    }
}