package net.berkle.groupspeedrun.config;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * A native NBT-based payload.
 * Efficient because it avoids String allocation and uses Minecraft's internal binary format.
 */
public record GSRConfigPayload(NbtCompound nbt) implements CustomPayload {

    public static final Id<GSRConfigPayload> ID = new Id<>(Identifier.of("groupspeedrun", "sync"));

    /**
     * PacketCodecs.NBT_COMPOUND is highly optimized for 1.21 networking.
     */
    public static final PacketCodec<PacketByteBuf, GSRConfigPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.NBT_COMPOUND, GSRConfigPayload::nbt,
            GSRConfigPayload::new
    );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}