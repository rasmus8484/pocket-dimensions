package com.pocketdimensions.menu;

import com.pocketdimensions.blockentity.WorldCoreBlockEntity;
import com.pocketdimensions.event.RealmEventHandler;
import com.pocketdimensions.init.ModMenuTypes;
import com.pocketdimensions.manager.RealmManager;
import com.pocketdimensions.network.ModNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.UsernameCache;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Server/client menu for the World Core block.
 * <p>
 * One lapis slot backed by the block entity's persistent inventory.
 * ContainerData syncs: siegeState, createdGameTimeLow/High, currentGameTimeLow/High.
 * clickMenuButton: 0 = exit realm.
 * <p>
 * Also carries allowlist and online player data for the access management panel.
 */
public class WorldCoreMenu extends AbstractContainerMenu {

    private static final int DATA_COUNT = 5;
    private static final int SLOT_INPUT = 0;

    private final ContainerData data;
    private final @Nullable WorldCoreBlockEntity blockEntity;
    private final BlockPos pos;

    /** Allowlist entries (synced via custom packet). */
    private List<ModNetworking.PlayerEntry> allowedPlayers = new ArrayList<>();
    /** Online non-owner, non-allowlisted players (synced via custom packet). */
    private List<ModNetworking.PlayerEntry> onlinePlayers = new ArrayList<>();

    /** Server-side constructor (from MenuProvider). */
    public WorldCoreMenu(int containerId, Inventory playerInv, WorldCoreBlockEntity be) {
        super(ModMenuTypes.WORLD_CORE.get(), containerId);
        this.blockEntity = be;
        this.pos = be.getBlockPos();
        this.data = be.createContainerData();
        addSlot(new Slot(be.getInventory(), 0, 80, 55) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.is(Items.LAPIS_LAZULI);
            }
        });
        addPlayerInventory(playerInv);
        addDataSlots(data);
    }

    /** Client-side constructor (from IContainerFactory via network). */
    public WorldCoreMenu(int containerId, Inventory playerInv, FriendlyByteBuf buf) {
        super(ModMenuTypes.WORLD_CORE.get(), containerId);
        this.pos = buf != null ? buf.readBlockPos() : BlockPos.ZERO;

        // Read allowlist data from extra buf written by server openMenu
        int allowedCount = buf != null ? buf.readVarInt() : 0;
        for (int i = 0; i < allowedCount; i++) {
            allowedPlayers.add(new ModNetworking.PlayerEntry(buf.readUUID(), buf.readUtf(64)));
        }
        int onlineCount = buf != null ? buf.readVarInt() : 0;
        for (int i = 0; i < onlineCount; i++) {
            onlinePlayers.add(new ModNetworking.PlayerEntry(buf.readUUID(), buf.readUtf(64)));
        }

        BlockEntity be = playerInv.player.level().getBlockEntity(pos);
        this.blockEntity = be instanceof WorldCoreBlockEntity wc ? wc : null;
        SimpleContainer dummy = new SimpleContainer(1) {
            @Override
            public boolean canPlaceItem(int slot, ItemStack stack) {
                return stack.is(Items.LAPIS_LAZULI);
            }
        };
        addSlot(new Slot(blockEntity != null ? blockEntity.getInventory() : dummy, 0, 80, 55) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.is(Items.LAPIS_LAZULI);
            }
        });
        this.data = new SimpleContainerData(DATA_COUNT);
        addPlayerInventory(playerInv);
        addDataSlots(data);
    }

    private void addPlayerInventory(Inventory playerInv) {
        // Shifted down by 50px from original (84→134, 142→192)
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(playerInv, col + (row + 1) * 9, 8 + col * 18, 134 + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(playerInv, col, 8 + col * 18, 192));
        }
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (id == 0 && player instanceof ServerPlayer) {
            RealmEventHandler.queueRealmExit(player.getUUID());
            player.closeContainer();
            return true;
        }
        return false;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (slot == null || !slot.hasItem()) return ItemStack.EMPTY;
        ItemStack slotStack = slot.getItem();
        ItemStack copy = slotStack.copy();

        if (index == SLOT_INPUT) {
            if (!moveItemStackTo(slotStack, 1, 37, true)) return ItemStack.EMPTY;
        } else {
            if (slotStack.is(Items.LAPIS_LAZULI)) {
                if (!moveItemStackTo(slotStack, SLOT_INPUT, SLOT_INPUT + 1, false)) return ItemStack.EMPTY;
            } else {
                return ItemStack.EMPTY;
            }
        }

        if (slotStack.isEmpty()) slot.set(ItemStack.EMPTY);
        else slot.setChanged();

        if (slotStack.getCount() == copy.getCount()) return ItemStack.EMPTY;
        slot.onTake(player, slotStack);
        return copy;
    }

    @Override
    public boolean stillValid(Player player) {
        if (blockEntity == null) return false;
        if (blockEntity.getOwnerUUID() == null) return false;
        if (!blockEntity.getOwnerUUID().equals(player.getUUID())) return false;
        return player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= 64.0;
    }

    // -------------------------------------------------------------------------
    // Data accessors (client-side reads from synced ContainerData)
    // -------------------------------------------------------------------------

    public BlockPos getBlockPos() { return pos; }
    public int getSiegeState() { return data.get(0); }

    public long getCreatedGameTime() {
        return Integer.toUnsignedLong(data.get(1)) | (Integer.toUnsignedLong(data.get(2)) << 32);
    }

    public long getCurrentGameTime() {
        return Integer.toUnsignedLong(data.get(3)) | (Integer.toUnsignedLong(data.get(4)) << 32);
    }

    // -------------------------------------------------------------------------
    // Allowlist data
    // -------------------------------------------------------------------------

    public List<ModNetworking.PlayerEntry> getAllowedPlayers() { return allowedPlayers; }
    public List<ModNetworking.PlayerEntry> getOnlinePlayers() { return onlinePlayers; }

    public void setAllowedPlayers(List<ModNetworking.PlayerEntry> list) { this.allowedPlayers = new ArrayList<>(list); }
    public void setOnlinePlayers(List<ModNetworking.PlayerEntry> list) { this.onlinePlayers = new ArrayList<>(list); }

    /** Write allowlist + online player data to the extra buf during server openMenu. */
    public static void writeExtraData(FriendlyByteBuf buf, WorldCoreBlockEntity be) {
        buf.writeBlockPos(be.getBlockPos());

        MinecraftServer server = be.getLevel().getServer();
        UUID ownerUUID = be.getOwnerUUID();
        if (server == null || ownerUUID == null) {
            buf.writeVarInt(0);
            buf.writeVarInt(0);
            return;
        }

        RealmManager mgr = RealmManager.get(server);
        List<UUID> allowed = mgr.getAllowedPlayers(ownerUUID);

        buf.writeVarInt(allowed.size());
        for (UUID uuid : allowed) {
            buf.writeUUID(uuid);
            buf.writeUtf(resolveName(uuid, server), 64);
        }

        List<ServerPlayer> onlinePlayers = server.getPlayerList().getPlayers();
        List<ServerPlayer> eligible = new ArrayList<>();
        for (ServerPlayer sp : onlinePlayers) {
            UUID spUUID = sp.getUUID();
            if (!spUUID.equals(ownerUUID) && !allowed.contains(spUUID)) {
                eligible.add(sp);
            }
        }
        buf.writeVarInt(eligible.size());
        for (ServerPlayer sp : eligible) {
            buf.writeUUID(sp.getUUID());
            buf.writeUtf(sp.getGameProfile().name(), 64);
        }
    }

    private static String resolveName(UUID uuid, MinecraftServer server) {
        ServerPlayer online = server.getPlayerList().getPlayer(uuid);
        if (online != null) return online.getGameProfile().name();
        String cached = UsernameCache.getLastKnownUsername(uuid);
        if (cached != null) return cached;
        return uuid.toString().substring(0, 8) + "...";
    }
}
