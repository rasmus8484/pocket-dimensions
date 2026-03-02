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
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * World Core block entity - indestructible permanent structure at realm center.
 * <p>
 * The WorldCore:
 * - Is never destroyed (block has max hardness/blast resistance).
 * - Provides guaranteed realm exit (shift + right-click).
 * - Accepts lapis fuel from the realm owner to slow active breach attempts (3x slowdown).
 * - Fuel is only consumed while an active breach is running.
 * - Emits a beacon beam whose color reflects current siege state:
 *   blue = normal, pink = World Breacher active, red = Anchor Breaker active.
 */
public class WorldCoreBlockEntity extends BlockEntity {

    // Siege state constants
    public static final int STATE_NORMAL    = 0;
    public static final int STATE_BREACHING = 1;
    public static final int STATE_BREAKING  = 2;

    private static final int COLOR_NORMAL    = 0xFF4488FF;  // blue
    private static final int COLOR_BREACHING = 0xFFFF44FF;  // pink
    private static final int COLOR_BREAKING  = 0xFFFF4444;  // red

    private UUID ownerUUID = null;

    /** Lapis fuel stored for defensive breach slowdown. */
    private int defenseFuel = 0;

    /** Current siege state - synced to client for beam colour. */
    private int siegeState = STATE_NORMAL;

    public WorldCoreBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntityTypes.WORLD_CORE.get(), pos, state);
    }

    // -------------------------------------------------------------------------
    // Server tick (called from WorldCoreBlock.getTicker)
    // -------------------------------------------------------------------------

    public static void serverTick(Level level, BlockPos pos, BlockState state,
                                  WorldCoreBlockEntity be) {
        if (!(level instanceof ServerLevel serverLevel)) return;
        if (level.getGameTime() % 20 != 0) return;

        int newState = computeSiegeState(be, serverLevel);
        if (newState != be.siegeState) {
            be.siegeState = newState;
            be.setChanged();
            level.sendBlockUpdated(pos, state, state, 3);
        }
    }

    private static int computeSiegeState(WorldCoreBlockEntity be, ServerLevel serverLevel) {
        if (be.ownerUUID == null) return STATE_NORMAL;
        MinecraftServer server = serverLevel.getServer();
        if (server == null) return STATE_NORMAL;
        var optAnchor = RealmManager.get(server).getAnchorLocation(be.ownerUUID);
        if (optAnchor.isEmpty()) return STATE_NORMAL;
        var anchorEntry = optAnchor.get();
        ServerLevel anchorLevel = server.getLevel(anchorEntry.getKey());
        if (anchorLevel == null) return STATE_NORMAL;
        BlockPos siegePos = anchorEntry.getValue().above();
        BlockEntity siegeBe = anchorLevel.getBlockEntity(siegePos);
        if (siegeBe instanceof AnchorBreakerBlockEntity ab && ab.getFuel() > 0) return STATE_BREAKING;
        if (siegeBe instanceof WorldBreacherBlockEntity) return STATE_BREACHING;
        return STATE_NORMAL;
    }

    // -------------------------------------------------------------------------
    // Actions
    // -------------------------------------------------------------------------

    /**
     * Shift + right-click: exit the realm, returning the player to their entry location.
     */
    public void exitRealm(Player player, Level level) {
        if (!(player instanceof ServerPlayer sp)) return;
        if (level.isClientSide()) return;
        RealmEventHandler.queueRealmExit(sp.getUUID());
    }

    /**
     * Shift+right-click with lapis: insert defense fuel (owner only).
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

    /** Called by siege block entities to check if the defender slowdown is active. */
    public boolean hasDefenseFuel() { return defenseFuel > 0; }

    /** Consume 1 lapis per breach tick-batch (called from siege block entities). */
    public void consumeDefenseFuel() {
        if (defenseFuel > 0) {
            defenseFuel--;
            setChanged();
        }
    }

    /** Returns the ARGB beam colour for the current siege state. */
    public int getBeamColor() {
        return switch (siegeState) {
            case STATE_BREACHING -> COLOR_BREACHING;
            case STATE_BREAKING  -> COLOR_BREAKING;
            default              -> COLOR_NORMAL;
        };
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
        output.putInt("siege_state", siegeState);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        long msb = input.getLongOr("owner_uuid_msb", 0L);
        long lsb = input.getLongOr("owner_uuid_lsb", 0L);
        ownerUUID = (msb != 0 || lsb != 0) ? new UUID(msb, lsb) : null;
        defenseFuel = input.getIntOr("defense_fuel", 0);
        siegeState = input.getIntOr("siege_state", STATE_NORMAL);
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

    @Override
    public AABB getRenderBoundingBox() {
        return INFINITE_EXTENT_AABB;
    }
}
