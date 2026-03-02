package com.pocketdimensions.blockentity;

import com.pocketdimensions.PocketDimensionsMod;
import com.pocketdimensions.PocketDimensionsConfig;
import com.pocketdimensions.init.ModBlockEntityTypes;
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
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.BossEvent;
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

import java.util.HashSet;
import java.util.Set;
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
    };

    /** Transient boss bar — not saved to NBT; recreated lazily after server restart. */
    private ServerBossEvent bossBar = null;

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
        int space = 64 - (slot.isEmpty() ? 0 : slot.getCount());
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

    public static void serverTick(Level level, BlockPos pos, BlockState state,
                                  WorldBreacherBlockEntity be) {
        if (!(level instanceof ServerLevel serverLevel)) return;

        // Check that WorldAnchor is directly below
        BlockPos anchorPos = pos.below();
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
                boolean wasDone = be.isBreachComplete();
                be.progressTicks = Math.min(be.progressTicks + 1,
                        PocketDimensionsConfig.BREACH_DURATION_TICKS.get());
                be.setChanged();
                // Sync to client the moment breach completes so the beacon beam appears immediately
                if (!wasDone && be.isBreachComplete()) {
                    level.sendBlockUpdated(pos, state, state, 3);
                }
            }

            // Every CORE_FUEL_BURN_TICKS: drain 1 attacker lapis; drain 1 defender lapis if active
            if (level.getGameTime() % PocketDimensionsConfig.CORE_FUEL_BURN_TICKS.get() == 0) {
                be.consumeOneFuel();
                if (defended) {
                    be.consumeDefenderFuel(serverLevel, anchor);
                }
            }
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

        // --- Boss bar management ---
        // Dismiss boss bar when breach is complete (beacon beam replaces it)
        if (be.isBreachComplete()) {
            if (be.bossBar != null) {
                be.bossBar.removeAllPlayers();
                be.bossBar = null;
            }
        } else if (be.progressTicks > 0 || be.hasFuel()) {
            // Lazy create
            if (be.bossBar == null) {
                be.bossBar = new ServerBossEvent(
                        buildBarName(be, defended),
                        pickColor(be, defended),
                        BossEvent.BossBarOverlay.PROGRESS);
            }

            // Every tick: update progress float
            int duration = PocketDimensionsConfig.BREACH_DURATION_TICKS.get();
            be.bossBar.setProgress(Math.min((float) be.progressTicks / duration, 1.0f));

            // Every 20 ticks: update name, color, player list
            if (level.getGameTime() % 20 == 0) {
                be.bossBar.setName(buildBarName(be, defended));
                be.bossBar.setColor(pickColor(be, defended));
                updateBossBarPlayers(be.bossBar, serverLevel, pos, anchor);
            }
        } else if (be.bossBar != null) {
            // No progress and no fuel — remove bar
            be.bossBar.removeAllPlayers();
            be.bossBar = null;
        }
    }

    /** Reset progress when the block is removed from the world. */
    @Override
    public void setRemoved() {
        if (bossBar != null) {
            bossBar.removeAllPlayers();
            bossBar = null;
        }
        progressTicks = 0;
        super.setRemoved();
    }

    // -------------------------------------------------------------------------
    // Boss bar helpers
    // -------------------------------------------------------------------------

    private static Component buildBarName(WorldBreacherBlockEntity be, boolean defended) {
        int duration = PocketDimensionsConfig.BREACH_DURATION_TICKS.get();
        int pct = (int) ((be.progressTicks / (double) duration) * 100);
        boolean complete = be.progressTicks >= duration;
        boolean paused = !be.hasFuel();

        StringBuilder sb = new StringBuilder("Breaching the Veil \u2014 ").append(pct).append("%");

        if (complete) {
            sb.append(" \u2014 The Veil is Torn!");
        } else if (paused) {
            sb.append(" \u2014 Dormant (starved of lapis)");
        } else {
            int remainingTicks = duration - be.progressTicks;
            int etaSeconds = defended
                    ? (remainingTicks * PocketDimensionsConfig.CORE_SLOW_FACTOR.get()) / 20
                    : remainingTicks / 20;
            sb.append(" \u2014 ").append(formatTime(etaSeconds));
        }

        if (defended && !complete && !paused) {
            sb.append(" \u2014 Warded");
        }

        return Component.literal(sb.toString());
    }

    private static BossEvent.BossBarColor pickColor(WorldBreacherBlockEntity be, boolean defended) {
        int duration = PocketDimensionsConfig.BREACH_DURATION_TICKS.get();
        if (be.progressTicks >= duration) return BossEvent.BossBarColor.GREEN;
        if (!be.hasFuel()) return BossEvent.BossBarColor.WHITE;
        if (defended) return BossEvent.BossBarColor.YELLOW;
        return BossEvent.BossBarColor.BLUE;
    }

    private static String formatTime(int totalSeconds) {
        if (totalSeconds <= 0) return "0s";
        int hours = totalSeconds / 3600;
        int minutes = (totalSeconds % 3600) / 60;
        int seconds = totalSeconds % 60;
        StringBuilder sb = new StringBuilder();
        if (hours > 0) sb.append(hours).append("h ");
        if (minutes > 0) sb.append(minutes).append("m ");
        sb.append(seconds).append("s");
        return sb.toString();
    }

    private static void updateBossBarPlayers(ServerBossEvent bar, ServerLevel level, BlockPos pos,
                                               @Nullable WorldAnchorBlockEntity anchor) {
        MinecraftServer server = level.getServer();
        double range = PocketDimensionsConfig.SIEGE_BOSSBAR_RANGE.get();

        // Players near the siege block in the overworld
        Set<ServerPlayer> eligible = new HashSet<>(
                server.getPlayerList().getPlayers().stream()
                        .filter(p -> p.level() == level && p.blockPosition().closerThan(pos, range))
                        .toList());

        // Players inside the linked realm plot
        if (anchor != null && anchor.getOwnerUUID() != null) {
            RealmManager rm = RealmManager.get(server);
            int[] bounds = rm.getRealmBounds(anchor.getOwnerUUID());
            ServerLevel realmLevel = server.getLevel(PocketDimensionsMod.REALM_DIM);
            if (realmLevel != null) {
                for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                    if (p.level() == realmLevel
                            && p.getX() >= bounds[0] && p.getX() < bounds[2]
                            && p.getZ() >= bounds[1] && p.getZ() < bounds[3]) {
                        eligible.add(p);
                    }
                }
            }
        }

        // Remove players who left, add players who entered
        Set<ServerPlayer> current = new HashSet<>(bar.getPlayers());
        for (ServerPlayer p : current) {
            if (!eligible.contains(p)) bar.removePlayer(p);
        }
        for (ServerPlayer p : eligible) {
            if (!current.contains(p)) bar.addPlayer(p);
        }
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
        ItemStack slot = inventory.getItem(0);
        output.putInt("slot_lapis_count", slot.isEmpty() ? 0 : slot.getCount());
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        progressTicks = input.getIntOr("progress_ticks", 0);
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
                        BlockPos anchorPos = worldPosition.below();
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
