package com.pocketdimensions.client;

import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;

/** Extracted on the game thread for WorldAnchorBlockEntityRenderer. */
public class WorldAnchorRenderState extends BlockEntityRenderState {
    public boolean linked;
    /** Seconds, continuous (game time + partial tick) / 20. */
    public float time;
}
