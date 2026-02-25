package com.pocketdimensions.blockentity;

import com.pocketdimensions.init.ModBlockEntityTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * World Anchor block entity — stores realm binding info.
 * <p>
 * Access check at interaction time only (never ticked):
 * - Default: owner only.
 * - After breach + breacher exists + ≥1 lapis in breacher: open to anyone.
 */
public class WorldAnchorBlockEntity extends BlockEntity {

    private UUID ownerUUID = null;

    /** True once linked to a realm via WorldSeed. */
    private boolean linked = false;

    public WorldAnchorBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntityTypes.WORLD_ANCHOR.get(), pos, state);
    }

    // -------------------------------------------------------------------------
    // Actions
    // -------------------------------------------------------------------------

    public void tryEnterRealm(Player player, Level level, BlockPos pos) {
        if (!linked || ownerUUID == null) {
            player.sendSystemMessage(Component.literal("[PocketDimensions] This anchor is not linked to any realm. Use a WorldSeed on it first."));
            return;
        }

        if (!canAccess(player, level, pos)) {
            player.sendSystemMessage(Component.literal("[PocketDimensions] Access denied."));
            return;
        }

        // TODO (Phase 3): teleport player to the realm region via RealmManager
        player.sendSystemMessage(Component.literal("[PocketDimensions] Entering realm... (stub)"));
    }

    /**
     * Link this anchor to the given player's realm (or create a new realm if none exists).
     * Called by WorldSeedItem on use.
     */
    public void linkToPlayer(UUID playerUUID, Level level) {
        this.ownerUUID = playerUUID;
        this.linked = true;
        setChanged();
        // TODO (Phase 3): allocate/rekey realm via RealmManager
    }

    /**
     * Returns true if the player is allowed to enter the realm via this anchor.
     * Access is only checked here, at interaction time.
     */
    private boolean canAccess(Player player, Level level, BlockPos anchorPos) {
        // Owner always has access
        if (player.getUUID().equals(ownerUUID)) return true;

        // Check if a WorldBreacher above has completed a breach with fuel remaining
        BlockPos breakerPos = anchorPos.above();
        if (level.getBlockEntity(breakerPos) instanceof WorldBreakerBlockEntity breaker) {
            return breaker.isBreachComplete() && breaker.getFuel() > 0;
        }

        return false;
    }

    // -------------------------------------------------------------------------
    // NBT
    // -------------------------------------------------------------------------

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (ownerUUID != null) tag.putUUID("owner_uuid", ownerUUID);
        tag.putBoolean("linked", linked);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.hasUUID("owner_uuid")) ownerUUID = tag.getUUID("owner_uuid");
        linked = tag.getBoolean("linked");
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
    // Getters
    // -------------------------------------------------------------------------

    public UUID getOwnerUUID() { return ownerUUID; }
    public boolean isLinked() { return linked; }
}
