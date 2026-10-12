package com.pocketkingdoms.network;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** The ward's state crosses the network unchanged. */
class WardSyncS2CTest {

    @Test
    void roundTrip() {
        WardSyncS2C m = new WardSyncS2C(true, 392, 71, -392, 0, -784, 784, 0, 2, true, false);
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        WardSyncS2C.encode(m, buf);
        assertEquals(m, WardSyncS2C.decode(buf));
    }

    @Test
    void absentRoundTrip() {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        WardSyncS2C.encode(WardSyncS2C.absent(), buf);
        assertFalse(WardSyncS2C.decode(buf).present());
    }
}
