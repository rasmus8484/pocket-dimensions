package com.pocketdimensions.blockentity;

import com.pocketdimensions.PocketDimensionsMod;
import com.pocketdimensions.event.RealmEventHandler;
import com.pocketdimensions.init.ModBlockEntityTypes;
import com.pocketdimensions.manager.RealmManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * World Anchor block entity - stores realm binding info.
 * <p>
 * Access check at interaction time only (never ticked):
 * - Phase 3: owner only.
 * - Phase 4: after breach + breacher exists + fuel: open to anyone.
 */
public class WorldAnchorBlockEntity extends BlockEntity {

    private static final Logger LOGGER = LogManager.getLogger();

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
            player.displayClientMessage(Component.literal(
                    "The anchor sits cold and dormant. It needs a World Seed to awaken."), false);
            return;
        }

        // No use inside a pocket room
        if (level.dimension().equals(PocketDimensionsMod.POCKET_DIM)) {
            player.displayClientMessage(Component.literal(
                    "The walls of this pocket press too close. You cannot reach a realm from here."), false);
            return;
        }

        // No re-entry from inside the realm
        if (level.dimension().equals(PocketDimensionsMod.REALM_DIM)) {
            player.displayClientMessage(Component.literal(
                    "You already stand on foreign ground."), false);
            return;
        }

        // Phase 4: owner always; anyone else only if a completed World Breacher with fuel is on top
        if (!canAccess(player, level, worldPosition)) {
            player.displayClientMessage(Component.literal("The anchor's wards hold firm. You shall not pass."), false);
            return;
        }

        MinecraftServer server = level.getServer();
        if (server == null) {
            LOGGER.warn("[WorldAnchor] server is null - level.isClientSide()={}", level.isClientSide());
            player.displayClientMessage(Component.literal("The rift flickers and collapses. Something is deeply wrong."), false);
            return;
        }

        RealmManager mgr = RealmManager.get(server);
        ServerLevel realmLevel = server.getLevel(PocketDimensionsMod.REALM_DIM);
        if (realmLevel == null) {
            player.displayClientMessage(Component.literal(
                    "The realm beyond refuses to answer. The void is silent."), false);
            return;
        }

        mgr.ensureGenerated(ownerUUID, realmLevel);
        mgr.setAnchorLocation(ownerUUID, level.dimension(), worldPosition);
        mgr.setEntryLocation(player.getUUID(), level.dimension(),
                player.getX(), player.getY(), player.getZ(),
                player.getYRot(), player.getXRot());

        // Record persistent realm bounds so enforcement survives server restarts
        int[] bounds = mgr.getRealmBounds(ownerUUID); // [minX, minZ, maxX, maxZ]
        BlockPos spawn = mgr.getSpawnPos(ownerUUID);
        mgr.setPlayerRealmInfo(player.getUUID(), ownerUUID,
                bounds[0], bounds[2], bounds[1], bounds[3], spawn);

        RealmEventHandler.queueRealmEntry(player.getUUID(), ownerUUID);
    }

    /**
     * Unlink this anchor from its realm (called when a WorldSeed rekeys to a different anchor).
     */
    public void unlink() {
        this.ownerUUID = null;
        this.linked = false;
        setChanged();
    }

    /**
     * Returns true if the player is allowed to enter the realm via this anchor.
     * Access is only checked here, at interaction time.
     * <p>
     * Phase 4 will relax this via World Breacher breach state.
     */
    private boolean canAccess(Player player, Level level, BlockPos anchorPos) {
        if (player.getUUID().equals(ownerUUID)) return true;

        BlockPos breakerPos = anchorPos.above();
        if (level.getBlockEntity(breakerPos) instanceof WorldBreacherBlockEntity breacher) {
            return breacher.isBreachComplete() && breacher.getFuel() > 0;
        }

        return false;
    }

    // -------------------------------------------------------------------------
    // NBT
    // -------------------------------------------------------------------------

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        if (ownerUUID != null) {
            output.putLong("owner_uuid_msb", ownerUUID.getMostSignificantBits());
            output.putLong("owner_uuid_lsb", ownerUUID.getLeastSignificantBits());
        }
        output.putBoolean("linked", linked);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        long msb = input.getLongOr("owner_uuid_msb", 0L);
        long lsb = input.getLongOr("owner_uuid_lsb", 0L);
        ownerUUID = (msb != 0 || lsb != 0) ? new UUID(msb, lsb) : null;
        linked = input.getBooleanOr("linked", false);
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

    public UUID getOwnerUUID() { return ownerUUID; }
    public void setOwnerUUID(UUID uuid) { this.ownerUUID = uuid; setChanged(); }
    public boolean isLinked() { return linked; }
    public void setLinked(boolean linked) { this.linked = linked; setChanged(); }
}
