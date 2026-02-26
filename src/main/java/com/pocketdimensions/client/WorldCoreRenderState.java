package com.pocketdimensions.client;

import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;

/** Render state for WorldCoreBlockEntityRenderer — extracted on the game thread, consumed on the render thread. */
public class WorldCoreRenderState extends BlockEntityRenderState {
    /** Height of the beam in blocks (0 = beam is blocked, don't render). */
    public int beamHeight;
    /** Game time + partial tick, used to animate the beam rotation. */
    public float animationTime;
}
