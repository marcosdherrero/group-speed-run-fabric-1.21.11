package net.berkle.groupspeedrun;

import com.google.gson.Gson;
import net.berkle.groupspeedrun.config.GSRConfigPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * Handles the distribution of configuration data from the server to clients.
 * Uses JSON serialization to ensure all speedrun state (timer, health, locators)
 * is synchronized across the entire group.
 */
public class GSRNetworking {
    private static final Gson GSON = new Gson();

    /**
     * Broadcasts the current server configuration to every player currently online.
     * Best used for global state changes like timer starts, resets, or structure discoveries.
     * * @param server The MinecraftServer instance.
     */
    public static void syncConfigWithAll(MinecraftServer server) {
        if (server == null || GSRMain.CONFIG == null) return;

        // Convert the current state to a JSON string for the payload
        String json = GSON.toJson(GSRMain.CONFIG);
        GSRConfigPayload payload = new GSRConfigPayload(json);

        // Iterate and send to all connected clients
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            ServerPlayNetworking.send(player, payload);
        }
    }

    /**
     * Sends the current configuration to a specific player.
     * Crucial for syncing players as they log in so their HUD is not out of date.
     * * @param player The specific ServerPlayerEntity to sync.
     */
    public static void syncConfigWithPlayer(ServerPlayerEntity player) {
        if (player == null || GSRMain.CONFIG == null) return;

        String json = GSON.toJson(GSRMain.CONFIG);
        GSRConfigPayload payload = new GSRConfigPayload(json);

        ServerPlayNetworking.send(player, payload);
    }
}