package net.berkle.groupspeedrun.config;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * A custom networking payload used to sync the GSRConfig from the server to the client.
 * In Minecraft 1.21, CustomPayload records are the standard way to send data over the network.
 * * @param json The serialized GSRConfig object in JSON format.
 */
public record GSRConfigPayload(String json) implements CustomPayload {

    /**
     * Unique identifier for the sync packet: "groupspeedrun:sync".
     * This ID is used by both the server to send and the client to receive the packet.
     */
    public static final Id<GSRConfigPayload> ID = new Id<>(Identifier.of("groupspeedrun", "sync"));

    /**
     * The Codec defines how the network buffer reads and writes this record.
     * We use a tuple codec to map the 'json' String field to the network.
     * * NOTE: We use PacketCodecs.string(Short.MAX_VALUE) to ensure that if the config
     * grows (lots of split data/stats), it doesn't hit the default small packet limit.
     */
    public static final PacketCodec<RegistryByteBuf, GSRConfigPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.string(32767), GSRConfigPayload::json, // Encoder (Getter)
            GSRConfigPayload::new                               // Decoder (Constructor)
    );

    /**
     * Returns the unique ID for this payload type.
     * Required by the CustomPayload interface for the Fabric Networking API.
     */
    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}