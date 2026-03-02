package com.pocketdimensions.menu;

import com.pocketdimensions.blockentity.AnchorBreakerBlockEntity;
import com.pocketdimensions.blockentity.WorldBreacherBlockEntity;
import com.pocketdimensions.init.ModMenuTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
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
 * Shared server/client menu for WorldBreacher and AnchorBreaker blocks.
 * <p>
 * One lapis slot backed by the block entity's persistent inventory.
 * ContainerData syncs: progressTicks, durationTicks, defended (0/1).
 */
public class SiegeBlockMenu extends AbstractContainerMenu {

    private static final int DATA_COUNT = 3;
    private static final int SLOT_INPUT = 0;

    private final ContainerData data;
    private final @Nullable BlockEntity blockEntity;
    private final BlockPos pos;

    /** Server-side constructor for WorldBreacher. */
    public SiegeBlockMenu(int containerId, Inventory playerInv, WorldBreacherBlockEntity be) {
        super(ModMenuTypes.SIEGE_BLOCK.get(), containerId);
        this.blockEntity = be;
        this.pos = be.getBlockPos();
        this.data = be.createContainerData();
        addSlot(new Slot(be.getInventory(), 0, 80, 46) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.is(Items.LAPIS_LAZULI);
            }
        });
        addPlayerInventory(playerInv);
        addDataSlots(data);
    }

    /** Server-side constructor for AnchorBreaker. */
    public SiegeBlockMenu(int containerId, Inventory playerInv, AnchorBreakerBlockEntity be) {
        super(ModMenuTypes.SIEGE_BLOCK.get(), containerId);
        this.blockEntity = be;
        this.pos = be.getBlockPos();
        this.data = be.createContainerData();
        addSlot(new Slot(be.getInventory(), 0, 80, 46) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.is(Items.LAPIS_LAZULI);
            }
        });
        addPlayerInventory(playerInv);
        addDataSlots(data);
    }

    /** Client-side constructor (from IContainerFactory via network). */
    public SiegeBlockMenu(int containerId, Inventory playerInv, FriendlyByteBuf buf) {
        super(ModMenuTypes.SIEGE_BLOCK.get(), containerId);
        this.pos = buf != null ? buf.readBlockPos() : BlockPos.ZERO;
        this.blockEntity = playerInv.player.level().getBlockEntity(pos);
        SimpleContainer dummy = new SimpleContainer(1) {
            @Override
            public boolean canPlaceItem(int slot, ItemStack stack) {
                return stack.is(Items.LAPIS_LAZULI);
            }
        };
        var container = blockEntity instanceof WorldBreacherBlockEntity wb ? wb.getInventory()
                : blockEntity instanceof AnchorBreakerBlockEntity ab ? ab.getInventory()
                : dummy;
        addSlot(new Slot(container, 0, 80, 46) {
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
        return player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= 64.0;
    }

    // -------------------------------------------------------------------------
    // Data accessors (client-side reads from synced ContainerData)
    // -------------------------------------------------------------------------

    public int getProgressTicks() { return data.get(0); }
    public int getDurationTicks() { return data.get(1); }
    public boolean isDefended() { return data.get(2) != 0; }
}
