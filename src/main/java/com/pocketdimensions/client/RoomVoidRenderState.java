package com.pocketdimensions.client;

import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;

/** Render state for RoomVoidRenderer: the sets of cracks showing (0..3) and the room's seed for their shape. */
public class RoomVoidRenderState extends BlockEntityRenderState {
    public int cracks;
    public long seed;
}
