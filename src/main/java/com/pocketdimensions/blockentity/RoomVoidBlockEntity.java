package com.pocketdimensions.blockentity;

import com.pocketdimensions.init.ModBlockEntityTypes;
import com.pocketdimensions.manager.RoomShell;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

/**
 * Sits in one floor corner of a pocket room (the shell's HEART) so the room has a renderer: RoomVoidRenderer draws the
 * end-portal void over every wall, floor and ceiling from here, and the cracks while the room's anchor is being mined.
 * The crack count (0..3) is sent to clients but never saved: when the mining stops, or the server restarts, it is 0.
 */
public class RoomVoidBlockEntity extends BlockEntity {

    private int cracks;

    public RoomVoidBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntityTypes.ROOM_VOID.get(), pos, state);
    }

    /** The shell's corner (local 0, 0, 0) in world space. */
    public BlockPos shellOrigin() {
        return worldPosition.offset(-RoomShell.HEART_X, -RoomShell.HEART_Y, -RoomShell.HEART_Z);
    }

    public int cracks() { return cracks; }

    /** Server side: show this many sets of cracks to everyone in the room. */
    public void setCracks(int cracks) {
        if (this.cracks == cracks || level == null) return;
        this.cracks = cracks;
        level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("cracks", cracks);
        return tag;
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        cracks = input.getIntOr("cracks", 0);
    }

    /** The whole room, so it is drawn whenever any of it is in view. */
    @Override
    public AABB getRenderBoundingBox() {
        BlockPos o = shellOrigin();
        return new AABB(o.getX(), o.getY(), o.getZ(), o.getX() + RoomShell.SIZE, o.getY() + RoomShell.SIZE, o.getZ() + RoomShell.SIZE);
    }
}
