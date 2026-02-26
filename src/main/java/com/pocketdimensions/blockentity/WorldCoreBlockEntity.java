package com.pocketdimensions.blockentity;

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
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * World Core block entity — indestructible permanent structure at realm center.
 * <p>
 * The WorldCore:
 * - Is never destroyed (block has max hardness/blast resistance).
 * - Provides guaranteed realm exit (shift + right-click).
 * - Accepts lapis fuel from the realm owner to slow active breach attempts (3× slowdown).
 * - Fuel is only consumed while an active breach is running.
 */
public class WorldCoreBlockEntity extends BlockEntity {

    private UUID ownerUUID = null;

    /** Lapis fuel stored for defensive breach slowdown. */
    private int defenseFuel = 0;

    public WorldCoreBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntityTypes.WORLD_CORE.get(), pos, state);
    }

    // -------------------------------------------------------------------------
    // Actions
    // -------------------------------------------------------------------------

    /**
     * Shift + right-click: exit the realm, returning the player to their entry location.
     * Entry location is stored per-player in RealmManager, not in this block entity.
     */
    public void exitRealm(Player player, Level level) {
        if (!(player instanceof ServerPlayer sp)) return;
        if (level.isClientSide()) return;
        // Queue exit via RealmEventHandler — fires from PlayerTickEvent next tick.
        // Direct teleport mid-interaction causes packet ordering issues.
        RealmEventHandler.queueRealmExit(sp.getUUID());
    }

    /**
     * Shift+right-click with lapis: insert defense fuel (owner only).
     * The block already verified the player is holding lapis; stack is consumed here.
     */
    public void tryInsertFuel(Player player, ItemStack stack, Level level) {
        if (ownerUUID == null || !player.getUUID().equals(ownerUUID)) {
            player.displayClientMessage(Component.literal(
                    "[WorldCore] Fuel: " + defenseFuel + " lapis | Only the realm owner can insert fuel."), false);
            return;
        }
        int toAdd = stack.getCount();
        defenseFuel += toAdd;
        stack.shrink(toAdd);
        setChanged();
        player.displayClientMessage(Component.literal(
                "[WorldCore] Inserted " + toAdd + " lapis. Total defense fuel: " + defenseFuel), false);
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
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        if (ownerUUID != null) {
            output.putLong("owner_uuid_msb", ownerUUID.getMostSignificantBits());
            output.putLong("owner_uuid_lsb", ownerUUID.getLeastSignificantBits());
        }
        output.putInt("defense_fuel", defenseFuel);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        long msb = input.getLongOr("owner_uuid_msb", 0L);
        long lsb = input.getLongOr("owner_uuid_lsb", 0L);
        ownerUUID = (msb != 0 || lsb != 0) ? new UUID(msb, lsb) : null;
        defenseFuel = input.getIntOr("defense_fuel", 0);
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
}
