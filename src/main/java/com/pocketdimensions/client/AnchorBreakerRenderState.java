package com.pocketdimensions.client;

import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;

/** Render state for AnchorBreakerBlockEntityRenderer - extracted on the game thread, consumed on the render thread. */
public class AnchorBreakerRenderState extends BlockEntityRenderState {
    /** Lightning sets frozen around the black hole (0..3, one per passed quarter). */
    public int boltSets;
    /** True while the breaker is fueled and siphoning: draws the red stream into the funnel. */
    public boolean stream;
    /** Per-position seed so each breaker's lightning has its own (static) shape. */
    public long seed;
}
