package com.pocketdimensions.blockentity;

import com.pocketdimensions.event.RealmEventHandler;
import com.pocketdimensions.init.ModBlockEntityTypes;
import com.pocketdimensions.manager.RealmManager;
import com.pocketdimensions.menu.WorldCoreMenu;
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
import net.minecraft.server.level.ServerPlayer;
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
 * World Core block entity - indestructible permanent structure at realm center.
 * <p>
 * The WorldCore:
 * - Is never destroyed (block has max hardness/blast resistance).
 * - Provides guaranteed realm exit (shift + right-click).
 * - Accepts lapis fuel from the realm owner to slow active breach attempts (3x slowdown).
 * - Fuel is only consumed while an active breach is running.
 * - Emits a beacon beam whose color reflects current siege state:
 *   blue = normal, pink = World Breacher active, red = Anchor Breaker active or anchor destroyed.
 */
public class WorldCoreBlockEntity extends BlockEntity implements MenuProvider {

    // Siege state constants
    public static final int STATE_NORMAL      = 0;
    public static final int STATE_BREACHING   = 1;
    public static final int STATE_BREAKING    = 2;
    public static final int STATE_ANCHOR_LOST = 3;

    private static final int COLOR_NORMAL    = 0xFF4488FF;  // blue
    private static final int COLOR_BREACHING = 0xFFFF44FF;  // pink
    private static final int COLOR_BREAKING  = 0xFFFF4444;  // red

    private UUID ownerUUID = null;

    /** Legacy lapis fuel counter (from direct right-click fueling on older worlds). Drained before slot. */
    private int defenseFuel = 0;

    /** Current siege state - synced to client for beam colour. */
    private int siegeState = STATE_NORMAL;

    /** Persistent 1-slot inventory for lapis fuel (visible in the GUI). */
    private final SimpleContainer inventory = new SimpleContainer(1) {
        @Override
        public boolean canPlaceItem(int slot, ItemStack stack) {
            return stack.is(Items.LAPIS_LAZULI);
        }
    };

    public WorldCoreBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntityTypes.WORLD_CORE.get(), pos, state);
        inventory.addListener(c -> setChanged());
    }

    // -------------------------------------------------------------------------
    // Server tick (called from WorldCoreBlock.getTicker)
    // -------------------------------------------------------------------------

    public static void serverTick(Level level, BlockPos pos, BlockState state,
                                  WorldCoreBlockEntity be) {
        if (!(level instanceof ServerLevel serverLevel)) return;

        // Force-load the anchor chunk in overworld so siege blocks can tick
        if (level.getGameTime() % 200 == 0 && be.ownerUUID != null) {
            var optAnchor = RealmManager.get(serverLevel.getServer()).getAnchorLocation(be.ownerUUID);
            if (optAnchor.isPresent()) {
                var entry = optAnchor.get();
                ServerLevel anchorLevel = serverLevel.getServer().getLevel(entry.getKey());
                if (anchorLevel != null) {
                    ((ServerChunkCache) anchorLevel.getChunkSource())
                            .addTicketWithRadius(TicketType.PORTAL, new ChunkPos(entry.getValue()), 2);
                }
            }
        }

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
        if (optAnchor.isEmpty()) return STATE_ANCHOR_LOST;
        var anchorEntry = optAnchor.get();
        ServerLevel anchorLevel = server.getLevel(anchorEntry.getKey());
        if (anchorLevel == null) return STATE_NORMAL;
        BlockPos siegePos = anchorEntry.getValue().above();
        BlockEntity siegeBe = anchorLevel.getBlockEntity(siegePos);
        if (siegeBe instanceof AnchorBreakerBlockEntity ab && ab.hasFuel()) return STATE_BREAKING;
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
     * Right-click with lapis: insert into inventory slot (owner only).
     */
    public void tryInsertFuel(Player player, ItemStack stack, Level level) {
        if (ownerUUID == null || !player.getUUID().equals(ownerUUID)) {
            player.displayClientMessage(Component.literal(
                    "The core pulses faintly but refuses your hand. Only the realm's master may feed it."), false);
            return;
        }
        int inserted = insertLapis(stack.getCount());
        if (inserted > 0) {
            stack.shrink(inserted);
            setChanged();
            player.displayClientMessage(Component.literal(
                    "The core accepts your offering."), false);
        } else {
            player.displayClientMessage(Component.literal(
                    "The core's reserves are full."), false);
        }
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

    /** Called by siege block entities to check if the defender slowdown is active. */
    public boolean hasDefenseFuel() {
        return defenseFuel > 0 || !inventory.getItem(0).isEmpty();
    }

    /** Consume 1 lapis per breach tick-batch (called from siege block entities). */
    public void consumeDefenseFuel() {
        if (defenseFuel > 0) {
            defenseFuel--;
        } else {
            ItemStack slot = inventory.getItem(0);
            if (!slot.isEmpty()) slot.shrink(1);
        }
        setChanged();
    }

    /** Returns the ARGB beam colour for the current siege state. */
    public int getBeamColor() {
        return switch (siegeState) {
            case STATE_BREACHING   -> COLOR_BREACHING;
            case STATE_BREAKING    -> COLOR_BREAKING;
            case STATE_ANCHOR_LOST -> COLOR_BREAKING;
            default                -> COLOR_NORMAL;
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
        ItemStack slot = inventory.getItem(0);
        output.putInt("slot_lapis_count", slot.isEmpty() ? 0 : slot.getCount());
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        long msb = input.getLongOr("owner_uuid_msb", 0L);
        long lsb = input.getLongOr("owner_uuid_lsb", 0L);
        ownerUUID = (msb != 0 || lsb != 0) ? new UUID(msb, lsb) : null;
        defenseFuel = input.getIntOr("defense_fuel", 0);
        siegeState = input.getIntOr("siege_state", STATE_NORMAL);
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

    public UUID getOwnerUUID() { return ownerUUID; }
    public void setOwnerUUID(UUID uuid) { this.ownerUUID = uuid; setChanged(); }
    public int getSiegeState() { return siegeState; }
    public SimpleContainer getInventory() { return inventory; }

    // -------------------------------------------------------------------------
    // MenuProvider
    // -------------------------------------------------------------------------

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.pocketdimensions.world_core");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInv, Player player) {
        return new WorldCoreMenu(containerId, playerInv, this);
    }

    /** Creates a ContainerData that syncs siege state and realm creation time to the client. */
    public ContainerData createContainerData() {
        return new ContainerData() {
            @Override
            public int get(int index) {
                return switch (index) {
                    case 0 -> siegeState;
                    case 1 -> {
                        if (level instanceof ServerLevel sl) {
                            long created = RealmManager.get(sl.getServer()).getCreatedGameTime(ownerUUID);
                            yield (int) (created & 0xFFFFFFFFL);
                        }
                        yield 0;
                    }
                    case 2 -> {
                        if (level instanceof ServerLevel sl) {
                            long created = RealmManager.get(sl.getServer()).getCreatedGameTime(ownerUUID);
                            yield (int) (created >>> 32);
                        }
                        yield 0;
                    }
                    case 3 -> {
                        if (level != null) yield (int) (level.getGameTime() & 0xFFFFFFFFL);
                        yield 0;
                    }
                    case 4 -> {
                        if (level != null) yield (int) (level.getGameTime() >>> 32);
                        yield 0;
                    }
                    default -> 0;
                };
            }

            @Override
            public void set(int index, int value) {}

            @Override
            public int getCount() { return 5; }
        };
    }

    @Override
    public AABB getRenderBoundingBox() {
        return INFINITE_EXTENT_AABB;
    }
}
