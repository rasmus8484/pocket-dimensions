package com.pocketdimensions.blockentity;

import com.pocketdimensions.block.WorldBreacherBlock;
import com.pocketdimensions.block.WorldAnchorBlock;
import com.pocketdimensions.PocketDimensionsMod;
import com.pocketdimensions.PocketDimensionsConfig;
import com.pocketdimensions.SiegeTuning;
import com.pocketdimensions.init.ModBlockEntityTypes;
import com.pocketdimensions.network.SiegeBarS2C;
import com.pocketdimensions.manager.RealmManager;
import com.pocketdimensions.menu.SiegeBlockMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
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
public class WorldBreacherBlockEntity extends BlockEntity implements MenuProvider {

    /** Ticks of progress. Full breach = BREACH_DURATION_TICKS. */
    private int progressTicks = 0;

    /** Legacy lapis fuel counter (from direct right-click fueling on older worlds). Drained before slot. */
    private int fuel = 0;

    /** Persistent 1-slot inventory for lapis fuel (visible in the GUI). */
    private final SimpleContainer inventory = new SimpleContainer(1) {
        @Override
        public boolean canPlaceItem(int slot, ItemStack stack) {
            return stack.is(Items.LAPIS_LAZULI);
        }

        /** Lapis capacity (server config); the GUI slot reads this on both sides. */
        @Override
        public int getMaxStackSize() {
            return com.pocketdimensions.PocketDimensionsServerConfig.WORLD_BREACHER_MAX_LAPIS.get();
        }
    };

    /** Who sees this siege's bar (client/siegebar draws it). Transient: rebuilt within a second after a restart. */
    private final SiegeBarTracker bar = new SiegeBarTracker();
    /** Ticks the lapis now burning has burnt (of core_fuel_burn_ticks); kept when the siege stops, saved. */
    private int burnt = 0;
    /** The lapis counts the bar was last sent ({@link #lapisSignature}). */
    private long sentLapis = -1;

    public WorldBreacherBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntityTypes.WORLD_BREACHER.get(), pos, state);
        inventory.addListener(c -> setChanged());
    }

    // -------------------------------------------------------------------------
    // Fuel helpers
    // -------------------------------------------------------------------------

    /** Returns true if any fuel is available (counter or slot). */
    public boolean hasFuel() {
        return fuel > 0 || !inventory.getItem(0).isEmpty();
    }

    /** Consume one unit of fuel: drain legacy counter first, then slot. */
    private void consumeOneFuel() {
        if (fuel > 0) {
            fuel--;
        } else {
            ItemStack slot = inventory.getItem(0);
            if (!slot.isEmpty()) slot.shrink(1);
        }
        setChanged();
    }

    /** Insert lapis into the inventory slot. Returns the amount actually inserted. */
    public int insertLapis(int amount) {
        ItemStack slot = inventory.getItem(0);
        int space = inventory.getMaxStackSize() - (slot.isEmpty() ? 0 : slot.getCount());
        int toAdd = Math.min(amount, space);
        if (toAdd <= 0) return 0;
        if (slot.isEmpty()) {
            inventory.setItem(0, new ItemStack(Items.LAPIS_LAZULI, toAdd));
        } else {
            slot.grow(toAdd);
        }
        return toAdd;
    }

    // -------------------------------------------------------------------------
    // Server tick (called from WorldBreacherBlock.getTicker)
    // -------------------------------------------------------------------------

    /**
     * Client: while the beam is lit (breach complete and fueled) pink runes climb it in a slow double helix from the
     * Mandible's eye, like the World Core's, one every 0.75 s alternating between the strands.
     */
    public static void clientTick(Level level, BlockPos pos, BlockState state, WorldBreacherBlockEntity be) {
        long t = level.getGameTime();
        if (t % 15 != 0 || !state.getValue(com.pocketdimensions.block.WorldBreacherBlock.COMPLETE)) return;
        double strand = (t / 15) % 2 == 0 ? 0 : Math.PI;
        level.addParticle(com.pocketdimensions.init.ModParticles.RUNE_HELIX.get(),
                pos.getX() + 0.5, pos.getY() + 0.75, pos.getZ() + 0.5, strand, 0, 0xFF5ADC);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state,
                                  WorldBreacherBlockEntity be) {
        if (!(level instanceof ServerLevel serverLevel)) return;

        // Check that WorldAnchor LOWER half is 2 below (siege block sits on UPPER half)
        BlockPos anchorPos = pos.below(2);
        boolean hasAnchor = level.getBlockEntity(anchorPos) instanceof WorldAnchorBlockEntity;
        WorldAnchorBlockEntity anchor = hasAnchor
                ? (WorldAnchorBlockEntity) level.getBlockEntity(anchorPos) : null;

        // Check defender slowdown from WorldCore inside the realm
        boolean defended = hasAnchor && anchor != null && be.isDefenderCoreActive(serverLevel, anchor);

        // Progress + fuel drain (only when fueled and anchor present). A complete breach stays open on its own and
        // burns nothing more, on either side.
        if (!be.isBreachComplete() && be.hasFuel() && hasAnchor && anchor != null) {
            boolean shouldAdvance = !defended
                    || (level.getGameTime() % PocketDimensionsConfig.CORE_SLOW_FACTOR.get() == 0);
            if (shouldAdvance) {
                boolean wasDone = be.isBreachComplete();
                be.progressTicks = Math.min(be.progressTicks + 1,
                        PocketDimensionsConfig.BREACH_DURATION_TICKS.get());
                be.setChanged();
                // Sync to client the moment breach completes so the beacon beam appears immediately
                if (!wasDone && be.isBreachComplete()) {
                    level.sendBlockUpdated(pos, state, state, 3);
                }
            }

            // Each side's lapis lasts core_fuel_burn_ticks of running siege, timed from when it starts burning
            int burnTicks = PocketDimensionsConfig.CORE_FUEL_BURN_TICKS.get();
            be.burnt = SiegeTuning.burnTick(be.burnt, burnTicks);
            if (be.burnt == 0) be.consumeOneFuel();
            if (defended) be.burnDefenderFuel(serverLevel, anchor, burnTicks);
            be.setChanged();
        }

        // Siege visuals: tint the anchor's runes with breach progress and light the eye when complete
        if (level.getGameTime() % 20 == 0 || be.isBreachComplete() != state.getValue(WorldBreacherBlock.COMPLETE)) {
            int duration = PocketDimensionsConfig.BREACH_DURATION_TICKS.get();
            int influence = be.isBreachComplete() ? 4 : 1 + Math.min(2, (int) (3L * be.progressTicks / duration));
            if (hasAnchor) WorldAnchorBlock.setInfluence(level, anchorPos, influence);
            if (state.getValue(WorldBreacherBlock.COMPLETE) != be.isBreachComplete()) {
                level.setBlock(pos, state.setValue(WorldBreacherBlock.COMPLETE, be.isBreachComplete()), 3);
            }
        }
        // Keep clients' copy of fuel/progress fresh (the beam only shows while fueled)
        if (level.getGameTime() % 40 == 0) level.sendBlockUpdated(pos, state, state, 3);

        // Force-load the WorldCore chunk in realm so it can tick (beacon color, defense fuel)
        if (level.getGameTime() % 200 == 0 && (be.hasFuel() || be.progressTicks > 0)
                && anchor != null && anchor.getOwnerUUID() != null) {
            BlockPos corePos = RealmManager.get(serverLevel.getServer()).getWorldCorePos(anchor.getOwnerUUID());
            if (corePos != null) {
                ServerLevel realmLevel = serverLevel.getServer().getLevel(PocketDimensionsMod.REALM_DIM);
                if (realmLevel != null) {
                    ((ServerChunkCache) realmLevel.getChunkSource())
                            .addTicketWithRadius(TicketType.PORTAL, new ChunkPos(corePos), 2);
                }
            }
        }

        // --- Siege bar (gone once the breach completes: the beacon beam takes over) ---
        if (!be.isBreachComplete() && (be.progressTicks > 0 || be.hasFuel())) {
            // once a second, and at once when lapis goes in or out on either side
            long lapis = be.lapisSignature(serverLevel, anchor);
            if (level.getGameTime() % 20 == 0 || lapis != be.sentLapis) {
                be.sentLapis = lapis;
                be.bar.update(serverLevel, pos, anchor, be.barState(serverLevel, anchor, defended));
            }
        } else {
            be.bar.clear();
        }
    }

    /** Reset progress when the block is removed from the world. */
    @Override
    public void setRemoved() {
        bar.clear();
        progressTicks = 0;
        super.setRemoved();
    }

    // -------------------------------------------------------------------------
    // Siege bar
    // -------------------------------------------------------------------------

    /** This siege as its bar shows it: progress, pace (0 dormant, 1, or the core's slow factor), both lapis counts. */
    private SiegeBarS2C barState(ServerLevel level, @Nullable WorldAnchorBlockEntity anchor, boolean defended) {
        WorldCoreBlockEntity wc = anchor != null ? findWorldCore(level, anchor) : null;
        int rate = !hasFuel() || anchor == null ? 0 : defended ? PocketDimensionsConfig.CORE_SLOW_FACTOR.get() : 1;
        return new SiegeBarS2C(null, false, SiegeBarS2C.BREACHER, progressTicks, PocketDimensionsConfig.BREACH_DURATION_TICKS.get(), rate,
                fuel + inventory.getItem(0).getCount(), com.pocketdimensions.PocketDimensionsServerConfig.WORLD_BREACHER_MAX_LAPIS.get(),
                wc != null ? wc.getDefenseLapis() : 0, burnt, wc != null ? wc.getDefenseBurnt() : 0,
                PocketDimensionsConfig.CORE_FUEL_BURN_TICKS.get());
    }

    // -------------------------------------------------------------------------
    // Defender helpers
    // -------------------------------------------------------------------------

    /** Returns true if the realm's WorldCore has defense fuel, without consuming it. */
    private boolean isDefenderCoreActive(ServerLevel level, WorldAnchorBlockEntity anchor) {
        WorldCoreBlockEntity wc = findWorldCore(level, anchor);
        return wc != null && wc.hasDefenseFuel();
    }

    /** Consumes one unit of defense fuel from the realm's WorldCore. */
    private void burnDefenderFuel(ServerLevel level, WorldAnchorBlockEntity anchor, int burnTicks) {
        WorldCoreBlockEntity wc = findWorldCore(level, anchor);
        if (wc != null) wc.burnDefenseFuel(burnTicks);
    }

    /** Both lapis counts in one number, to notice the moment either changes. */
    private long lapisSignature(ServerLevel level, @Nullable WorldAnchorBlockEntity anchor) {
        WorldCoreBlockEntity wc = anchor != null ? findWorldCore(level, anchor) : null;
        return ((long) (fuel + inventory.getItem(0).getCount()) << 32) | (wc != null ? wc.getDefenseLapis() : 0);
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
        output.putInt("burnt", burnt);
        ItemStack slot = inventory.getItem(0);
        output.putInt("slot_lapis_count", slot.isEmpty() ? 0 : slot.getCount());
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        progressTicks = input.getIntOr("progress_ticks", 0);
        fuel = input.getIntOr("fuel", 0);
        burnt = input.getIntOr("burnt", 0);
        int slotCount = input.getIntOr("slot_lapis_count", 0);
        if (slotCount > 0) {
            inventory.setItem(0, new ItemStack(Items.LAPIS_LAZULI, slotCount));
        } else {
            inventory.setItem(0, ItemStack.EMPTY);
        }
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
    public SimpleContainer getInventory() { return inventory; }

    // -------------------------------------------------------------------------
    // MenuProvider
    // -------------------------------------------------------------------------

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.pocketdimensions.world_breacher");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInv, Player player) {
        return new SiegeBlockMenu(containerId, playerInv, this);
    }

    public ContainerData createContainerData() {
        return new ContainerData() {
            @Override
            public int get(int index) {
                return switch (index) {
                    case 0 -> progressTicks;
                    case 1 -> PocketDimensionsConfig.BREACH_DURATION_TICKS.get();
                    case 2 -> {
                        if (!(level instanceof ServerLevel sl)) yield 0;
                        BlockPos anchorPos = worldPosition.below(2);
                        if (!(sl.getBlockEntity(anchorPos) instanceof WorldAnchorBlockEntity anchor)) yield 0;
                        WorldCoreBlockEntity wc = findWorldCore(sl, anchor);
                        yield (wc != null && wc.hasDefenseFuel()) ? 1 : 0;
                    }
                    default -> 0;
                };
            }

            @Override
            public void set(int index, int value) {}

            @Override
            public int getCount() { return 3; }
        };
    }

    @Override
    public AABB getRenderBoundingBox() {
        return INFINITE_EXTENT_AABB;
    }
}
