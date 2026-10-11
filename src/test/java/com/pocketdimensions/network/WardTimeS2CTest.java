package com.pocketdimensions.network;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** The measuring request crosses the network unchanged. */
class WardTimeS2CTest {

    @Test
    void roundTrip() {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        WardTimeS2C.encode(new WardTimeS2C(12.5f), buf);
        assertEquals(new WardTimeS2C(12.5f), WardTimeS2C.decode(buf));
    }
}
