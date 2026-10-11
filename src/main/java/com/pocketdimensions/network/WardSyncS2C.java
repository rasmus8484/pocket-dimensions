package com.pocketdimensions.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.event.network.CustomPayloadEvent;

/**
 * The ward of the realm the player is in, for the sigil overhead: the Core's position, the plot's bounds, the siege
 * state (WorldCoreBlockEntity.STATE_*), whether a breach stands open and whether the Core holds lapis. Sent by
 * event/WardSync when any of it changes; `present` false when the player is in no realm.
 */
public record WardSyncS2C(boolean present, int coreX, int coreY, int coreZ, int minX, int minZ, int maxX, int maxZ,
                          int siege, boolean breachOpen, boolean warded) {

    public static WardSyncS2C absent() { return new WardSyncS2C(false, 0, 0, 0, 0, 0, 0, 0, 0, false, false); }

    public static void encode(WardSyncS2C m, FriendlyByteBuf buf) {
        buf.writeBoolean(m.present);
        if (!m.present) return;
        buf.writeVarInt(m.coreX); buf.writeVarInt(m.coreY); buf.writeVarInt(m.coreZ);
        buf.writeVarInt(m.minX); buf.writeVarInt(m.minZ); buf.writeVarInt(m.maxX); buf.writeVarInt(m.maxZ);
        buf.writeVarInt(m.siege); buf.writeBoolean(m.breachOpen); buf.writeBoolean(m.warded);
    }

    public static WardSyncS2C decode(FriendlyByteBuf buf) {
        if (!buf.readBoolean()) return absent();
        return new WardSyncS2C(true, buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
                buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readBoolean(), buf.readBoolean());
    }

    public static void handle(WardSyncS2C m, CustomPayloadEvent.Context ctx) {
        com.pocketdimensions.client.ward.WardView.receive(m);
    }
}
