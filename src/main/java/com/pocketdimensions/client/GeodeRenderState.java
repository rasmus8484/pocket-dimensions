package com.pocketdimensions.client;

import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;

/** Extracted on the game thread for WorldCoreBlockEntityRenderer (the Geode Heart). */
public class GeodeRenderState extends BlockEntityRenderState {
    /** Siege state (WorldCoreBlock.SIEGE): 0 normal, 1 breaching, 2 breaking, 3 anchor lost (inert). */
    public int siege;
    /** Seconds, continuous (game time + partial tick) / 20. */
    public float time;
    /** Beam length in whole blocks above the black hole (0 = blocked). */
    public int beamHeight;
    /** Game time + partial tick for the beacon beam's own animation. */
    public float beamTime;
}
