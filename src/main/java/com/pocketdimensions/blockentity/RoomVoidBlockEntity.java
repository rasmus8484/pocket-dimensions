package com.pocketdimensions.blockentity;

import com.pocketdimensions.init.ModBlockEntityTypes;
import com.pocketdimensions.manager.RoomShell;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * Sits in one floor corner of a pocket room (the shell's HEART) so the room has a renderer: RoomVoidRenderer draws the
 * end-portal void over every wall, floor and ceiling from here. Holds no data.
 */
public class RoomVoidBlockEntity extends BlockEntity {

    public RoomVoidBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntityTypes.ROOM_VOID.get(), pos, state);
    }

    /** The shell's corner (local 0, 0, 0) in world space. */
    public BlockPos shellOrigin() {
        return worldPosition.offset(-RoomShell.HEART_X, -RoomShell.HEART_Y, -RoomShell.HEART_Z);
    }

    /** The whole room, so it is drawn whenever any of it is in view. */
    @Override
    public AABB getRenderBoundingBox() {
        BlockPos o = shellOrigin();
        return new AABB(o.getX(), o.getY(), o.getZ(), o.getX() + RoomShell.SIZE, o.getY() + RoomShell.SIZE, o.getZ() + RoomShell.SIZE);
    }
}
