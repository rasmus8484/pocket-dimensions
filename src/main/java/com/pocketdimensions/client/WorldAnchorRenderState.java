package com.pocketdimensions.client;

import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;

/** Extracted on the game thread for WorldAnchorBlockEntityRenderer. */
public class WorldAnchorRenderState extends BlockEntityRenderState {
    public boolean linked;
    /** Seconds, continuous (game time + partial tick) / 20. */
    public float time;
    /**
     * Ring look. GLOW = calm cyan (default). EMBER = flickering red-yellow accretion glow, reserved for the
     * siege visuals (see PRD SG-011): set it from the block entity's siege state in extractRenderState.
     */
    public BlackHoleRenderer.RingPalette palette = BlackHoleRenderer.RingPalette.GLOW;
}
