package com.pocketdimensions.client.screen;

import com.pocketdimensions.PocketDimensionsConfig;
import com.pocketdimensions.PocketDimensionsMod;
import com.pocketdimensions.manager.RealmRules;
import com.pocketdimensions.menu.WorldCoreMenu;
import com.pocketdimensions.network.ModNetworking;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.PlayerSkin;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The World Core's screen: a slab of the Geode Heart's own weathered rock (design/world-core-gui-mock.html). Nothing
 * sits on the stone; everything is carved into it or glows out of its runes.
 * <p>
 * Hierarchy, from loudest: the realm's name (double size, glowing); rune light for live state (siege colour);
 * gold-inlaid section headings; dark engraved text with a lit lower lip. Text sits on polished faces of the rock so
 * it reads. Tabs are three rune seals; the owner and managers see all three, everyone else only Overview.
 */
public class WorldCoreScreen extends AbstractContainerScreen<WorldCoreMenu> {

    private static Identifier tex(String n) { return Identifier.fromNamespaceAndPath(PocketDimensionsMod.MODID, "textures/gui/" + n + ".png"); }
    private static final Identifier SLAB = tex("core_slab"), POLISHED = tex("core_polished"), HOLLOW = tex("core_hollow"), TABLET = tex("core_tablet");
    private static final int W = WorldCoreMenu.GUI_W, H = WorldCoreMenu.GUI_H, TABLET_W = 204, TABLET_H = 126;

    // Engraving colours
    private static final int CARVE = 0xFF17151C, LIP = 0x61FFFFFF, LABEL = 0xFF3B3743, INLAY = 0xFFE7B24A, INLAY_LO = 0xFF4A3208;
    private static final int UNBIND = 0xFFB0281C, UNBIND_HOT = 0xFFFF6A5A, PALE = 0xFFECE8F2;
    /** Rune light per siege state (peace, breaching, breaking, anchor lost): bright, deep. */
    private static final int[][] ACCENT = {{0xFF8CEBFF, 0xFF3C82FF}, {0xFFFF8CE6, 0xFFC832B4}, {0xFFFFAA3C, 0xFFFF5028}, {0xFFA8A4B2, 0xFF5A5664}};

    private static final String[][] SIEGE_TEXT = {
        {"at peace", "The realm is at peace", "No siege block is on your anchor."},
        {"breaching", "A World Breacher is on your anchor", "It will open the realm to anyone. Lapis in the ward slows it."},
        {"breaking", "An Anchor Breaker is on your anchor", "It will destroy the anchor for good. Lapis in the ward slows it."},
        {"anchor lost", "The anchor is lost", "The realm's anchor was destroyed. Nothing new can enter."},
    };
    private static final String[][] GLYPHS = {{"XXXX", "X...", "XXX.", "...X", "XXXX"}, {".XX.", "X..X", ".XX.", "..X.", "XXX."},
        {"X.X.", "XXXX", "X.X.", "..X.", "..XX"}, {"XXX.", "..X.", "XXXX", ".X..", "XX.."}, {"X..X", "X.X.", "XX..", "X.X.", "X..X"}, {".X..", "XXX.", ".X.X", "...X", "..XX"}};
    private static final String[][] SEALS = {
        {"..XXXXX..", ".X.....X.", "X..XXX..X", "X.X.X.X.X", "X..XXX..X", ".X.....X.", "..XXXXX.."},   // eye: Overview
        {"..XXXXX..", ".X.....X.", "X..XXX..X", "X..X.X..X", "X..XXX..X", ".X..X..X.", "..XXXXX.."},   // key: Access
        {"..XXXXX..", ".X.....X.", "X.X.X.X.X", "X.XXXXX.X", "X.XXXXX.X", ".X.....X.", "..XXXXX.."},   // crown: Manage
    };
    private static final String[] CROWN = {"X....X....X", "XX..XXX..XX", "XXXXXXXXXXX", "X.........X", "XXXXXXXXXXX"};

    private enum Tab { OVERVIEW, ACCESS, MANAGE }
    private static final String[] TAB_NAMES = {"Overview", "Access", "Manage"};

    private Tab tab = Tab.OVERVIEW;
    private EditBox nameInput, realmNameInput;
    private boolean pickerOpen, warnOpen;
    private long warnOpenedAt;
    private int ledgerScroll, pickScroll;
    private String renamedFlash = "";
    private long renamedAt;

    /** A carved button: drawn and hit-tested by the screen itself, so it can live on the stone and inside overlays. */
    private record Btn(int x, int y, int w, int h, String label, boolean unbind, boolean enabled, Runnable action) {
        boolean hit(double mx, double my) { return mx >= x && mx < x + w && my >= y && my < y + h; }
    }

    public WorldCoreScreen(WorldCoreMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageWidth = W;
        this.imageHeight = H;
    }

    @Override
    protected void init() {
        super.init();
        nameInput = new EditBox(font, leftPos + 21, topPos + 76, 116, 12, Component.literal("Player name"));
        nameInput.setBordered(false); nameInput.setMaxLength(16); nameInput.setTextColor(0xFFF2EEFA);
        nameInput.setHint(Component.literal("Player name").withColor(0xFF8D8996));
        addRenderableWidget(nameInput);
        realmNameInput = new EditBox(font, leftPos + 21, topPos + 76, 146, 12, Component.literal("Realm name"));
        realmNameInput.setBordered(false); realmNameInput.setMaxLength(RealmRules.MAX_NAME); realmNameInput.setTextColor(0xFFF2EEFA);
        realmNameInput.setValue(menu.sync().name());
        realmNameInput.setHint(Component.literal("Name your realm").withColor(0xFF8D8996));
        addRenderableWidget(realmNameInput);
        setTab(tab);
    }

    private RealmRules.Role role() { return menu.role(); }
    private int accent() { return ACCENT[siege()][0]; }
    private int accentDeep() { return ACCENT[siege()][1]; }
    private int siege() { return Math.max(0, Math.min(3, menu.getSiegeState())); }

    private void setTab(Tab t) {
        if (t != Tab.OVERVIEW && !RealmRules.canManage(role())) t = Tab.OVERVIEW;
        tab = t;
        pickerOpen = false; warnOpen = false;
        menu.setOverviewOpen(t == Tab.OVERVIEW);
        nameInput.visible = t == Tab.ACCESS;
        realmNameInput.visible = t == Tab.MANAGE;
        if (t == Tab.MANAGE) realmNameInput.setValue(menu.sync().name());
        setFocused(null);
    }

    private void send(int action, String text, UUID target) {
        ModNetworking.getChannel().send(new ModNetworking.CoreActionC2S(menu.getBlockPos(), action, text, target),
                net.minecraftforge.network.PacketDistributor.SERVER.noArg());
    }

    private void click() {
        if (minecraft != null) minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }

    // =====================================================================================================
    // Drawing: the stone (renderBg, absolute coordinates)
    // =====================================================================================================

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        int x = leftPos, y = topPos;
        boolean overlay = pickerOpen || warnOpen;
        g.blit(RenderPipelines.GUI_TEXTURED, SLAB, x, y, 0, 0, W, H, W, H);
        float t = (System.currentTimeMillis() % 3_600_000L) / 1000f;

        drawGlints(g, x, y, t);
        drawSeals(g, x, y, overlay ? -1 : mouseX, mouseY);
        drawInscription(g, x + 14, y + 21, 224, 0, t);
        String name = RealmRules.displayName(menu.sync().name(), menu.sync().ownerName());
        g.pose().pushMatrix();
        g.pose().translate(x + W / 2f, y + 29);
        g.pose().scale(2f, 2f);
        glow(g, name, -font.width(name) / 2, 0);                       // level 1: the realm's name
        g.pose().popMatrix();
        glowCentered(g, SIEGE_TEXT[siege()][0], x + W / 2, y + 48);

        switch (tab) {
            case OVERVIEW -> drawOverview(g, x, y, t);
            case ACCESS -> drawAccess(g, x, y, overlay ? -1 : mouseX, mouseY);
            case MANAGE -> drawManage(g, x, y);
        }
        drawInscription(g, x + 14, y + H - 11, 224, 3, t);
        if (!overlay) for (Btn b : buttons()) drawButton(g, b, mouseX, mouseY);
    }

    private void drawOverview(GuiGraphics g, int x, int y, float t) {
        // Status
        polished(g, x + 14, y + 58, 170, 86);
        inlay(g, "Status", x + 20, y + 63);
        glow(g, SIEGE_TEXT[siege()][1], x + 20, y + 75);
        int ly = y + 86;
        for (FormattedCharSequence line : font.split(FormattedText.of(SIEGE_TEXT[siege()][2]), 158)) { carved(g, line, x + 20, ly); ly += 10; }
        ly = y + 108;
        UUID owner = menu.core() != null ? menu.core().getOwnerUUID() : null;
        label(g, "Owner", x + 20, ly);
        if (owner != null) face(g, owner, x + 58, ly - 1);
        carved(g, menu.sync().ownerName(), x + 70, ly);
        label(g, "Age", x + 20, ly + 12); carved(g, age(), x + 58, ly + 12);
        if (RealmRules.canManage(role())) { label(g, "Access", x + 20, ly + 24); carved(g, (menu.sync().allowed().size() + 1) + " players", x + 58, ly + 24); }

        // The ward: a crystal-lined hollow and a crystal vein
        polished(g, x + 190, y + 58, 48, 86);
        inlay(g, "Ward", x + 196, y + 63);
        drawHollow(g, x + 203, y + 74);
        int lapis = menu.getSlot(0).getItem().getCount();
        drawVein(g, x + 211, y + 100, Math.min(1f, lapis / 64f));
        long secs = (long) lapis * PocketDimensionsConfig.CORE_FUEL_BURN_TICKS.get() / 20;
        glowCentered(g, lapis == 0 ? "empty" : (secs / 60) + "m " + (secs % 60) + "s", x + 214, y + 134);

        // In the realm now
        List<ModNetworking.PlayerEntry> here = menu.sync().inRealm();
        polished(g, x + 14, y + 150, 224, 26);
        inlay(g, "In the realm now: " + here.size(), x + 20, y + 155);
        int px = x + 20;
        for (int i = 0; i < here.size(); i++) {
            ModNetworking.PlayerEntry p = here.get(i);
            int w = 13 + font.width(p.name());
            if (px + w > x + 232) { carved(g, "+" + (here.size() - i), px, y + 166); break; }
            face(g, p.uuid(), px, y + 165); carved(g, p.name(), px + 12, y + 166);
            px += w + 8;
        }

        // The player's inventory, in pockets carved into the stone
        for (int r = 0; r < 4; r++) for (int c = 0; c < 9; c++) {
            int sy = r < 3 ? WorldCoreMenu.INV_Y + r * 18 : WorldCoreMenu.HOTBAR_Y;
            recess(g, x + WorldCoreMenu.INV_X - 1 + c * 18, y + sy - 1, 18, 18);
        }
    }

    private void drawAccess(GuiGraphics g, int x, int y, int mx, int my) {
        polished(g, x + 14, y + 58, 224, 36);
        inlay(g, "Add a player", x + 20, y + 63);
        channel(g, x + 19, y + 73, 120, 14);

        polished(g, x + 14, y + 98, 224, 166);
        inlay(g, "Who may enter", x + 20, y + 103);
        recess(g, x + 20, y + 113, 212, 130);
        List<Row> rows = rows();
        g.enableScissor(x + 21, y + 114, x + 231, y + 242);
        for (int i = 0; i < rows.size(); i++) {
            Row r = rows.get(i);
            int ry = y + 114 + i * 14 - ledgerScroll;
            if (ry < y + 100 || ry > y + 244) continue;
            boolean hot = mx >= x + 21 && mx < x + 231 && my >= ry && my < ry + 14 && my >= y + 114 && my < y + 242;
            g.fill(x + 22, ry + 12, x + 230, ry + 13, 0x40000000);            // carved rule under each line
            g.fill(x + 22, ry + 13, x + 230, ry + 14, 0x30FFFFFF);
            face(g, r.uuid, x + 23, ry + 2);
            if (hot) glow(g, r.name, x + 36, ry + 3); else carved(g, r.name, x + 36, ry + 3);
            if (r.owner) g.drawString(font, "owner", x + 39 + font.width(r.name), ry + 3, 0xFF8A6512, false);
            drawCrown(g, x + 204, ry + 4, r.owner || r.manager, r.owner || !RealmRules.canCrown(role()) ? false : hot);
            if (!r.owner && RealmRules.canRemove(role(), r.manager)) drawX(g, x + 222, ry + 4, hot && mx >= x + 219);
        }
        g.disableScissor();
        int max = PocketDimensionsConfig.MAX_ALLOWED_PLAYERS.get();
        label(g, rows.size() + " of " + (max > 0 ? String.valueOf(max + 1) : "unlimited"), x + 20, y + 249);
        String key = "Crown: manager";
        label(g, key, x + 232 - font.width(key), y + 249);
    }

    private void drawManage(GuiGraphics g, int x, int y) {
        polished(g, x + 14, y + 58, 224, 44);
        inlay(g, "Realm name", x + 20, y + 63);
        channel(g, x + 19, y + 73, 150, 14);
        boolean flash = !renamedFlash.isEmpty() && System.currentTimeMillis() - renamedAt < 1800;
        if (flash) glow(g, renamedFlash, x + 20, y + 91); else label(g, "Carved above, for everyone at the core.", x + 20, y + 91);

        polished(g, x + 14, y + 106, 224, 26);
        inlay(g, "More to come", x + 20, y + 111);
        label(g, "Room for future realm settings.", x + 20, y + 122);

        if (RealmRules.canRelocate(role())) {
            polished(g, x + 14, y + 136, 224, 58);
            inlay(g, "Relocate realm", x + 20, y + 141, 0xFFFF7A66, 0xFF4A120C);
            int ly = y + 152;
            for (FormattedCharSequence line : font.split(FormattedText.of("Grow a new realm in an untouched place. Everything here is lost."), 210)) { carved(g, line, x + 20, ly); ly += 10; }
        } else {
            polished(g, x + 14, y + 136, 224, 24);
            label(g, "Only the owner can relocate the realm.", x + 20, y + 144);
        }
    }

    // =====================================================================================================
    // Drawing: overlays (renderLabels, relative coordinates, on their own stratum above everything)
    // =====================================================================================================

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        if (!pickerOpen && !warnOpen) return;
        g.nextStratum();
        int mx = mouseX - leftPos, my = mouseY - topPos;
        if (pickerOpen) drawPicker(g, mx, my);
        if (warnOpen) drawWarn(g);
        for (Btn b : buttons()) drawButton(g, new Btn(b.x - leftPos, b.y - topPos, b.w, b.h, b.label, b.unbind, b.enabled, b.action), mx, my);
    }

    private void drawPicker(GuiGraphics g, int mx, int my) {
        int px = 20, py = 70, pw = 212, ph = 188;
        for (int ty = 0; ty < ph; ty += 64) for (int tx = 0; tx < pw; tx += 64)
            g.blit(RenderPipelines.GUI_TEXTURED, HOLLOW, px + tx, py + ty, 0, 0, Math.min(64, pw - tx), Math.min(64, ph - ty), 64, 64);
        edges(g, px, py, pw, ph, 0x99000000, LIP);
        glow(g, "Players online now", px + 6, py + 6);
        List<ModNetworking.PlayerEntry> online = menu.sync().online();
        g.enableScissor(leftPos + px + 2, topPos + py + 18, leftPos + px + pw - 2, topPos + py + ph - 22);
        if (online.isEmpty()) g.drawString(font, "Everyone online already has access.", px + 6, py + 22, 0xFFA8A3B2, false);
        for (int i = 0; i < online.size(); i++) {
            ModNetworking.PlayerEntry p = online.get(i);
            int ry = py + 20 + i * 16 - pickScroll;
            face(g, p.uuid(), px + 6, ry + 3);
            g.drawString(font, p.name(), px + 19, ry + 5, PALE, true);
        }
        g.disableScissor();
    }

    private void drawWarn(GuiGraphics g) {
        g.fill(0, 0, W, H, 0x8C080610);
        int tx = (W - TABLET_W) / 2, ty = 70;
        g.blit(RenderPipelines.GUI_TEXTURED, TABLET, tx, ty, 0, 0, TABLET_W, TABLET_H, TABLET_W, TABLET_H);
        String name = RealmRules.displayName(menu.sync().name(), menu.sync().ownerName());
        glowColored(g, "Relocate " + name + "?", tx + 10, ty + 9, UNBIND_HOT, UNBIND);
        int ly = ty + 22;
        for (String s : new String[]{"A new realm grows somewhere else.", "This one is lost forever:", " - every block built or mined", " - every chest and what is in it", " - the lapis in the ward"}) { carved(g, s, tx + 10, ly); ly += 10; }
        label(g, "Everyone inside is sent back out.", tx + 10, ly + 2);
        label(g, "Your access list is kept.", tx + 10, ly + 12);
    }

    // =====================================================================================================
    // Buttons
    // =====================================================================================================

    private List<Btn> buttons() {
        List<Btn> out = new ArrayList<>();
        int x = leftPos, y = topPos;
        if (warnOpen) {
            int tx = x + (W - TABLET_W) / 2, ty = y + 70;
            int left = 3 - (int) ((System.currentTimeMillis() - warnOpenedAt) / 1000);
            out.add(new Btn(tx + 64, ty + TABLET_H - 20, 72, 14, "Keep this realm", false, true, () -> warnOpen = false));
            out.add(new Btn(tx + 140, ty + TABLET_H - 20, 56, 14, left > 0 ? "Relocate " + left : "Relocate", true, left <= 0, () -> {
                send(ModNetworking.CoreActionC2S.RELOCATE, "", new UUID(0, 0));
                warnOpen = false;
            }));
            return out;
        }
        if (pickerOpen) {
            List<ModNetworking.PlayerEntry> online = menu.sync().online();
            for (int i = 0; i < online.size(); i++) {
                ModNetworking.PlayerEntry p = online.get(i);
                int ry = y + 90 + i * 16 - pickScroll;
                if (ry < y + 88 || ry > y + 232) continue;
                out.add(new Btn(x + 196, ry + 1, 30, 14, "Add", false, true, () -> send(ModNetworking.CoreActionC2S.ADD, p.name(), p.uuid())));
            }
            out.add(new Btn(x + 194, y + 240, 34, 14, "Done", false, true, () -> pickerOpen = false));
            return out;
        }
        if (tab == Tab.ACCESS) {
            out.add(new Btn(x + 144, y + 73, 30, 14, "Add", false, true, this::addTyped));
            out.add(new Btn(x + 178, y + 73, 54, 14, "Online", false, true, () -> { pickerOpen = true; pickScroll = 0; nameInput.setFocused(false); }));
        } else if (tab == Tab.MANAGE) {
            out.add(new Btn(x + 174, y + 73, 58, 14, "Rename", false, true, () -> {
                send(ModNetworking.CoreActionC2S.RENAME, realmNameInput.getValue(), new UUID(0, 0));
                renamedFlash = "Carved."; renamedAt = System.currentTimeMillis();
            }));
            if (RealmRules.canRelocate(role())) out.add(new Btn(x + 20, y + 175, 60, 14, "Relocate", true, true, () -> { warnOpen = true; warnOpenedAt = System.currentTimeMillis(); }));
        }
        return out;
    }

    private void addTyped() {
        String n = nameInput.getValue().trim();
        if (n.isEmpty()) return;
        send(ModNetworking.CoreActionC2S.ADD, n, new UUID(0, 0));
        nameInput.setValue("");
    }

    private void drawButton(GuiGraphics g, Btn b, int mx, int my) {
        boolean hot = b.enabled && b.hit(mx, my);
        g.fill(b.x, b.y, b.x + b.w, b.y + b.h, hot ? 0x14000000 : 0x1AFFFFFF);
        edges(g, b.x, b.y, b.w, b.h, 0x47FFFFFF, 0x59000000);                // a raised rune stone
        int tx = b.x + (b.w - font.width(b.label)) / 2, ty = b.y + (b.h - 8) / 2 + 1;
        if (!b.enabled) g.drawString(font, b.label, tx, ty, 0xFF6A6672, false);
        else if (hot) glowColored(g, b.label, tx, ty, b.unbind ? UNBIND_HOT : accent(), b.unbind ? UNBIND : accentDeep());
        else carved(g, b.label, tx, ty);
    }

    // =====================================================================================================
    // Input
    // =====================================================================================================

    private record Row(UUID uuid, String name, boolean manager, boolean owner) {}

    private List<Row> rows() {
        List<Row> out = new ArrayList<>();
        UUID owner = menu.core() != null ? menu.core().getOwnerUUID() : null;
        if (owner != null) out.add(new Row(owner, menu.sync().ownerName(), false, true));
        for (ModNetworking.AccessEntry e : menu.sync().allowed()) out.add(new Row(e.uuid(), e.name(), e.manager(), false));
        return out;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mx = event.x(), my = event.y();
        if (event.button() == 0) {
            for (Btn b : buttons()) if (b.enabled && b.hit(mx, my)) { click(); b.action.run(); return true; }
            if (warnOpen || pickerOpen) return true;                      // overlays swallow other clicks
            // seals (tabs)
            int[][] seals = sealRects();
            for (int i = 0; i < seals.length; i++) if (seals[i] != null && mx >= seals[i][0] && mx < seals[i][0] + seals[i][2] && my >= seals[i][1] && my < seals[i][1] + 11) {
                if (tab.ordinal() != i) { click(); setTab(Tab.values()[i]); }
                return true;
            }
            if (tab == Tab.ACCESS && mx >= leftPos + 21 && mx < leftPos + 231 && my >= topPos + 114 && my < topPos + 242) {
                int i = (int) ((my - topPos - 114 + ledgerScroll) / 14);
                List<Row> rows = rows();
                if (i >= 0 && i < rows.size() && !rows.get(i).owner) {
                    Row r = rows.get(i);
                    if (mx >= leftPos + 202 && mx < leftPos + 217 && RealmRules.canCrown(role())) { click(); send(ModNetworking.CoreActionC2S.TOGGLE_MANAGER, "", r.uuid); return true; }
                    if (mx >= leftPos + 219 && RealmRules.canRemove(role(), r.manager)) { click(); send(ModNetworking.CoreActionC2S.REMOVE, "", r.uuid); return true; }
                }
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        if (pickerOpen) {
            int max = Math.max(0, menu.sync().online().size() * 16 - 140);
            pickScroll = (int) Math.max(0, Math.min(max, pickScroll - sy * 16));
            return true;
        }
        if (tab == Tab.ACCESS && my >= topPos + 114 && my < topPos + 242) {
            int max = Math.max(0, rows().size() * 14 - 128);
            ledgerScroll = (int) Math.max(0, Math.min(max, ledgerScroll - sy * 14));
            return true;
        }
        return super.mouseScrolled(mx, my, sx, sy);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (warnOpen || pickerOpen) {
            if (event.key() == 256) { warnOpen = false; pickerOpen = false; return true; }   // Escape closes the overlay first
            return true;
        }
        EditBox box = nameInput.isFocused() ? nameInput : realmNameInput.isFocused() ? realmNameInput : null;
        if (box != null) {
            if (event.key() == 256) { box.setFocused(false); return true; }
            if (event.key() == 257 || event.key() == 335) {                   // Enter
                if (box == nameInput) addTyped();
                else { send(ModNetworking.CoreActionC2S.RENAME, realmNameInput.getValue(), new UUID(0, 0)); renamedFlash = "Carved."; renamedAt = System.currentTimeMillis(); }
                return true;
            }
            return box.keyPressed(event) || true;                             // typing never closes the screen
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        if (nameInput.isFocused()) return nameInput.charTyped(event);
        if (realmNameInput.isFocused()) return realmNameInput.charTyped(event);
        return super.charTyped(event);
    }

    // =====================================================================================================
    // Carvings and rune light
    // =====================================================================================================

    /** Seal hit boxes {x, y, w} in screen coordinates; null for tabs this viewer can't open. */
    private int[][] sealRects() {
        boolean staff = RealmRules.canManage(role());
        int[] widths = new int[3]; int total = 0, n = 0;
        for (int i = 0; i < 3; i++) if (i == 0 || staff) { widths[i] = 12 + font.width(TAB_NAMES[i]); total += widths[i]; n++; }
        total += (n - 1) * 14;
        int sx = leftPos + (W - total) / 2;
        int[][] out = new int[3][];
        for (int i = 0; i < 3; i++) if (i == 0 || staff) { out[i] = new int[]{sx, topPos + 7, widths[i]}; sx += widths[i] + 14; }
        return out;
    }

    private void drawSeals(GuiGraphics g, int x, int y, int mx, int my) {
        int[][] rects = sealRects();
        for (int i = 0; i < 3; i++) {
            if (rects[i] == null) continue;
            boolean on = tab.ordinal() == i, hot = mx >= rects[i][0] && mx < rects[i][0] + rects[i][2] && my >= rects[i][1] && my < rects[i][1] + 11;
            String[] p = SEALS[i];
            for (int r = 0; r < p.length; r++) for (int c = 0; c < p[r].length(); c++) {
                if (p[r].charAt(c) != 'X') continue;
                int px = rects[i][0] + c, py = rects[i][1] + 1 + r;
                if (on) g.fill(px, py, px + 1, py + 1, accent());
                else { g.fill(px, py + 1, px + 1, py + 2, LIP); g.fill(px, py, px + 1, py + 1, hot ? accentDeep() : CARVE); }
            }
            if (on || hot) glow(g, TAB_NAMES[i], rects[i][0] + 12, rects[i][1] + 2); else carved(g, TAB_NAMES[i], rects[i][0] + 12, rects[i][1] + 2);
        }
    }

    /** A line of the anchors' runes carved into the slab, light flowing slowly through it. */
    private void drawInscription(GuiGraphics g, int x, int y, int w, int seed, float t) {
        int n = (w + 2) / 6, off = (w - n * 6 + 2) / 2, acc = accent() & 0xFFFFFF;
        for (int i = 0; i < n; i++) {
            String[] glyph = GLYPHS[(i * 5 + seed) % 6];
            double wave = 0.5 + 0.5 * Math.sin(t * 0.9 - i * 0.45 + seed);
            int a = (int) (255 * (0.25 + 0.75 * wave * wave));
            for (int r = 0; r < 5; r++) for (int c = 0; c < 4; c++) {
                if (glyph[r].charAt(c) != 'X') continue;
                int px = x + off + i * 6 + c, py = y + r;
                g.fill(px, py + 1, px + 1, py + 2, 0x38FFFFFF);
                g.fill(px, py, px + 1, py + 1, (a << 24) | acc);
            }
        }
    }

    /** Tiny crystals in the rock catching the light, each in its own time (mostly near the edges). */
    private void drawGlints(GuiGraphics g, int x, int y, float t) {
        int acc = accent() & 0xFFFFFF;
        for (int i = 0; i < 26; i++) {
            int gx = 4 + (int) (hash(i, 1) * (W - 8)), gy = 4 + (int) (hash(i, 2) * (H - 8));
            boolean inner = gx > 12 && gx < W - 12 && gy > 12 && gy < H - 12;
            if (inner && hash(i, 3) > 0.25) continue;
            double s = Math.max(0, Math.sin(t * 0.7 + i * 2.3));
            int a = (int) (255 * (0.15 + 0.85 * s * s * s));
            g.fill(x + gx, y + gy, x + gx + 1, y + gy + 1, (a << 24) | acc);
        }
    }

    private static double hash(int a, int b) {
        int n = a * 374761393 ^ b * 668265263;
        n = (n ^ (n >>> 13)) * 1274126177;
        return ((n ^ (n >>> 16)) >>> 0 & 0xFFFFFFFFL) / 4294967295.0;
    }

    /** The ward: a hollow in the rock lined with calcite and glowing crystal. */
    private void drawHollow(GuiGraphics g, int x, int y) {
        for (int py = 0; py < 22; py++) for (int px = 0; px < 22; px++) {
            double d = Math.hypot(px + 0.5 - 11, (py + 0.5 - 11) * 1.05);
            if (d > 11) continue;
            int c = d > 10 ? 0x40FFFFFF : d > 9 ? 0xFFDEDFE4 : d > 8 ? (hash(px, py) > 0.45 ? accent() : accentDeep()) : 0xE0100E16;
            g.fill(x + px, y + py, x + px + 1, y + py + 1, c);
        }
    }

    /** A crystal vein down the rock that fills with crystal as the ward gets lapis. */
    private void drawVein(GuiGraphics g, int x, int y, float fill) {
        edges(g, x, y, 6, 30, CARVE, LIP);
        g.fill(x + 1, y + 1, x + 5, y + 29, 0x8C0A080E);
        int h = Math.round(28 * fill);
        for (int py = 0; py < h; py++) for (int px = 0; px < 4; px++) {
            int yy = y + 28 - py;
            g.fill(x + 1 + px, yy, x + 2 + px, yy + 1, hash(px, py + 40) > 0.5 ? accent() : accentDeep());
        }
    }

    private void drawCrown(GuiGraphics g, int x, int y, boolean filled, boolean hot) {
        for (int r = 0; r < CROWN.length; r++) for (int c = 0; c < CROWN[r].length(); c++) {
            if (CROWN[r].charAt(c) != 'X') continue;
            if (filled) g.fill(x + c, y + r, x + c + 1, y + r + 1, r == 4 ? 0xFFF6D98A : 0xFFD6A23C);
            else { g.fill(x + c, y + r + 1, x + c + 1, y + r + 2, LIP); g.fill(x + c, y + r, x + c + 1, y + r + 1, hot ? accentDeep() : CARVE); }
        }
        if (filled) g.fill(x + 5, y + 3, x + 6, y + 4, accent());
    }

    /** The unbinding rune (remove): a small cross that burns red under the cursor. */
    private void drawX(GuiGraphics g, int x, int y, boolean hot) {
        int c = hot ? UNBIND_HOT : UNBIND;
        for (int i = 0; i < 5; i++) { g.fill(x + i, y + i, x + i + 1, y + i + 1, c); g.fill(x + 4 - i, y + i, x + 5 - i, y + i + 1, c); }
    }

    /** A polished face: the rock flowed smooth and flat here, so text reads against calm stone. */
    private void polished(GuiGraphics g, int x, int y, int w, int h) {
        for (int ty = 0; ty < h; ty += 64) for (int tx = 0; tx < w; tx += 64)
            g.blit(RenderPipelines.GUI_TEXTURED, POLISHED, x + tx, y + ty, 0, 0, Math.min(64, w - tx), Math.min(64, h - ty), 64, 64);
        edges(g, x, y, w, h, 0x47000000, 0x4DFFFFFF);
    }

    /** Sunk into the rock: dark upper-left edge, lit lower-right lip. */
    private void recess(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, 0x14000000);
        edges(g, x, y, w, h, CARVE, LIP);
        g.fill(x + 1, y + 1, x + w - 1, y + 2, 0x2E000000);
    }

    /** A carved channel for typing into. */
    private void channel(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, 0x8C14121A);
        edges(g, x, y, w, h, 0x99000000, LIP);
    }

    /** Top/left edge in one colour, bottom/right in another. */
    private static void edges(GuiGraphics g, int x, int y, int w, int h, int topLeft, int bottomRight) {
        g.fill(x, y, x + w, y + 1, topLeft); g.fill(x, y, x + 1, y + h, topLeft);
        g.fill(x, y + h - 1, x + w, y + h, bottomRight); g.fill(x + w - 1, y, x + w, y + h, bottomRight);
    }

    private void face(GuiGraphics g, UUID id, int x, int y) {
        edges(g, x - 1, y - 1, 10, 10, CARVE, LIP);                         // set in a carved niche
        PlayerInfo info = minecraft != null && minecraft.getConnection() != null ? minecraft.getConnection().getPlayerInfo(id) : null;
        PlayerSkin skin = info != null ? info.getSkin() : DefaultPlayerSkin.get(id);
        PlayerFaceRenderer.draw(g, skin, x, y, 8);
    }

    // level 4: engraved text (dark letters with a lit lower lip), labels a shade lighter
    private void carved(GuiGraphics g, String s, int x, int y) { g.drawString(font, s, x, y + 1, LIP, false); g.drawString(font, s, x, y, CARVE, false); }
    private void carved(GuiGraphics g, FormattedCharSequence s, int x, int y) { g.drawString(font, s, x, y + 1, LIP, false); g.drawString(font, s, x, y, CARVE, false); }
    private void label(GuiGraphics g, String s, int x, int y) { g.drawString(font, s, x, y + 1, LIP, false); g.drawString(font, s, x, y, LABEL, false); }

    // level 3: gold inlaid in the stone
    private void inlay(GuiGraphics g, String s, int x, int y) { inlay(g, s, x, y, INLAY, INLAY_LO); }
    private void inlay(GuiGraphics g, String s, int x, int y, int color, int shadow) {
        g.fill(x + 1, y + 3, x + 4, y + 6, shadow); g.fill(x, y + 2, x + 3, y + 5, color);
        g.drawString(font, s, x + 6, y + 1, shadow, false); g.drawString(font, s, x + 5, y, color, false);
    }

    // level 2 (and 1, scaled): rune light
    private void glow(GuiGraphics g, String s, int x, int y) { glowColored(g, s, x, y, accent(), accentDeep()); }
    private void glowCentered(GuiGraphics g, String s, int cx, int y) { glow(g, s, cx - font.width(s) / 2, y); }
    private void glowColored(GuiGraphics g, String s, int x, int y, int color, int deep) {
        int halo = (0x55 << 24) | (deep & 0xFFFFFF);
        g.drawString(font, s, x, y + 1, 0x99000000, false);
        g.drawString(font, s, x - 1, y, halo, false); g.drawString(font, s, x + 1, y, halo, false);
        g.drawString(font, s, x, y - 1, halo, false);
        g.drawString(font, s, x, y, color, false);
    }

    private String age() {
        long created = menu.getCreatedGameTime(), now = menu.getCurrentGameTime();
        if (created == 0) return "unknown";
        long secs = Math.max(0, now - created) / 20, days = secs / 86400, hours = (secs % 86400) / 3600, mins = (secs % 3600) / 60;
        if (days > 0) return days + "d " + hours + "h";
        if (hours > 0) return hours + "h " + mins + "m";
        return mins + "m";
    }
}
