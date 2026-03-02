package com.pocketdimensions.blockentity;

import com.pocketdimensions.PocketDimensionsMod;
import com.pocketdimensions.PocketDimensionsConfig;
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
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * World Breacher block entity - tracks siege progress and lapis fuel.
 * <p>
 * Progress rules:
 * - Base duration: config BREACH_DURATION_TICKS (default 24000 = 1 MC day).
 * - Advances only when: breacher exists, WorldAnchor below exists, fuel > 0, chunk loaded.
 * - If fuel runs out: progress PAUSES (no decay).
 * - If breacher is destroyed: progress RESETS to 0.
 * - WorldCore fuel (defender) reduces progress rate to 1/CORE_SLOW_FACTOR.
 */
public class WorldBreacherBlockEntity extends BlockEntity {

    /** Ticks of progress. Full breach = BREACH_DURATION_TICKS. */
    private int progressTicks = 0;

    /** Stack of lapis lazuli fuel. */
    private int fuel = 0;

    public WorldBreacherBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntityTypes.WORLD_BREACHER.get(), pos, state);
    }

    // -------------------------------------------------------------------------
    // Server tick (called from WorldBreacherBlock.getTicker)
    // -------------------------------------------------------------------------

    public static void serverTick(Level level, BlockPos pos, BlockState state,
                                  WorldBreacherBlockEntity be) {
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
            be.progressTicks = Math.min(be.progressTicks + 1,
                    PocketDimensionsConfig.BREACH_DURATION_TICKS.get());
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
    }

    /** Reset progress when the block is removed from the world. */
    @Override
    public void setRemoved() {
        progressTicks = 0;
        super.setRemoved();
    }

    /** Shows siege status message to the interacting player. */
    public void sendStatusTo(Player player) {
        int pct = (int) ((progressTicks / (double) PocketDimensionsConfig.BREACH_DURATION_TICKS.get()) * 100);
        player.displayClientMessage(Component.literal(
                "[World Breacher] Siege progress: " + pct + "% | Fuel: " + fuel + " lapis"), false);
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

    public boolean isBreachComplete() {
        return progressTicks >= PocketDimensionsConfig.BREACH_DURATION_TICKS.get();
    }
    public int getProgressTicks() { return progressTicks; }
    public int getFuel() { return fuel; }
    public void addFuel(int amount) { fuel += amount; setChanged(); }
}
