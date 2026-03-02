package com.pocketdimensions.client.screen;

import com.pocketdimensions.blockentity.WorldCoreBlockEntity;
import com.pocketdimensions.menu.WorldCoreMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraftforge.common.UsernameCache;

import java.util.UUID;

public class WorldCoreScreen extends AbstractContainerScreen<WorldCoreMenu> {

    private static final int TEXT_COLOR = 0x404040;

    public WorldCoreScreen(WorldCoreMenu menu, Inventory playerInv, Component title) {
        super(menu, playerInv, title);
        this.imageWidth = 176;
        this.imageHeight = 166;
    }

    @Override
    protected void init() {
        super.init();
        addRenderableWidget(Button.builder(Component.literal("Exit Realm"), btn -> {
            if (minecraft != null && minecraft.gameMode != null) {
                minecraft.gameMode.handleInventoryButtonClick(menu.containerId, 0);
            }
        }).bounds(leftPos + 112, topPos + 6, 56, 14).build());
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        renderVanillaBackground(g);

        // Realm info
        int textY = topPos + 8;
        g.drawString(font, "Owner: " + getOwnerName(), leftPos + 8, textY, TEXT_COLOR, false);

        textY += 12;
        g.drawString(font, "Age: " + computeAge(), leftPos + 8, textY, TEXT_COLOR, false);

        textY += 12;
        int siegeState = menu.getSiegeState();
        String statusStr = switch (siegeState) {
            case WorldCoreBlockEntity.STATE_BREACHING   -> "Under Siege";
            case WorldCoreBlockEntity.STATE_BREAKING    -> "Under Attack";
            case WorldCoreBlockEntity.STATE_ANCHOR_LOST -> "Anchor Lost";
            default                                     -> "Peaceful";
        };
        int statusColor = switch (siegeState) {
            case WorldCoreBlockEntity.STATE_BREACHING   -> 0xAA44AA;
            case WorldCoreBlockEntity.STATE_BREAKING    -> 0xCC4444;
            case WorldCoreBlockEntity.STATE_ANCHOR_LOST -> 0xCC4444;
            default                                     -> 0x44AA44;
        };
        g.drawString(font, "Status: ", leftPos + 8, textY, TEXT_COLOR, false);
        g.drawString(font, statusStr, leftPos + 8 + font.width("Status: "), textY, statusColor, false);

        // "Lapis Fuel" label next to slot
        g.drawString(font, "Lapis Fuel", leftPos + 80 - font.width("Lapis Fuel") / 2, topPos + 44, TEXT_COLOR, false);

        // Slot outline (slot at 80,55 in menu coords)
        renderSlotOutline(g, leftPos + 79, topPos + 54);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        // Suppress default title/inv label rendering — we draw our own layout
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        renderTooltip(g, mouseX, mouseY);
    }

    // -------------------------------------------------------------------------
    // Vanilla-style background rendering
    // -------------------------------------------------------------------------

    private void renderVanillaBackground(GuiGraphics g) {
        int x = leftPos;
        int y = topPos;
        int w = imageWidth;
        int h = imageHeight;

        // Main fill
        g.fill(x, y, x + w, y + h, 0xFFC6C6C6);

        // 3D border: light top/left, dark bottom/right
        g.fill(x, y, x + w - 1, y + 1, 0xFFFFFFFF);
        g.fill(x, y, x + 1, y + h - 1, 0xFFFFFFFF);
        g.fill(x + 1, y + h - 1, x + w, y + h, 0xFF555555);
        g.fill(x + w - 1, y + 1, x + w, y + h, 0xFF555555);

        // Player inventory slot outlines
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                renderSlotOutline(g, x + 7 + col * 18, y + 83 + row * 18);
            }
        }
        for (int col = 0; col < 9; col++) {
            renderSlotOutline(g, x + 7 + col * 18, y + 141);
        }
    }

    private void renderSlotOutline(GuiGraphics g, int x, int y) {
        // Vanilla slot: 18x18 sunken rectangle
        g.fill(x, y, x + 18, y + 1, 0xFF373737);       // top dark edge
        g.fill(x, y, x + 1, y + 18, 0xFF373737);       // left dark edge
        g.fill(x + 1, y + 17, x + 18, y + 18, 0xFFFFFFFF); // bottom light edge
        g.fill(x + 17, y + 1, x + 18, y + 18, 0xFFFFFFFF); // right light edge
        g.fill(x + 1, y + 1, x + 17, y + 17, 0xFF8B8B8B);  // inner background
    }

    // -------------------------------------------------------------------------
    // Info helpers
    // -------------------------------------------------------------------------

    private String getOwnerName() {
        if (minecraft != null && minecraft.level != null) {
            var be = minecraft.level.getBlockEntity(menu.getBlockPos());
            if (be instanceof WorldCoreBlockEntity wc && wc.getOwnerUUID() != null) {
                UUID owner = wc.getOwnerUUID();
                String name = UsernameCache.getLastKnownUsername(owner);
                if (name != null) return name;
                return owner.toString().substring(0, 8) + "...";
            }
        }
        return "Unknown";
    }

    private String computeAge() {
        long created = menu.getCreatedGameTime();
        long current = menu.getCurrentGameTime();
        if (created == 0) return "Unknown";
        long elapsed = current - created;
        if (elapsed < 0) elapsed = 0;

        long totalSeconds = elapsed / 20;
        long days = totalSeconds / 86400;
        long hours = (totalSeconds % 86400) / 3600;
        long minutes = (totalSeconds % 3600) / 60;

        if (days > 0) return days + "d " + hours + "h";
        if (hours > 0) return hours + "h " + minutes + "m";
        return minutes + "m";
    }
}
