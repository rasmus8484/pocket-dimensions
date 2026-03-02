package com.pocketdimensions.blockentity;

import com.pocketdimensions.PocketDimensionsConfig;
import com.pocketdimensions.PocketDimensionsMod;
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
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Anchor Breaker block entity - tracks anchor-destruction progress and lapis fuel.
 *
 * Progress rules mirror WorldBreakerBlockEntity, but on completion this block
 * permanently removes the WorldAnchor beneath it (and drops itself via neighborChanged).
 */
public class AnchorBreakerBlockEntity extends BlockEntity {

    /** Ticks of progress. Full destruction = BREAKER_DURATION_TICKS. */
    private int progressTicks = 0;

    /** Stack of lapis lazuli fuel. */
    private int fuel = 0;

    public AnchorBreakerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntityTypes.ANCHOR_BREAKER.get(), pos, state);
    }

    // -------------------------------------------------------------------------
    // Server tick (called from AnchorBreakerBlock.getTicker)
    // -------------------------------------------------------------------------

    public static void serverTick(Level level, BlockPos pos, BlockState state,
                                  AnchorBreakerBlockEntity be) {
        if (!(level instanceof ServerLevel serverLevel)) return;
        if (be.fuel <= 0) return;  // Paused, no decay

        // Check that WorldAnchor is directly below
        BlockPos anchorPos = pos.below();
        if (!(level.getBlockEntity(anchorPos) instanceof WorldAnchorBlockEntity anchor)) return;

        // Check defender slowdown from WorldCore inside the realm
        boolean defenderSlowed = be.isDefenderCoreActive(serverLevel, anchor);

        // Progress: normally +1/tick; with defender core: +1 every CORE_SLOW_FACTOR ticks
        boolean shouldAdvance = !defenderSlowed
                || (level.getGameTime() % PocketDimensionsConfig.CORE_SLOW_FACTOR.get() == 0);
        if (shouldAdvance) {
            be.progressTicks++;
            be.setChanged();
        }

        // Every CORE_FUEL_BURN_TICKS: drain 1 attacker lapis; drain 1 defender lapis if active
        if (level.getGameTime() % PocketDimensionsConfig.CORE_FUEL_BURN_TICKS.get() == 0) {
            be.fuel = Math.max(0, be.fuel - 1);
            if (defenderSlowed) {
                be.consumeDefenderFuel(serverLevel, anchor);
            }
            be.setChanged();
        }

        // On completion: clear anchor from RealmManager, then destroy the anchor block
        if (be.progressTicks >= PocketDimensionsConfig.BREAKER_DURATION_TICKS.get()) {
            UUID ownerUUID = anchor.getOwnerUUID();
            if (ownerUUID != null) {
                RealmManager.get(serverLevel.getServer()).clearAnchorLocation(ownerUUID);
            }
            // Destroying the anchor triggers neighborChanged on this block -> drops this block
            level.setBlock(anchorPos, Blocks.AIR.defaultBlockState(), 3);
            return;  // do not touch `be` after block removal
        }
    }

    /** Reset progress when the block is removed from the world. */
    @Override
    public void setRemoved() {
        progressTicks = 0;
        super.setRemoved();
    }

    /** Shows destruction status message to the interacting player. */
    public void sendStatusTo(Player player) {
        int pct = (int) ((progressTicks / (double) PocketDimensionsConfig.BREAKER_DURATION_TICKS.get()) * 100);
        player.displayClientMessage(Component.literal(
                "[Anchor Breaker] Anchor destruction: " + pct + "% | Fuel: " + fuel + " lapis"), false);
    }

    /** Returns true if the realm's WorldCore has defense fuel, without consuming it. */
    private boolean isDefenderCoreActive(ServerLevel level, WorldAnchorBlockEntity anchor) {
        WorldCoreBlockEntity wc = findWorldCore(level, anchor);
        return wc != null && wc.hasDefenseFuel();
    }

    /** Consumes one unit of defense fuel from the realm's WorldCore. */
    private void consumeDefenderFuel(ServerLevel level, WorldAnchorBlockEntity anchor) {
        WorldCoreBlockEntity wc = findWorldCore(level, anchor);
        if (wc != null) wc.consumeDefenseFuel();
    }

    /** Looks up the WorldCoreBlockEntity for the realm owned by this anchor's owner. */
    @Nullable
    private WorldCoreBlockEntity findWorldCore(ServerLevel level, WorldAnchorBlockEntity anchor) {
        UUID ownerUUID = anchor.getOwnerUUID();
        if (ownerUUID == null) return null;
        MinecraftServer server = level.getServer();
        if (server == null) return null;
        BlockPos corePos = RealmManager.get(server).getWorldCorePos(ownerUUID);
        if (corePos == null) return null;
        ServerLevel realmLevel = server.getLevel(PocketDimensionsMod.REALM_DIM);
        if (realmLevel == null) return null;
        return realmLevel.getBlockEntity(corePos) instanceof WorldCoreBlockEntity wc ? wc : null;
    }

    // -------------------------------------------------------------------------
    // NBT
    // -------------------------------------------------------------------------

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putInt("progress_ticks", progressTicks);
        output.putInt("fuel", fuel);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        progressTicks = input.getIntOr("progress_ticks", 0);
        fuel = input.getIntOr("fuel", 0);
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

    public int getProgressTicks() { return progressTicks; }
    public int getFuel() { return fuel; }
    public void addFuel(int amount) { fuel += amount; setChanged(); }
}
