package com.pocketdimensions.client.siegebar;

import com.mojang.blaze3d.platform.NativeImage;
import com.pocketdimensions.PocketDimensionsMod;
import com.pocketdimensions.network.SiegeBarS2C;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.BossHealthOverlay;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.minecraftforge.client.event.AddGuiOverlayLayersEvent;
import net.minecraftforge.client.gui.overlay.ForgeLayeredDraw;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The siege bars on screen: keeps each siege the server reports, and draws them in a HUD layer just above the vanilla
 * boss bars (below any that are showing). The look follows the viewer: Aurora Stones inside the realm, Rift Eye
 * anywhere else. The art comes from {@link SiegeBarArt} into one texture per bar; the game's font draws the text on it.
 */
public final class SiegeBarClient {

    private static final Identifier LAYER = Identifier.fromNamespaceAndPath(PocketDimensionsMod.MODID, "siege_bars");
    private static final String BREACH = "Breaching the Veil", BREAK = "Shattering the Anchor";
    private static final long STALE_MS = 5000, FRAME_MS = 33;
    private static final int GAP = 2;

    private record Entry(SiegeBarState state, long receivedTick, long receivedMs) {}

    private static final Map<UUID, Entry> BARS = new LinkedHashMap<>();
    private static final List<Slot> SLOTS = new ArrayList<>();
    private static Field bossEvents;
    private static boolean lookedForBossEvents;

    public static void register() {
        AddGuiOverlayLayersEvent.BUS.addListener(e -> e.getLayeredDraw()
                .addAbove(ForgeLayeredDraw.PRE_SLEEP_STACK, LAYER, ForgeLayeredDraw.BOSS_OVERLAY, SiegeBarClient::render));
    }

    /** A packet from the server (main thread). */
    public static void receive(SiegeBarS2C m) {
        if (m.remove()) { BARS.remove(m.id()); return; }
        Minecraft mc = Minecraft.getInstance();
        SiegeBarArt.Kind kind = m.kind() == SiegeBarS2C.BREAKER ? SiegeBarArt.Kind.BREAKER : SiegeBarArt.Kind.BREACHER;
        SiegeBarState state = new SiegeBarState(kind, m.progressTicks(), m.durationTicks(), m.rate(), m.siegeFuel(), m.siegeCap(), m.coreFuel(),
                m.siegeBurnt(), m.coreBurnt(), m.burnTicks());
        BARS.put(m.id(), new Entry(state, mc.level != null ? mc.level.getGameTime() : 0, System.currentTimeMillis()));
    }

    private static void render(GuiGraphics g, DeltaTracker dt) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) { BARS.clear(); return; }
        long now = System.currentTimeMillis();
        BARS.values().removeIf(e -> now - e.receivedMs() > STALE_MS);
        if (BARS.isEmpty() || mc.options.hideGui) return;

        Font font = mc.font;
        SiegeBarArt.Side side = mc.level.dimension() == PocketDimensionsMod.REALM_DIM ? SiegeBarArt.Side.CORE : SiegeBarArt.Side.ANCHOR;
        float partial = dt.getGameTimeDeltaPartialTick(false);
        int vanilla = vanillaBossBars(mc);
        int x = g.guiWidth() / 2 - SiegeBarArt.W / 2, y = vanilla == 0 ? 0 : 3 + vanilla * 19;
        int i = 0;
        for (Entry e : BARS.values()) {
            if (y + SiegeBarArt.H > g.guiHeight() / 2) break;               // like vanilla's bars, keep to the top of the screen
            SiegeBarState s = e.state();
            double since = mc.level.getGameTime() - e.receivedTick() + partial;
            String title = s.kind() == SiegeBarArt.Kind.BREAKER ? BREAK : BREACH;
            String pct = s.percentText(since), time = s.timeText(since);
            int fuel = side == SiegeBarArt.Side.CORE ? s.coreFuel() : s.siegeFuel();
            int cap = side == SiegeBarArt.Side.CORE ? Math.max(64, s.coreFuel()) : Math.max(1, s.siegeCap());
            String fuelText = fuel + " / " + cap;
            double fuelLeft = side == SiegeBarArt.Side.CORE ? s.coreFuelAt(since) : s.siegeFuelAt(since);
            SiegeBarArt.Params p = new SiegeBarArt.Params(side, s.kind(), s.mode(), s.progressAt(since), fuelLeft, cap, now / 1000.0,
                    width(font, title), width(font, pct), width(font, fuelText));

            Slot slot = slot(mc, i++);
            slot.paint(p, now);
            g.blit(RenderPipelines.GUI_TEXTURED, slot.id, x, y, 0, 0, SiegeBarArt.W, SiegeBarArt.H, SiegeBarArt.W, SiegeBarArt.H);

            SiegeBarArt.Layout l = SiegeBarArt.layout(p);
            int sh = SiegeBarArt.statShadow(side);
            text(g, font, title, x + l.titleX(), y + l.titleY(), SiegeBarArt.titleColor(side), SiegeBarArt.titleShadow(side));
            text(g, font, pct, x + l.pctX(), y + l.statY(), SiegeBarArt.pctColor(p), sh);
            text(g, font, time, x + l.timeX(), y + l.statY(), SiegeBarArt.timeColor(p), sh);
            text(g, font, fuelText, x + l.fuelX(), y + l.statY(), SiegeBarArt.fuelColor(), sh);
            y += SiegeBarArt.H + GAP;
        }
    }

    /** The font's width less the spacing after the last letter, which is how the art measures text. */
    private static int width(Font font, String s) { return Math.max(0, font.width(s) - 1); }

    private static void text(GuiGraphics g, Font font, String s, int x, int y, int color, int shadow) {
        g.drawString(font, s, x + 1, y + 1, shadow, false);
        g.drawString(font, s, x, y, color, false);
    }

    /** How many vanilla boss bars are showing, so ours stack below them (0 if the overlay cannot be read). */
    private static int vanillaBossBars(Minecraft mc) {
        try {
            if (!lookedForBossEvents) {
                lookedForBossEvents = true;
                for (Field f : BossHealthOverlay.class.getDeclaredFields()) {
                    if (Map.class.isAssignableFrom(f.getType())) { f.setAccessible(true); bossEvents = f; break; }
                }
            }
            return bossEvents == null ? 0 : ((Map<?, ?>) bossEvents.get(mc.gui.getBossOverlay())).size();
        } catch (ReflectiveOperationException | RuntimeException ex) {
            bossEvents = null;
            return 0;
        }
    }

    private static Slot slot(Minecraft mc, int i) {
        while (SLOTS.size() <= i) SLOTS.add(new Slot(mc, SLOTS.size()));
        return SLOTS.get(i);
    }

    /** One bar's texture, repainted at most ~30 times a second. */
    private static final class Slot {
        final Identifier id;
        final DynamicTexture tex;
        final int[] px = new int[SiegeBarArt.W * SiegeBarArt.H];
        long painted;

        Slot(Minecraft mc, int index) {
            id = Identifier.fromNamespaceAndPath(PocketDimensionsMod.MODID, "siege_bar_" + index);
            tex = new DynamicTexture(() -> "Siege bar " + index, SiegeBarArt.W, SiegeBarArt.H, true);
            mc.getTextureManager().register(id, tex);
        }

        void paint(SiegeBarArt.Params p, long now) {
            if (now - painted < FRAME_MS) return;
            painted = now;
            SiegeBarArt.draw(p, px);
            NativeImage img = tex.getPixels();
            if (img == null) return;
            for (int y = 0; y < SiegeBarArt.H; y++) for (int x = 0; x < SiegeBarArt.W; x++) img.setPixel(x, y, px[y * SiegeBarArt.W + x]);
            tex.upload();
        }
    }

    private SiegeBarClient() {}
}
