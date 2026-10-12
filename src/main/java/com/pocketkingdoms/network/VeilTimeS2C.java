package com.pocketkingdoms.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.event.network.CustomPayloadEvent;

/** /pk test veilTime: asks the player's client to time the Veil fog on the GPU for `seconds` and report in its chat. */
public record VeilTimeS2C(float seconds) {

    public static void encode(VeilTimeS2C m, FriendlyByteBuf buf) { buf.writeFloat(m.seconds); }

    public static VeilTimeS2C decode(FriendlyByteBuf buf) { return new VeilTimeS2C(buf.readFloat()); }

    public static void handle(VeilTimeS2C m, CustomPayloadEvent.Context ctx) {
        com.pocketkingdoms.client.veil.VeilFogRenderer.measure(m.seconds());
    }
}
