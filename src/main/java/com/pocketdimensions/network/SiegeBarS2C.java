package com.pocketdimensions.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.event.network.CustomPayloadEvent;

import java.util.UUID;

/**
 * A siege's state for the themed siege bar (client/siegebar): sent once a second to everyone in range, and once with
 * {@code remove} when a player leaves range or the siege ends. {@code rate} is how many ticks one tick of progress
 * takes: 0 while the siege block has no lapis, 1 normally, the core's slow factor while the World Core wards.
 */
public record SiegeBarS2C(UUID id, boolean remove, int kind, int progressTicks, int durationTicks, int rate,
                          int siegeFuel, int siegeCap, int coreFuel) {

    public static final int BREACHER = 0, BREAKER = 1;

    public static SiegeBarS2C removal(UUID id) { return new SiegeBarS2C(id, true, 0, 0, 0, 0, 0, 0, 0); }

    public static void encode(SiegeBarS2C m, FriendlyByteBuf buf) {
        buf.writeUUID(m.id); buf.writeBoolean(m.remove);
        if (m.remove) return;
        buf.writeVarInt(m.kind); buf.writeVarInt(m.progressTicks); buf.writeVarInt(m.durationTicks); buf.writeVarInt(m.rate);
        buf.writeVarInt(m.siegeFuel); buf.writeVarInt(m.siegeCap); buf.writeVarInt(m.coreFuel);
    }

    public static SiegeBarS2C decode(FriendlyByteBuf buf) {
        UUID id = buf.readUUID();
        if (buf.readBoolean()) return removal(id);
        return new SiegeBarS2C(id, false, buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
                buf.readVarInt(), buf.readVarInt(), buf.readVarInt());
    }

    public static void handle(SiegeBarS2C m, CustomPayloadEvent.Context ctx) {
        com.pocketdimensions.client.siegebar.SiegeBarClient.receive(m);
    }
}
