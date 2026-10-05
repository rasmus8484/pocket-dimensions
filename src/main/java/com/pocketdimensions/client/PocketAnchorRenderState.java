package com.pocketdimensions.client;

import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.world.level.block.state.BlockState;

/** Extracted on the game thread for PocketAnchorRenderer (the Tumbling Cube). */
public class PocketAnchorRenderState extends BlockEntityRenderState {
    /** Someone online is in the room: brighter bands. */
    public boolean occupied;
    /** Seconds, continuous, offset per anchor so neighbours don't move in step. */
    public float time;
    /** Tumble phase in seconds; runs half again as fast while occupied (accumulated, so it never jumps). */
    public float phase;
    /** The never-placed cube=true state whose model is the cube. */
    public BlockState cube;
}
