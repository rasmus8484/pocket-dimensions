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
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * World Core block entity — indestructible permanent structure at realm center.
 * <p>
 * The WorldCore:
 * - Is never destroyed (block has max hardness/blast resistance).
 * - Provides guaranteed realm exit (crouch + right-click).
 * - Accepts lapis fuel from the realm owner to slow active breach attempts (3× slowdown).
 * - Fuel is only consumed while an active breach is running.
 */
public class WorldCoreBlockEntity extends BlockEntity {

    private UUID ownerUUID = null;

    /** Lapis fuel stored for defensive breach slowdown. */
    private int defenseFuel = 0;

    /** Where the owner last entered the realm from (for exit targeting). */
    private double exitX, exitY, exitZ;
    private float exitYaw, exitPitch;
    private String exitDimension = "minecraft:overworld";

    public WorldCoreBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntityTypes.WORLD_CORE.get(), pos, state);
    }

    // -------------------------------------------------------------------------
    // Actions
    // -------------------------------------------------------------------------

    /**
     * Crouch + right-click: exit the realm, returning the player to their entry location.
     */
    public void exitRealm(Player player, Level level) {
        // TODO (Phase 3): teleport player to exitX/Y/Z in exitDimension
        player.sendSystemMessage(Component.literal("[PocketDimensions] Exiting realm... (stub)"));
    }

    /**
     * Right-click: insert lapis fuel (owner only) or display status.
     * Fuel is consumed only during an active breach attempt.
     */
    public void tryInsertFuel(Player player, Level level) {
        if (ownerUUID == null || !player.getUUID().equals(ownerUUID)) {
            player.sendSystemMessage(Component.literal(
                    "[WorldCore] Fuel: " + defenseFuel + " lapis | Only the realm owner can insert fuel."));
            return;
        }

        // Count lapis in the player's main hand
        var hand = player.getMainHandItem();
        if (hand.is(Items.LAPIS_LAZULI) && hand.getCount() > 0) {
            int toAdd = hand.getCount();
            defenseFuel += toAdd;
            hand.shrink(toAdd);
            setChanged();
            player.sendSystemMessage(Component.literal(
                    "[WorldCore] Inserted " + toAdd + " lapis. Total defense fuel: " + defenseFuel));
        } else {
            player.sendSystemMessage(Component.literal(
                    "[WorldCore] Defense fuel: " + defenseFuel + " lapis. Hold lapis to insert."));
        }
    }

    /** Called by WorldBreakerBlockEntity to check if the defender slowdown is active. */
    public boolean hasDefenseFuel() { return defenseFuel > 0; }

    /** Consume 1 lapis per breach tick-batch (called from WorldBreakerBlockEntity). */
    public void consumeDefenseFuel() {
        if (defenseFuel > 0) {
            defenseFuel--;
            setChanged();
        }
    }

    // -------------------------------------------------------------------------
    // NBT
    // -------------------------------------------------------------------------

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (ownerUUID != null) tag.putUUID("owner_uuid", ownerUUID);
        tag.putInt("defense_fuel", defenseFuel);
        tag.putDouble("exit_x", exitX);
        tag.putDouble("exit_y", exitY);
        tag.putDouble("exit_z", exitZ);
        tag.putFloat("exit_yaw", exitYaw);
        tag.putFloat("exit_pitch", exitPitch);
        tag.putString("exit_dimension", exitDimension);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.hasUUID("owner_uuid")) ownerUUID = tag.getUUID("owner_uuid");
        defenseFuel = tag.getInt("defense_fuel");
        exitX = tag.getDouble("exit_x");
        exitY = tag.getDouble("exit_y");
        exitZ = tag.getDouble("exit_z");
        exitYaw = tag.getFloat("exit_yaw");
        exitPitch = tag.getFloat("exit_pitch");
        exitDimension = tag.getString("exit_dimension");
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

    public void setExitLocation(double x, double y, double z, float yaw, float pitch, String dimension) {
        this.exitX = x; this.exitY = y; this.exitZ = z;
        this.exitYaw = yaw; this.exitPitch = pitch;
        this.exitDimension = dimension;
        setChanged();
    }
}
