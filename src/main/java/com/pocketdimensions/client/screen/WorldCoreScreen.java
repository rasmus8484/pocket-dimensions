package com.pocketdimensions.client.screen;

import com.pocketdimensions.PocketDimensionsConfig;
import com.pocketdimensions.blockentity.WorldCoreBlockEntity;
import com.pocketdimensions.menu.WorldCoreMenu;
import com.pocketdimensions.network.ModNetworking;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraftforge.common.UsernameCache;
import net.minecraftforge.network.PacketDistributor;

import java.util.List;
import java.util.UUID;

public class WorldCoreScreen extends AbstractContainerScreen<WorldCoreMenu> {

    private static final int TEXT_COLOR = 0x404040;
    private static final int VISIBLE_ROWS = 3;
    private static final int ROW_HEIGHT = 12;

    private EditBox nameField;
    private int scrollOffset = 0;

    public WorldCoreScreen(WorldCoreMenu menu, Inventory playerInv, Component title) {
        super(menu, playerInv, title);
        this.imageWidth = 176;
        this.imageHeight = 216;
    }

    @Override
    protected void init() {
        super.init();

        // Exit Realm button (top right)
        addRenderableWidget(Button.builder(Component.literal("Exit Realm"), btn -> {
            if (minecraft != null && minecraft.gameMode != null) {
                minecraft.gameMode.handleInventoryButtonClick(menu.containerId, 0);
            }
        }).bounds(leftPos + 112, topPos + 6, 56, 14).build());

        // Player name input field
        nameField = new EditBox(font, leftPos + 8, topPos + 120, 120, 14, Component.literal("Player name"));
        nameField.setMaxLength(16);
        nameField.setHint(Component.literal("Player name..."));
        addWidget(nameField);

        // Add button
        addRenderableWidget(Button.builder(Component.literal("Add"), btn -> {
            String name = nameField.getValue().trim();
            if (!name.isEmpty()) {
                ModNetworking.getChannel().send(
                        new ModNetworking.AllowlistActionC2S(
                                menu.getBlockPos(),
                                ModNetworking.AllowlistActionC2S.ACTION_ADD,
                                name,
                                new UUID(0, 0)),
                        PacketDistributor.SERVER.noArg());
                nameField.setValue("");
            }
        }).bounds(leftPos + 132, topPos + 120, 36, 14).build());
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        // Let EditBox consume key presses when focused
        if (nameField != null && nameField.isFocused()) {
            if (event.key() == 256) { // Escape
                nameField.setFocused(false);
                return true;
            }
            return nameField.keyPressed(event) || super.keyPressed(event);
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        if (nameField != null && nameField.isFocused()) {
            return nameField.charTyped(event);
        }
        return super.charTyped(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        // Scroll the allowlist when hovering over the list area
        int listX = leftPos + 8;
        int listY = topPos + 82;
        int listW = 160;
        int listH = VISIBLE_ROWS * ROW_HEIGHT;
        if (mouseX >= listX && mouseX < listX + listW && mouseY >= listY && mouseY < listY + listH) {
            int maxScroll = Math.max(0, menu.getAllowedPlayers().size() - VISIBLE_ROWS);
            scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset - (int) scrollY));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean focused) {
        double mouseX = event.x();
        double mouseY = event.y();
        int button = event.button();

        // Handle clicks on online player names
        if (button == 0) {
            int onlineY = topPos + 136;
            List<ModNetworking.PlayerEntry> online = menu.getOnlinePlayers();
            if (mouseY >= onlineY && mouseY < onlineY + 10 && !online.isEmpty()) {
                int textX = leftPos + 8 + font.width("Online: ");
                for (ModNetworking.PlayerEntry entry : online) {
                    int nameWidth = font.width(entry.name());
                    if (mouseX >= textX && mouseX < textX + nameWidth) {
                        ModNetworking.getChannel().send(
                                new ModNetworking.AllowlistActionC2S(
                                        menu.getBlockPos(),
                                        ModNetworking.AllowlistActionC2S.ACTION_ADD,
                                        entry.name(),
                                        entry.uuid()),
                                PacketDistributor.SERVER.noArg());
                        return true;
                    }
                    textX += nameWidth + font.width(", ");
                }
            }

            // Handle clicks on remove buttons in the allowlist
            List<ModNetworking.PlayerEntry> allowed = menu.getAllowedPlayers();
            int listY = topPos + 82;
            for (int i = 0; i < VISIBLE_ROWS && (i + scrollOffset) < allowed.size(); i++) {
                int rowY = listY + i * ROW_HEIGHT;
                int removeX = leftPos + 156;
                if (mouseX >= removeX && mouseX < removeX + 12 && mouseY >= rowY && mouseY < rowY + ROW_HEIGHT) {
                    ModNetworking.PlayerEntry entry = allowed.get(i + scrollOffset);
                    ModNetworking.getChannel().send(
                            new ModNetworking.AllowlistActionC2S(
                                    menu.getBlockPos(),
                                    ModNetworking.AllowlistActionC2S.ACTION_REMOVE,
                                    entry.name(),
                                    entry.uuid()),
                            PacketDistributor.SERVER.noArg());
                    return true;
                }
            }
        }
        return super.mouseClicked(event, focused);
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

        // ---- Separator line ----
        g.fill(leftPos + 4, topPos + 68, leftPos + imageWidth - 4, topPos + 69, 0xFF999999);

        // ---- Access panel ----
        renderAccessPanel(g, mouseX, mouseY);

        // ---- EditBox ----
        nameField.render(g, mouseX, mouseY, 0);
    }

    private void renderAccessPanel(GuiGraphics g, int mouseX, int mouseY) {
        List<ModNetworking.PlayerEntry> allowed = menu.getAllowedPlayers();
        int max = PocketDimensionsConfig.MAX_ALLOWED_PLAYERS.get();

        // Header
        String header = "Realm Access (" + allowed.size();
        if (max > 0) header += "/" + max;
        header += ")";
        g.drawString(font, header, leftPos + 8, topPos + 72, TEXT_COLOR, false);

        // Clamp scroll
        int maxScroll = Math.max(0, allowed.size() - VISIBLE_ROWS);
        if (scrollOffset > maxScroll) scrollOffset = maxScroll;

        // Player list (3 visible rows)
        int listY = topPos + 82;
        // Sunken list background
        g.fill(leftPos + 6, listY - 1, leftPos + 170, listY + VISIBLE_ROWS * ROW_HEIGHT + 1, 0xFF373737);
        g.fill(leftPos + 7, listY, leftPos + 169, listY + VISIBLE_ROWS * ROW_HEIGHT, 0xFF8B8B8B);

        for (int i = 0; i < VISIBLE_ROWS && (i + scrollOffset) < allowed.size(); i++) {
            ModNetworking.PlayerEntry entry = allowed.get(i + scrollOffset);
            int rowY = listY + i * ROW_HEIGHT;

            // Player name
            g.drawString(font, entry.name(), leftPos + 10, rowY + 2, 0xFFFFFF, false);

            // Remove button [x]
            int removeX = leftPos + 156;
            boolean hovered = mouseX >= removeX && mouseX < removeX + 12 && mouseY >= rowY && mouseY < rowY + ROW_HEIGHT;
            g.drawString(font, "x", removeX + 3, rowY + 2, hovered ? 0xFF5555 : 0xCC4444, false);
        }

        // Scroll indicator
        if (allowed.size() > VISIBLE_ROWS) {
            int barHeight = VISIBLE_ROWS * ROW_HEIGHT;
            int thumbHeight = Math.max(4, barHeight * VISIBLE_ROWS / allowed.size());
            int thumbY = listY + (barHeight - thumbHeight) * scrollOffset / maxScroll;
            g.fill(leftPos + 167, listY, leftPos + 169, listY + barHeight, 0xFF555555);
            g.fill(leftPos + 167, thumbY, leftPos + 169, thumbY + thumbHeight, 0xFFAAAAAA);
        }

        // ---- Online picker ----
        List<ModNetworking.PlayerEntry> online = menu.getOnlinePlayers();
        if (!online.isEmpty()) {
            int onlineY = topPos + 136;
            g.drawString(font, "Online: ", leftPos + 8, onlineY, TEXT_COLOR, false);
            int textX = leftPos + 8 + font.width("Online: ");
            for (int i = 0; i < online.size(); i++) {
                ModNetworking.PlayerEntry entry = online.get(i);
                boolean hovered = mouseX >= textX && mouseX < textX + font.width(entry.name())
                        && mouseY >= onlineY && mouseY < onlineY + 10;
                g.drawString(font, entry.name(), textX, onlineY, hovered ? 0x55FF55 : 0x44AA44, false);
                textX += font.width(entry.name());
                if (i < online.size() - 1) {
                    g.drawString(font, ", ", textX, onlineY, TEXT_COLOR, false);
                    textX += font.width(", ");
                }
                // Don't overflow the panel
                if (textX > leftPos + imageWidth - 10) break;
            }
        }
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

        // Player inventory slot outlines (shifted down by 50px)
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                renderSlotOutline(g, x + 7 + col * 18, y + 133 + row * 18);
            }
        }
        for (int col = 0; col < 9; col++) {
            renderSlotOutline(g, x + 7 + col * 18, y + 191);
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
