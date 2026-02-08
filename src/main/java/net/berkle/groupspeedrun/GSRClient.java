package net.berkle.groupspeedrun;

import net.berkle.groupspeedrun.config.GSRConfigPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

/**
 * Client-side entrypoint.
 * Receives the NBT payload and updates the global config instantly.
 */
public class GSRClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        // Register receiver for the native NBT sync packet
        ClientPlayNetworking.registerGlobalReceiver(GSRConfigPayload.ID, (payload, context) -> {
            // Unpacking binary NBT is much faster than parsing a JSON String
            var nbt = payload.nbt();

            /*
             * Update the config on the main thread to ensure the HUD
             * doesn't see a "half-updated" state during a frame render.
             */
            context.client().execute(() -> {
                if (GSRMain.CONFIG != null) {
                    GSRMain.CONFIG.readNbt(nbt);
                }
            });
        });
    }
}