package com.pocketdimensions.client.screen;

import com.pocketdimensions.menu.SiegeBlockMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public class SiegeBlockScreen extends AbstractContainerScreen<SiegeBlockMenu> {

    private static final int TEXT_COLOR = 0x404040;

    public SiegeBlockScreen(SiegeBlockMenu menu, Inventory playerInv, Component title) {
        super(menu, playerInv, title);
        this.imageWidth = 176;
        this.imageHeight = 166;
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        renderVanillaBackground(g);

        int progress = menu.getProgressTicks();
        int duration = menu.getDurationTicks();
        boolean defended = menu.isDefended();
        boolean complete = duration > 0 && progress >= duration;
        float pct = duration > 0 ? Math.min((float) progress / duration, 1.0f) : 0f;

        // Status text
        int textY = topPos + 8;
        String statusStr;
        int statusColor;
        if (complete) {
            statusStr = "Complete";
            statusColor = 0x44AA44;
        } else if (defended && progress > 0) {
            statusStr = "Warded \u2014 " + (int) (pct * 100) + "%";
            statusColor = 0xAAAA00;
        } else if (progress > 0) {
            statusStr = "Active \u2014 " + (int) (pct * 100) + "%";
            statusColor = 0x4466CC;
        } else {
            statusStr = "Dormant";
            statusColor = 0x888888;
        }
        g.drawString(font, statusStr, leftPos + 8, textY, statusColor, false);

        // Progress bar
        int barX = leftPos + 8;
        int barY = topPos + 20;
        int barW = 160;
        int barH = 8;
        // Sunken bar frame
        g.fill(barX, barY, barX + barW, barY + 1, 0xFF373737);
        g.fill(barX, barY, barX + 1, barY + barH, 0xFF373737);
        g.fill(barX + 1, barY + barH - 1, barX + barW, barY + barH, 0xFFFFFFFF);
        g.fill(barX + barW - 1, barY + 1, barX + barW, barY + barH, 0xFFFFFFFF);
        g.fill(barX + 1, barY + 1, barX + barW - 1, barY + barH - 1, 0xFF8B8B8B);
        if (pct > 0) {
            int fillW = Math.max(1, (int) ((barW - 2) * pct));
            int barColor = complete ? 0xFF44AA44 : (defended ? 0xFFAAAA00 : 0xFF4466CC);
            g.fill(barX + 1, barY + 1, barX + 1 + fillW, barY + barH - 1, barColor);
        }

        // ETA
        int etaY = topPos + 31;
        if (!complete && progress > 0 && duration > 0) {
            int remaining = duration - progress;
            int etaSeconds = defended ? (remaining * 3) / 20 : remaining / 20;
            g.drawString(font, "ETA: ~" + formatTime(etaSeconds), leftPos + 8, etaY, TEXT_COLOR, false);
        }

        // "Lapis Fuel" label above slot
        g.drawString(font, "Lapis Fuel", leftPos + 80 - font.width("Lapis Fuel") / 2, topPos + 36, TEXT_COLOR, false);

        // Slot outline (slot at 80,46 in menu coords)
        renderSlotOutline(g, leftPos + 79, topPos + 45);

        // Warded indicator
        if (defended && !complete && progress > 0) {
            g.drawString(font, "Realm defenses active", leftPos + 8, topPos + 66, 0xAAAA00, false);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        // Suppress default title/inv label rendering
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
        g.fill(x, y, x + 18, y + 1, 0xFF373737);
        g.fill(x, y, x + 1, y + 18, 0xFF373737);
        g.fill(x + 1, y + 17, x + 18, y + 18, 0xFFFFFFFF);
        g.fill(x + 17, y + 1, x + 18, y + 18, 0xFFFFFFFF);
        g.fill(x + 1, y + 1, x + 17, y + 17, 0xFF8B8B8B);
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
}
