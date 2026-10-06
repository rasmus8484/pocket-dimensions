package com.pocketdimensions.menu;

import com.pocketdimensions.blockentity.WorldCoreBlockEntity;
import com.pocketdimensions.event.RealmEventHandler;
import com.pocketdimensions.init.ModMenuTypes;
import com.pocketdimensions.manager.RealmManager;
import com.pocketdimensions.manager.RealmRules;
import com.pocketdimensions.network.ModNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
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
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * The World Core's menu. Anyone who reaches the core may open it; what they may do comes from their role
 * (RealmRules): visitors only see Overview and can put lapis into the ward but never take it out.
 * <p>
 * ContainerData syncs siegeState and the created/current game time. Everything else the screen shows (role, name,
 * access list, online players, who is in the realm) arrives as ModNetworking.CoreSyncS2C: on open, after every action,
 * and every two seconds while open.
 */
public class WorldCoreMenu extends AbstractContainerMenu {

    // Layout shared with WorldCoreScreen (GUI pixels from the slab's top left)
    public static final int GUI_W = 252, GUI_H = 270;
    public static final int LAPIS_X = 206, LAPIS_Y = 77;
    public static final int INV_X = 46, INV_Y = 182, HOTBAR_Y = 240;

    private static final int DATA_COUNT = 5;
    private static final int SLOT_INPUT = 0;

    private final ContainerData data;
    private final @Nullable WorldCoreBlockEntity blockEntity;
    private final BlockPos pos;
    private final Player player;

    /** Latest sync from the server (client side), or what the server last sent (server side). */
    private ModNetworking.CoreSyncS2C sync = new ModNetworking.CoreSyncS2C(RealmRules.Role.VISITOR.ordinal(), "", "", List.of(), List.of(), List.of());
    /** Client side: the screen hides the inventory (and the ward slot) on tabs other than Overview. */
    private boolean overviewOpen = true;
    private int syncTimer = 0;

    /** Server-side constructor (from MenuProvider). */
    public WorldCoreMenu(int containerId, Inventory playerInv, WorldCoreBlockEntity be) {
        super(ModMenuTypes.WORLD_CORE.get(), containerId);
        this.blockEntity = be;
        this.pos = be.getBlockPos();
        this.player = playerInv.player;
        this.data = be.createContainerData();
        if (player instanceof ServerPlayer sp) this.sync = ModNetworking.buildSync(sp, be);
        addSlots(be.getInventory(), playerInv);
    }

    /** Client-side constructor (from IContainerFactory via network). */
    public WorldCoreMenu(int containerId, Inventory playerInv, FriendlyByteBuf buf) {
        super(ModMenuTypes.WORLD_CORE.get(), containerId);
        this.pos = buf != null ? buf.readBlockPos() : BlockPos.ZERO;
        if (buf != null) this.sync = ModNetworking.CoreSyncS2C.decode(buf);
        this.player = playerInv.player;
        BlockEntity be = player.level().getBlockEntity(pos);
        this.blockEntity = be instanceof WorldCoreBlockEntity wc ? wc : null;
        this.data = new SimpleContainerData(DATA_COUNT);
        addSlots(blockEntity != null ? blockEntity.getInventory() : new SimpleContainer(1), playerInv);
    }

    private void addSlots(Container ward, Inventory playerInv) {
        addSlot(new Slot(ward, 0, LAPIS_X, LAPIS_Y) {
            @Override public boolean mayPlace(ItemStack stack) { return stack.is(Items.LAPIS_LAZULI); }
            @Override public boolean mayPickup(Player p) { return RealmRules.canTakeLapis(role()); }   // visitors give, never take
            @Override public boolean isActive() { return overviewOpen; }
        });
        for (int row = 0; row < 3; row++) for (int col = 0; col < 9; col++) {
            addSlot(new Slot(playerInv, col + (row + 1) * 9, INV_X + col * 18, INV_Y + row * 18) {
                @Override public boolean isActive() { return overviewOpen; }
            });
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(playerInv, col, INV_X + col * 18, HOTBAR_Y) {
                @Override public boolean isActive() { return overviewOpen; }
            });
        }
        addDataSlots(data);
    }

    /** The viewer's role, from the server's own records on the server and from the last sync on the client. */
    public RealmRules.Role role() {
        if (player instanceof ServerPlayer sp && blockEntity != null && blockEntity.getOwnerUUID() != null) {
            return RealmManager.get(((ServerLevel) sp.level()).getServer()).roleOf(blockEntity.getOwnerUUID(), sp.getUUID());
        }
        return RealmRules.Role.values()[Math.max(0, Math.min(2, sync.role()))];
    }

    /** Server side: keep the open screen fresh (who is in the realm, the access list) every two seconds. */
    @Override
    public void broadcastChanges() {
        super.broadcastChanges();
        if (player instanceof ServerPlayer sp && blockEntity != null && ++syncTimer >= 40) {
            syncTimer = 0;
            ModNetworking.sendSync(sp, blockEntity);
        }
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (id == 0 && player instanceof ServerPlayer) {       // kept for older clients: exit the realm
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
            if (!slot.mayPickup(player)) return ItemStack.EMPTY;
            if (!moveItemStackTo(slotStack, 1, 37, true)) return ItemStack.EMPTY;
        } else {
            if (!slotStack.is(Items.LAPIS_LAZULI) || !moveItemStackTo(slotStack, SLOT_INPUT, SLOT_INPUT + 1, false)) return ItemStack.EMPTY;
        }
        if (slotStack.isEmpty()) slot.set(ItemStack.EMPTY);
        else slot.setChanged();
        if (slotStack.getCount() == copy.getCount()) return ItemStack.EMPTY;
        slot.onTake(player, slotStack);
        return copy;
    }

    @Override
    public boolean stillValid(Player player) {
        if (blockEntity == null || blockEntity.isRemoved()) return false;
        return player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= 64.0;
    }

    // -------------------------------------------------------------------------
    // Data for the screen
    // -------------------------------------------------------------------------

    public BlockPos getBlockPos() { return pos; }
    public int getSiegeState() { return data.get(0); }
    public long getCreatedGameTime() { return Integer.toUnsignedLong(data.get(1)) | (Integer.toUnsignedLong(data.get(2)) << 32); }
    public long getCurrentGameTime() { return Integer.toUnsignedLong(data.get(3)) | (Integer.toUnsignedLong(data.get(4)) << 32); }
    public ModNetworking.CoreSyncS2C sync() { return sync; }
    public void applySync(ModNetworking.CoreSyncS2C s) { this.sync = s; }
    public void setOverviewOpen(boolean open) { this.overviewOpen = open; }
    public @Nullable WorldCoreBlockEntity core() { return blockEntity; }

    /** Server: what openMenu writes for the client constructor (position, then the first sync). */
    public static void writeExtraData(FriendlyByteBuf buf, WorldCoreBlockEntity be, ServerPlayer player) {
        buf.writeBlockPos(be.getBlockPos());
        ModNetworking.CoreSyncS2C.encode(ModNetworking.buildSync(player, be), buf);
    }
}
