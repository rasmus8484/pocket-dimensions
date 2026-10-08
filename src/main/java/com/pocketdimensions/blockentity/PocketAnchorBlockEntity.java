package com.pocketdimensions.blockentity;

import com.pocketdimensions.PocketDimensionsMod;
import com.pocketdimensions.block.PocketAnchorBlock;
import com.pocketdimensions.init.ModBlockEntityTypes;
import com.pocketdimensions.init.ModItems;
import com.pocketdimensions.manager.PocketRoomManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Stores the pocket_id (UUID) and owner UUID for a placed Pocket Anchor.
 * The server resolves room coordinates from pocket_id via PocketRoomManager.
 * <p>
 * The item NBT is never trusted for coordinates - only the server-side map is.
 */
public class PocketAnchorBlockEntity extends BlockEntity {

    /** Identifies the linked pocket room in the server-side PocketRoomManager. */
    private UUID pocketId = null;

    /** Original owner for logging/reference (not used for access control). */
    private UUID ownerUUID = null;

    /** Client only: the cube's tumble phase in seconds, advancing faster while the room is occupied. */
    private double phase, prevPhase;

    public PocketAnchorBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntityTypes.POCKET_ANCHOR.get(), pos, state);
    }

    // -------------------------------------------------------------------------
    // Ticking
    // -------------------------------------------------------------------------

    /** Once a second: mirror whether anyone online is in the room into OCCUPIED (renderer speed and light level). */
    public static void serverTick(Level level, BlockPos pos, BlockState state, PocketAnchorBlockEntity be) {
        if ((level.getGameTime() + pos.asLong()) % 20 != 0 || !(level instanceof ServerLevel sl)) return;
        MinecraftServer server = sl.getServer();
        boolean occupied = be.pocketId != null && PocketRoomManager.get(server).getOccupants(be.pocketId).stream()
                .anyMatch(id -> server.getPlayerList().getPlayer(id) != null);
        if (state.getValue(PocketAnchorBlock.OCCUPIED) != occupied) {
            level.setBlock(pos, state.setValue(PocketAnchorBlock.OCCUPIED, occupied), 3);
        }
    }

    /** The tumble speeds up by half while occupied; accumulating it here keeps the cube from jumping when it does. */
    public static void clientTick(Level level, BlockPos pos, BlockState state, PocketAnchorBlockEntity be) {
        be.prevPhase = be.phase;
        be.phase += (state.getValue(PocketAnchorBlock.OCCUPIED) ? 1.5 : 1.0) / 20.0;
    }

    /** Tumble phase in seconds, interpolated to the frame. */
    public float getPhase(float partialTick) {
        return (float) (prevPhase + (phase - prevPhase) * partialTick);
    }

    // -------------------------------------------------------------------------
    // Actions
    // -------------------------------------------------------------------------

    /**
     * Right-click: teleport the player into the linked pocket room.
     * Anyone can enter - no ownership restriction.
     */
    public void enterRoom(Player player, Level level, BlockPos pos) {
        if (pocketId == null) {
            player.displayClientMessage(Component.literal("This anchor is hollow - no pocket is folded within."), false);
            return;
        }
        if (level.isClientSide()) return;

        MinecraftServer server = level.getServer();
        if (server == null) return;

        PocketRoomManager mgr = PocketRoomManager.get(server);
        ServerLevel pocketLevel = server.getLevel(PocketDimensionsMod.POCKET_DIM);
        if (pocketLevel == null) {
            player.displayClientMessage(Component.literal("The fold between worlds has collapsed. The pocket cannot be reached."), false);
            return;
        }

        // Block re-entry from inside the pocket dimension - prevents clobbering the return address.
        if (level.dimension().equals(PocketDimensionsMod.POCKET_DIM)) return;

        mgr.ensureGenerated(pocketId, pocketLevel);
        mgr.setAnchorLocation(pocketId, level.dimension(), worldPosition);
        mgr.setEntryLocation(player.getUUID(), level.dimension(),
                player.getX(), player.getY(), player.getZ(),
                player.getYRot(), player.getXRot());
        mgr.addOccupant(pocketId, player.getUUID());

        BlockPos spawn = mgr.findSafeSpawn(pocketId, pocketLevel);
        Vec3 dest = new Vec3(spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5);
        com.pocketdimensions.event.Companions.travel((ServerPlayer) player, pocketLevel, dest, 0f, 0f, com.pocketdimensions.manager.TravelRules.Journey.CHOSEN);
    }

    /**
     * Crouch + right-click: fold the anchor back into item form in the thief's inventory.
     * No warning is sent to players inside - intentional stealth mechanic.
     */
    public void stealAnchor(Player thief, Level level, BlockPos pos, BlockState state) {
        if (pocketId == null) return;

        ItemStack stack = new ItemStack(ModItems.POCKET_ANCHOR_ITEM.get());
        final UUID id = pocketId;
        final UUID owner = ownerUUID;
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
            tag.putLong("pocket_id_msb", id.getMostSignificantBits());
            tag.putLong("pocket_id_lsb", id.getLeastSignificantBits());
            if (owner != null) {
                tag.putLong("original_owner_msb", owner.getMostSignificantBits());
                tag.putLong("original_owner_lsb", owner.getLeastSignificantBits());
            }
        });

        thief.getInventory().add(stack);
        level.removeBlock(pos, false);

        // Clear the anchor from the manager so exit logic knows it's no longer placed
        if (!level.isClientSide() && level.getServer() != null) {
            PocketRoomManager.get(level.getServer()).clearAnchorLocation(pocketId);
        }
        if (owner != null && !owner.equals(thief.getUUID()))
            com.pocketdimensions.advancement.Milestones.reach(thief, com.pocketdimensions.advancement.Milestones.STEAL_ANCHOR);
        // No sound, no particle - intentionally silent
    }

    // -------------------------------------------------------------------------
    // NBT persistence
    // -------------------------------------------------------------------------

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        if (pocketId != null) {
            output.putLong("pocket_id_msb", pocketId.getMostSignificantBits());
            output.putLong("pocket_id_lsb", pocketId.getLeastSignificantBits());
        }
        if (ownerUUID != null) {
            output.putLong("owner_uuid_msb", ownerUUID.getMostSignificantBits());
            output.putLong("owner_uuid_lsb", ownerUUID.getLeastSignificantBits());
        }
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        long pidMsb = input.getLongOr("pocket_id_msb", 0L);
        long pidLsb = input.getLongOr("pocket_id_lsb", 0L);
        pocketId = (pidMsb != 0 || pidLsb != 0) ? new UUID(pidMsb, pidLsb) : null;

        long ownMsb = input.getLongOr("owner_uuid_msb", 0L);
        long ownLsb = input.getLongOr("owner_uuid_lsb", 0L);
        ownerUUID = (ownMsb != 0 || ownLsb != 0) ? new UUID(ownMsb, ownLsb) : null;
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
