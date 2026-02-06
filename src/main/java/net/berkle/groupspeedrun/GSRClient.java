package net.berkle.groupspeedrun;

import com.google.gson.Gson;
import net.berkle.groupspeedrun.config.GSRConfig;
import net.berkle.groupspeedrun.config.GSRConfigPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

/**
 * Client-side entrypoint for GroupSpeedrun.
 * Handles receiving synchronization packets from the server to update the local HUD.
 */
public class GSRClient implements ClientModInitializer {

    // Reusable GSON instance for decoding the incoming JSON configuration
    private static final Gson GSON = new Gson();

    @Override
    public void onInitializeClient() {
        // Register the global receiver for the 'groupspeedrun:sync' payload
        ClientPlayNetworking.registerGlobalReceiver(GSRConfigPayload.ID, (payload, context) -> {
            // Extract the JSON string from the 1.21 CustomPayload record
            String json = payload.json();

            /*
             * Networking packets arrive on a background thread.
             * We must use context.client().execute() to schedule the config update
             * on the main Minecraft client thread. This prevents race conditions
             * when the HUD tries to render while the config is being updated.
             */
            context.client().execute(() -> {
                try {
                    // Update the global CONFIG reference with the data from the server
                    GSRMain.CONFIG = GSON.fromJson(json, GSRConfig.class);
                } catch (Exception e) {
                    GSRMain.LOGGER.error("[GSR-Client] Failed to parse sync packet!", e);
                }
            });
        });
    }
}