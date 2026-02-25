package com.pocketdimensions.blockentity;

import com.pocketdimensions.init.ModBlockEntityTypes;
import com.pocketdimensions.init.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Stores the pocket_id (UUID) and owner UUID for a placed Pocket Anchor.
 * The server resolves room coordinates from pocket_id via PocketRoomManager.
 * <p>
 * The item NBT is never trusted for coordinates — only the server-side map is.
 */
public class PocketAnchorBlockEntity extends BlockEntity {

    /** Identifies the linked pocket room in the server-side PocketRoomManager. */
    private UUID pocketId = null;

    /** Original owner for logging/reference (not used for access control). */
    private UUID ownerUUID = null;

    public PocketAnchorBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntityTypes.POCKET_ANCHOR.get(), pos, state);
    }

    // -------------------------------------------------------------------------
    // Actions
    // -------------------------------------------------------------------------

    /**
     * Right-click: teleport the player into the linked pocket room.
     * Anyone can enter — no ownership restriction.
     */
    public void enterRoom(Player player, Level level, BlockPos pos) {
        if (pocketId == null) {
            player.sendSystemMessage(Component.literal("[PocketDimensions] This anchor is not linked to any room."));
            return;
        }
        // TODO (Phase 2): look up room coords via PocketRoomManager, teleport player
        player.sendSystemMessage(Component.literal("[PocketDimensions] Entering pocket room... (stub)"));
    }

    /**
     * Crouch + right-click: convert the anchor back into a PocketItem in the thief's inventory.
     * No warning is sent to players inside — intentional stealth mechanic.
     */
    public void stealAnchor(Player thief, Level level, BlockPos pos, BlockState state) {
        if (pocketId == null) return;

        ItemStack stack = new ItemStack(ModItems.POCKET_ITEM.get());
        CompoundTag tag = stack.getOrCreateTag();
        tag.putUUID("pocket_id", pocketId);
        if (ownerUUID != null) tag.putUUID("original_owner", ownerUUID);

        thief.getInventory().add(stack);
        level.removeBlock(pos, false);
        // No sound, no particle — intentionally silent
    }

    // -------------------------------------------------------------------------
    // NBT persistence
    // -------------------------------------------------------------------------

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (pocketId != null) tag.putUUID("pocket_id", pocketId);
        if (ownerUUID != null) tag.putUUID("owner_uuid", ownerUUID);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.hasUUID("pocket_id")) pocketId = tag.getUUID("pocket_id");
        if (tag.hasUUID("owner_uuid")) ownerUUID = tag.getUUID("owner_uuid");
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    // -------------------------------------------------------------------------
    // Getters / setters
    // -------------------------------------------------------------------------

    public UUID getPocketId() { return pocketId; }
    public void setPocketId(UUID id) { this.pocketId = id; setChanged(); }

    public UUID getOwnerUUID() { return ownerUUID; }
    public void setOwnerUUID(UUID uuid) { this.ownerUUID = uuid; setChanged(); }
}
