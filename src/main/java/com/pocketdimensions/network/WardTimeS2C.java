package com.pocketdimensions.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.event.network.CustomPayloadEvent;

/** /pd test wardTime: asks the player's client to measure the ward sigil for `seconds` and report in its chat. */
public record WardTimeS2C(float seconds) {

    public static void encode(WardTimeS2C m, FriendlyByteBuf buf) { buf.writeFloat(m.seconds); }

    public static WardTimeS2C decode(FriendlyByteBuf buf) { return new WardTimeS2C(buf.readFloat()); }

    public static void handle(WardTimeS2C m, CustomPayloadEvent.Context ctx) {
        com.pocketdimensions.client.ward.WardSigilRenderer.measure(m.seconds());
    }
}
