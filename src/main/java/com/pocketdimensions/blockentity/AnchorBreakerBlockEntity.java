package com.pocketdimensions.blockentity;

import com.pocketdimensions.PocketDimensionsConfig;
import com.pocketdimensions.PocketDimensionsMod;
import com.pocketdimensions.block.AnchorBreakerBlock;
import com.pocketdimensions.block.WorldAnchorBlock;
import com.pocketdimensions.init.ModBlockEntityTypes;
import com.pocketdimensions.network.SiegeBarS2C;
import com.pocketdimensions.init.ModSounds;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.AABB;
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
 * Progress rules mirror WorldBreacherBlockEntity, but on completion this block
 * permanently removes the WorldAnchor beneath it (and drops itself via neighborChanged).
 */
public class AnchorBreakerBlockEntity extends BlockEntity implements MenuProvider {

    /** Ticks of progress. Full destruction = BREAKER_DURATION_TICKS. */
    private int progressTicks = 0;
    /** Who fixed this breaker on the anchor; told when it severs it (Severed). Null for breakers placed before this. */
    private @org.jetbrains.annotations.Nullable UUID placer;

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
            return com.pocketdimensions.PocketDimensionsServerConfig.ANCHOR_BREAKER_MAX_LAPIS.get();
        }
    };

    /** Who sees this siege's bar (client/siegebar draws it). Transient: rebuilt within a second after a restart. */
    private final SiegeBarTracker bar = new SiegeBarTracker();

    /** Fuel presence last sent to clients (null = not yet this session); the siphon stream shows only while fueled. */
    @Nullable private Boolean syncedFuel = null;

    /** Volume of the reality crack; variable-range sounds reach 16 blocks per unit of volume. */
    private static final float CRACK_VOLUME = 4.0f;

    public AnchorBreakerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntityTypes.ANCHOR_BREAKER.get(), pos, state);
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

    /** Breaking quarter for the given progress: 0 not started, then 1..4 for 0-25 % .. 75-100 %. */
    public static int chargeLevel(int progressTicks, int durationTicks) {
        if (progressTicks <= 0) return 0;
        return 1 + Math.min(3, (int) (4L * progressTicks / Math.max(1, durationTicks)));
    }

    private static void playCrack(Level level, BlockPos anchorPos) {
        level.playSound(null, anchorPos.above(), ModSounds.REALITY_CRACK.get(), SoundSource.BLOCKS, CRACK_VOLUME, 1.0f);
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
    // Server tick (called from AnchorBreakerBlock.getTicker)
    // -------------------------------------------------------------------------

    public static void serverTick(Level level, BlockPos pos, BlockState state,
                                  AnchorBreakerBlockEntity be) {
        if (!(level instanceof ServerLevel serverLevel)) return;

        // Check that WorldAnchor LOWER half is 2 below (siege block sits on UPPER half)
        BlockPos anchorPos = pos.below(2);
        boolean hasAnchor = level.getBlockEntity(anchorPos) instanceof WorldAnchorBlockEntity;
        WorldAnchorBlockEntity anchor = hasAnchor
                ? (WorldAnchorBlockEntity) level.getBlockEntity(anchorPos) : null;

        // Check defender slowdown from WorldCore inside the realm
        boolean defended = hasAnchor && anchor != null && be.isDefenderCoreActive(serverLevel, anchor);

        // Progress + fuel drain (only when fueled and anchor present)
        if (be.hasFuel() && hasAnchor && anchor != null) {
            boolean shouldAdvance = !defended
                    || (level.getGameTime() % PocketDimensionsConfig.CORE_SLOW_FACTOR.get() == 0);
            if (shouldAdvance) {
                be.progressTicks++;
                be.setChanged();
            }

            // Every CORE_FUEL_BURN_TICKS: drain 1 attacker lapis; drain 1 defender lapis if active
            if (level.getGameTime() % PocketDimensionsConfig.CORE_FUEL_BURN_TICKS.get() == 0) {
                be.consumeOneFuel();
                if (defended) {
                    be.consumeDefenderFuel(serverLevel, anchor);
                }
            }

            // On completion: clear anchor from RealmManager, then destroy the anchor block
            if (be.progressTicks >= PocketDimensionsConfig.BREAKER_DURATION_TICKS.get()) {
                UUID ownerUUID = anchor.getOwnerUUID();
                if (ownerUUID != null) {
                    RealmManager.get(serverLevel.getServer()).clearAnchorLocation(ownerUUID);
                }
                // Destroying the anchor triggers neighborChanged on this block -> drops this block
                // setRemoved will clean up the boss bar
                playCrack(level, anchorPos);
                if (be.placer != null) {
                    var p = serverLevel.getServer().getPlayerList().getPlayer(be.placer);
                    if (p != null) com.pocketdimensions.advancement.Milestones.reach(p, com.pocketdimensions.advancement.Milestones.SEVER_ANCHOR);
                }
                level.setBlock(anchorPos, Blocks.AIR.defaultBlockState(), 3);
                return;  // do not touch `be` after block removal
            }
        }

        // Visual level: the anchor's DAMAGE (cracks, sigil, heated runes) and our CHARGE (coils, lightning sets).
        // CHARGE is a saved block state, so after a restart the level matches it and no crack replays.
        int charge = chargeLevel(be.progressTicks, PocketDimensionsConfig.BREAKER_DURATION_TICKS.get());
        int shown = state.getValue(AnchorBreakerBlock.CHARGE);
        if (hasAnchor && (charge != shown || level.getGameTime() % 20 == 0)) {
            WorldAnchorBlock.setDamage(level, anchorPos, charge);
            if (charge != shown) {
                if (charge > shown && charge >= 2) playCrack(level, anchorPos);   // a new lightning set at 25/50/75 %
                level.setBlock(pos, state.setValue(AnchorBreakerBlock.CHARGE, charge), 3);
            }
        }
        // Tell clients when fuel runs out or returns (the siphon stream and motes show only while fueled)
        if (be.syncedFuel == null || be.syncedFuel != be.hasFuel()) {
            be.syncedFuel = be.hasFuel();
            level.sendBlockUpdated(pos, state, state, 3);
        }

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

        // --- Siege bar ---
        if (be.progressTicks > 0 || be.hasFuel()) {
            if (level.getGameTime() % 20 == 0) be.bar.update(serverLevel, pos, anchor, be.barState(serverLevel, anchor, defended));
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
        return new SiegeBarS2C(null, false, SiegeBarS2C.BREAKER, progressTicks, PocketDimensionsConfig.BREAKER_DURATION_TICKS.get(), rate,
                fuel + inventory.getItem(0).getCount(), com.pocketdimensions.PocketDimensionsServerConfig.ANCHOR_BREAKER_MAX_LAPIS.get(),
                wc != null ? wc.getDefenseLapis() : 0);
    }

    // -------------------------------------------------------------------------
    // Defender helpers
    // -------------------------------------------------------------------------

    private boolean isDefenderCoreActive(ServerLevel level, WorldAnchorBlockEntity anchor) {
        WorldCoreBlockEntity wc = findWorldCore(level, anchor);
        return wc != null && wc.hasDefenseFuel();
    }

    private void consumeDefenderFuel(ServerLevel level, WorldAnchorBlockEntity anchor) {
        WorldCoreBlockEntity wc = findWorldCore(level, anchor);
        if (wc != null) wc.consumeDefenseFuel();
    }

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
    public void setPlacer(UUID placer) {
        this.placer = placer;
        setChanged();
    }

    // NBT
    // -------------------------------------------------------------------------

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putInt("progress_ticks", progressTicks);
        if (placer != null) output.putString("placer", placer.toString());
        output.putInt("fuel", fuel);
        ItemStack slot = inventory.getItem(0);
        output.putInt("slot_lapis_count", slot.isEmpty() ? 0 : slot.getCount());
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        progressTicks = input.getIntOr("progress_ticks", 0);
        String placerId = input.getStringOr("placer", "");
        try { placer = placerId.isEmpty() ? null : UUID.fromString(placerId); } catch (IllegalArgumentException e) { placer = null; }
        fuel = input.getIntOr("fuel", 0);
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

    public int getProgressTicks() { return progressTicks; }

    /** The lightning reaches about 4.3 blocks from the black hole two blocks below. */
    @Override
    public AABB getRenderBoundingBox() {
        return new AABB(worldPosition).inflate(5);
    }
    public int getFuel() { return fuel; }
    public SimpleContainer getInventory() { return inventory; }

    // -------------------------------------------------------------------------
    // MenuProvider
    // -------------------------------------------------------------------------

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.pocketdimensions.anchor_breaker");
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
                    case 1 -> PocketDimensionsConfig.BREAKER_DURATION_TICKS.get();
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
}
