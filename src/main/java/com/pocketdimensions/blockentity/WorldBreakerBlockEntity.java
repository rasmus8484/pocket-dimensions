package com.pocketdimensions.blockentity;

import com.pocketdimensions.init.ModBlockEntityTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jetbrains.annotations.Nullable;

/**
 * World Breacher block entity — tracks siege progress and lapis fuel.
 * <p>
 * Progress rules:
 * - Base duration: 20 minutes (24000 ticks) of uninterrupted progress.
 * - Advances only when: breacher exists, WorldAnchor below exists, fuel > 0, chunk loaded.
 * - If fuel runs out: progress PAUSES (no decay).
 * - If breacher is destroyed: progress RESETS to 0.
 * - WorldCore fuel (defender) reduces progress rate to 1/3 (3× slowdown).
 */
public class WorldBreakerBlockEntity extends BlockEntity {

    /** Ticks of progress. Full breach = BASE_DURATION_TICKS. */
    private int progressTicks = 0;

    /** Stack of lapis lazuli fuel. */
    private int fuel = 0;

    /** 20 min × 20 tps × 60 s/min = 24 000 ticks for a full breach. */
    public static final int BASE_DURATION_TICKS = 24000;

    public WorldBreakerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntityTypes.WORLD_BREAKER.get(), pos, state);
    }

    // -------------------------------------------------------------------------
    // Server tick (called from WorldBreakerBlock.getTicker)
    // -------------------------------------------------------------------------

    public static void serverTick(Level level, BlockPos pos, BlockState state,
                                  WorldBreakerBlockEntity be) {
        if (!(level instanceof ServerLevel serverLevel)) return;
        if (be.fuel <= 0) return;  // Paused, no decay

        // Check that WorldAnchor is directly below
        BlockPos anchorPos = pos.below();
        if (!(level.getBlockEntity(anchorPos) instanceof WorldAnchorBlockEntity anchor)) return;

        // Check defender slowdown from WorldCore inside the realm
        boolean defenderSlowed = be.isDefenderCoreActive(serverLevel, anchor);

        // Progress: normally +1/tick; with defender core: +1 every 3 ticks
        boolean shouldAdvance = !defenderSlowed || (level.getGameTime() % 3 == 0);
        if (shouldAdvance) {
            be.progressTicks = Math.min(be.progressTicks + 1, BASE_DURATION_TICKS);
            be.setChanged();
        }

        // Consume fuel: 1 lapis per 200 ticks (~10 seconds) — adjust for balance
        if (level.getGameTime() % 200 == 0) {
            be.fuel = Math.max(0, be.fuel - 1);
            be.setChanged();
        }
    }

    /** Reset progress when the block is removed from the world. */
    @Override
    public void setRemoved() {
        progressTicks = 0;
        super.setRemoved();
    }

    /** Shows siege status message to the interacting player. */
    public void sendStatusTo(Player player) {
        int pct = (int) ((progressTicks / (double) BASE_DURATION_TICKS) * 100);
        player.displayClientMessage(Component.literal(
                "[WorldBreacher] Siege progress: " + pct + "% | Fuel: " + fuel + " lapis"), false);
    }

    /**
     * Checks whether the realm's WorldCore has defensive fuel active.
     * TODO (Phase 3/4): query RealmManager for the WorldCore of this anchor's owner realm.
     */
    private boolean isDefenderCoreActive(ServerLevel level, WorldAnchorBlockEntity anchor) {
        // Stub — always returns false until WorldCore fueling is implemented
        return false;
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

    public boolean isBreachComplete() { return progressTicks >= BASE_DURATION_TICKS; }
    public int getProgressTicks() { return progressTicks; }
    public int getFuel() { return fuel; }
    public void addFuel(int amount) { fuel += amount; setChanged(); }
}
