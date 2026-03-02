package com.pocketdimensions.menu;

import com.pocketdimensions.blockentity.WorldCoreBlockEntity;
import com.pocketdimensions.event.RealmEventHandler;
import com.pocketdimensions.init.ModMenuTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
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
import org.jetbrains.annotations.Nullable;

/**
 * Server/client menu for the World Core block.
 * <p>
 * One lapis slot backed by the block entity's persistent inventory.
 * ContainerData syncs: siegeState, createdGameTimeLow/High, currentGameTimeLow/High.
 * clickMenuButton: 0 = exit realm.
 */
public class WorldCoreMenu extends AbstractContainerMenu {

    private static final int DATA_COUNT = 5;
    private static final int SLOT_INPUT = 0;

    private final ContainerData data;
    private final @Nullable WorldCoreBlockEntity blockEntity;
    private final BlockPos pos;

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
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(playerInv, col + (row + 1) * 9, 8 + col * 18, 84 + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(playerInv, col, 8 + col * 18, 142));
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
}
