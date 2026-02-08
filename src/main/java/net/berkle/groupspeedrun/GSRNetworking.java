package net.berkle.groupspeedrun;

import net.berkle.groupspeedrun.config.GSRConfigPayload;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * Optimized networking manager.
 * Uses native NBT conversion to reduce CPU spikes during synchronization.
 */
public class GSRNetworking {

    /**
     * Broadcasts state to all players.
     * Snapshot is created once to save CPU cycles.
     */
    public static void syncConfigWithAll(MinecraftServer server) {
        if (server == null || GSRMain.CONFIG == null) return;

        // Create the NBT snapshot once for the entire broadcast
        NbtCompound nbt = new NbtCompound();
        GSRMain.CONFIG.writeNbt(nbt); // We use a native writer method
        GSRConfigPayload payload = new GSRConfigPayload(nbt);

        for (ServerPlayerEntity player : PlayerLookup.all(server)) {
            ServerPlayNetworking.send(player, payload);
        }
    }

    public static void syncConfigWithPlayer(ServerPlayerEntity player) {
        if (player == null || GSRMain.CONFIG == null) return;

        NbtCompound nbt = new NbtCompound();
        GSRMain.CONFIG.writeNbt(nbt);
        ServerPlayNetworking.send(player, new GSRConfigPayload(nbt));
    }

    /**
     * Sends a specific scale update to a single player.
     */
    public static void sendScaleUpdate(ServerPlayerEntity player, float scale) {
        NbtCompound nbt = new NbtCompound();
        nbt.putFloat("timerScale", scale);
        nbt.putFloat("locateScale", scale);

        // We send this to the specific player, not the whole server
        ServerPlayNetworking.send(player, new GSRConfigPayload(nbt));
    }
}