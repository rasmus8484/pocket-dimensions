package com.pocketdimensions.client.siegebar;

import java.util.Arrays;

/**
 * The siege bars' pixel art, ported from the approved mock (design/siege-bar-mock.html): Rift Eye for the anchor's side
 * and Aurora Stones for the core's side. Draws one 256 x 56 frame into an ARGB buffer; the text is not drawn here (the
 * game's font draws it on top, at {@link #layout}), but the art leaves room for it and draws the shield and gem icons.
 * Pure Java, no Minecraft classes, so it can be tested.
 */
public final class SiegeBarArt {

    public static final int W = 256, H = 56, XC = 128;
    /** Row above the bar's top rail; the progress line is 5 rows from PROGRESS_Y, the fuel line 2 rows from FUEL_Y. */
    static final int YT = 19, YC = 25;
    public static final int PROGRESS_Y = YT + 2, FUEL_Y = YT + 8, LINE_X = 37, LINE_LEN = 182;
    static final int BAN0 = 7, BAN1 = 18, PLQ0 = 30, PLQ1 = 41;
    public static final int TITLE_Y = 10, STAT_Y = 32;

    public enum Side { ANCHOR, CORE }
    public enum Kind { BREACHER, BREAKER }
    public enum Mode { ACTIVE, WARDED, DORMANT }

    /**
     * What to draw. {@code t} is seconds (drives the slow motion); the widths are the game font's pixel widths of the
     * title, the percent and the lapis count, so the art can fit the banner and place its icons beside the text.
     */
    public record Params(Side side, Kind kind, Mode mode, double progress, int fuel, int cap, double t,
                         int titleW, int pctW, int fuelW) {}

    /** Where the game font should draw each piece of text. */
    public record Layout(int titleX, int titleY, int pctX, int timeX, int fuelX, int statY) {}

    public static Layout layout(Params p) {
        int pctX = XC - 66, timeX = pctX + p.pctW() + 7 + (p.mode() == Mode.WARDED ? 8 : 0);
        return new Layout(XC - (p.titleW() >> 1), TITLE_Y, pctX, timeX, XC + 66 - p.fuelW(), STAT_Y);
    }

    /** Half the banner's width: 64, or wider to fit a long title. */
    public static int bannerHalfWidth(int titleW) { return Math.max(64, (titleW >> 1) + 17); }

    /** Draws a frame into {@code argb} (length W * H, unpremultiplied ARGB, transparent where nothing is drawn). */
    public static void draw(Params p, int[] argb) {
        Painter painter = new Painter(p);
        if (p.side() == Side.ANCHOR) painter.riftEye(); else painter.auroraStones();
        painter.buf.export(argb);
    }

    // ---- text colours, ARGB ----
    public static int titleColor(Side side) { return side == Side.ANCHOR ? argb(G_HI) : argb(rgb(236, 234, 242)); }
    public static int titleShadow(Side side) { return side == Side.ANCHOR ? argb(rgb(40, 28, 10)) : argb(rgb(30, 26, 36)); }
    public static int statShadow(Side side) { return side == Side.ANCHOR ? argb(rgb(16, 14, 22)) : argb(rgb(24, 20, 30)); }
    public static int pctColor(Params p) { return argb(p.mode() == Mode.DORMANT ? rgb(170, 166, 180) : SIEGE[p.kind().ordinal()][HI]); }
    public static int timeColor(Params p) {
        if (p.mode() == Mode.WARDED) return argb(WARD);
        return argb(p.side() == Side.ANCHOR ? rgb(226, 222, 236) : rgb(236, 234, 242));
    }
    public static int fuelColor() { return argb(LAPIS[HI]); }

    // =====================================================================================================
    // colours: packed 0xRRGGBB
    // =====================================================================================================

    static int rgb(double r, double g, double b) {
        return (clamp(r) << 16) | (clamp(g) << 8) | clamp(b);
    }
    private static int clamp(double v) { return (int) Math.max(0, Math.min(255, Math.round(v))); }
    private static int argb(int rgb) { return 0xFF000000 | rgb; }
    static int R(int c) { return (c >> 16) & 255; }
    static int G(int c) { return (c >> 8) & 255; }
    static int B(int c) { return c & 255; }
    static int mix(int a, int b, double t) {
        return rgb(R(a) + (R(b) - R(a)) * t, G(a) + (G(b) - G(a)) * t, B(a) + (B(b) - B(a)) * t);
    }
    static int shade(int c, double k) { return rgb(R(c) * k, G(c) * k, B(c) * k); }
    static int desat(int c, double k) {
        double l = (R(c) + G(c) + B(c)) / 3.0;
        return rgb(R(c) + (l - R(c)) * k, G(c) + (l - G(c)) * k, B(c) + (l - B(c)) * k);
    }

    static double hash3(int x, int y, int z) {
        int n = x * 374761393 ^ y * 668265263 ^ z * 1274126177;
        n = (n ^ (n >>> 13)) * 1274126177;
        return ((n ^ (n >>> 16)) & 0xFFFFFFFFL) / 4294967295.0;
    }
    static int tone(int c, int x, int y, int s, double amt) { return shade(c, 1 - amt / 2 + hash3(x, y, s) * amt); }

    static final int LO = 0, MID = 1, HI = 2, BR = 3;
    static final int[][] SIEGE = {
            {rgb(170, 40, 160), rgb(255, 90, 220), rgb(255, 160, 240), rgb(255, 220, 250)},     // the Breacher: pink
            {rgb(170, 28, 14), rgb(250, 70, 32), rgb(255, 118, 62), rgb(255, 196, 150)},         // the Breaker: red-orange
    };
    static final int[] LAPIS = {rgb(28, 50, 140), rgb(55, 100, 230), rgb(130, 170, 255), rgb(205, 222, 255)};
    static final int[] CORE_BLUE = {rgb(40, 110, 230), rgb(120, 210, 255), rgb(150, 240, 255), rgb(220, 250, 255)};
    static final int WARD = rgb(140, 235, 255);
    static final int GOLD = rgb(214, 162, 60), G_HI = rgb(250, 226, 150), G_LO = rgb(150, 104, 36);
    static final int PLINTH = rgb(48, 50, 62);
    static final int ROCK = rgb(122, 117, 130), LICHEN = rgb(104, 120, 88), CALC = rgb(222, 224, 228);
    static final int AC_A = rgb(30, 120, 120), AC_B = rgb(70, 220, 190), AC_C = rgb(180, 255, 235);
    static final int OUTLINE = rgb(14, 12, 20);
    static final String[][] GLYPHS = {
            {"XXXX", "X...", "XXX.", "...X", "XXXX"}, {".XX.", "X..X", ".XX.", "..X.", "XXX."}, {"X.X.", "XXXX", "X.X.", "..X.", "..XX"},
            {"XXX.", "..X.", "XXXX", ".X..", "XX.."}, {"X..X", "X.X.", "XX..", "X.X.", "X..X"}, {".X..", "XXX.", ".X.X", "...X", "..XX"},
    };
    static final String[][] TINY = {{"XX.", ".X.", ".XX"}, {"X.X", ".X.", "X.X"}, {"XXX", "X..", "X.."}, {".X.", "XXX", ".X."}, {"X..", "XX.", "..X"}, {".XX", "X..", "XX."}};
    static final String[] GEM = {"..X..", ".XXX.", "XXXXX", ".XXX.", "..X.."};
    static final String[] SHIELD = {"XXXXX", "XXXXX", "XX.XX", ".XXX.", "..X.."};

    static int goldT(int x, int y) { return hash3(x + 11, y, 5) > 0.93 ? G_HI : tone(GOLD, x, y, 9, 0.14); }
    static int rock(int x, int y, int base, int seed) {
        double h = hash3(x, y, seed), bl = hash3(x >> 2, y >> 2, seed + 1), m = hash3(x >> 3, y >> 3, seed + 2);
        return shade(base, (0.86 + h * 0.14) * (bl > 0.8 ? 0.88 : 1) * (0.94 + m * 0.1));
    }
    static int rockAt(int x, int y, int seed) { return rock(x, y, ROCK, seed); }

    // =====================================================================================================
    // buffers
    // =====================================================================================================

    /** The frame: premultiplied colour and alpha, so glows can light transparent pixels as well as solid ones. */
    static final class Buf {
        final float[] d = new float[W * H * 4];
        void set(int x, int y, int c) {
            if (x < 0 || y < 0 || x >= W || y >= H) return;
            int i = (y * W + x) * 4;
            d[i] = R(c); d[i + 1] = G(c); d[i + 2] = B(c); d[i + 3] = 1;
        }
        void add(int x, int y, int c, double k) {
            if (x < 0 || y < 0 || x >= W || y >= H) return;
            int i = (y * W + x) * 4;
            d[i] = (float) Math.min(255, d[i] + R(c) * k);
            d[i + 1] = (float) Math.min(255, d[i + 1] + G(c) * k);
            d[i + 2] = (float) Math.min(255, d[i + 2] + B(c) * k);
            d[i + 3] = (float) Math.min(1, d[i + 3] + k);
        }
        int get(int x, int y) {
            int i = (y * W + x) * 4;
            float a = d[i + 3];
            return a <= 0 ? 0 : rgb(d[i] / a, d[i + 1] / a, d[i + 2] / a);
        }
        void export(int[] out) {
            for (int p = 0; p < W * H; p++) {
                int i = p * 4;
                float a = d[i + 3];
                out[p] = a <= 0 ? 0 : (clamp(a * 255) << 24) | rgb(d[i] / a, d[i + 1] / a, d[i + 2] / a);
            }
        }
    }

    /** One drawing pass whose pieces get a shared 1 px dark outline when composited, like a sprite. */
    static final class Layer {
        static final int EMPTY = -1;
        final int[] c = new int[W * H];
        int[] gx = new int[64], gy = new int[64], gc = new int[64];
        float[] gk = new float[64];
        int glows = 0;
        Layer() { Arrays.fill(c, EMPTY); }
        void set(int x, int y, int col) { if (x >= 0 && y >= 0 && x < W && y < H) c[y * W + x] = col; }
        boolean has(int x, int y) { return x >= 0 && y >= 0 && x < W && y < H && c[y * W + x] != EMPTY; }
        void sym(int x, int y, int col) { set(x, y, col); set(255 - x, y, col); }
        void glow(int x, int y, int col, double k) {
            if (glows == gx.length) { int n = glows * 2; gx = Arrays.copyOf(gx, n); gy = Arrays.copyOf(gy, n); gc = Arrays.copyOf(gc, n); gk = Arrays.copyOf(gk, n); }
            gx[glows] = x; gy[glows] = y; gc[glows] = col; gk[glows] = (float) k; glows++;
        }
        void symGlow(int x, int y, int col, double k) { glow(x, y, col, k); glow(255 - x, y, col, k); }
    }

    private static final int[][] AROUND = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {-1, -1}, {1, -1}, {-1, 1}};

    static void finish(Layer L, Buf b) {
        for (int y = 0; y < H; y++) for (int x = 0; x < W; x++)
            if (!L.has(x, y) && (L.has(x - 1, y) || L.has(x + 1, y) || L.has(x, y - 1) || L.has(x, y + 1))) b.set(x, y, OUTLINE);
        for (int i = 0; i < W * H; i++) if (L.c[i] != Layer.EMPTY) b.set(i % W, i / W, L.c[i]);
        blendGlows(L, b);
    }
    static void blendGlows(Layer L, Buf b) {
        for (int g = 0; g < L.glows; g++) for (int[] o : AROUND) {
            double k = o[0] != 0 && o[1] != 0 ? L.gk[g] * 0.5 : L.gk[g];
            b.add(L.gx[g] + o[0], L.gy[g] + o[1], L.gc[g], k);
        }
    }

    interface ColorAt { int at(int x, int y, int r); }
    interface ColorXY { int at(int x, int y); }

    // =====================================================================================================
    // the painter: one frame
    // =====================================================================================================

    static final class Painter {
        final Params p;
        final Buf buf = new Buf();
        final double t;
        final boolean live;
        final int[] pal;

        Painter(Params p) {
            this.p = p;
            this.t = p.t();
            this.live = p.mode() != Mode.DORMANT;
            int[] s = SIEGE[p.kind().ordinal()];
            this.pal = live ? s : new int[]{dull(s[LO]), dull(s[MID]), dull(s[HI]), dull(s[BR])};
        }
        private static int dull(int c) { return shade(desat(c, 0.85), 0.5); }

        int lit(int i, int r, int rows, int fw) {
            int c = r == 0 ? pal[HI] : r == rows - 1 ? pal[LO] : pal[MID];
            if (!live) return c;
            c = shade(c, 0.84 + 0.16 * Math.sin(t * 1.6 - i * 0.09));
            if (i >= fw - 1) c = mix(c, pal[BR], 0.75); else if (i == fw - 2) c = mix(c, pal[BR], 0.35);
            return c;
        }
        int runeCol() { return live ? shade(rgb(140, 235, 255), 0.86 + 0.14 * Math.sin(t * 1.2)) : rgb(70, 96, 120); }
        int[] ringPal(double mixP) {
            double k = Math.min(1, mixP);
            return new int[]{mix(CORE_BLUE[LO], pal[LO], k), mix(CORE_BLUE[MID], pal[MID], k), mix(CORE_BLUE[HI], pal[HI], k), mix(CORE_BLUE[BR], pal[BR], k)};
        }
        int bannerHalf() { return bannerHalfWidth(p.titleW()); }
        int banW(int y) { int h = bannerHalf(); return y == BAN0 ? h - 3 : y == BAN0 + 1 ? h - 1 : h; }
        static int plqW(int y) { return (int) Math.round(80 - (y - PLQ0) * 1.1) - (y == PLQ1 ? 2 : y == PLQ1 - 1 ? 1 : 0); }

        static int[][] segments(int len, int cap) {
            int avail = len - (cap - 1), x = 0;
            int[][] out = new int[cap][];
            for (int i = 0; i < cap; i++) {
                int w = (int) Math.floor((double) avail * (i + 1) / cap) - (int) Math.floor((double) avail * i / cap);
                out[i] = new int[]{x, w};
                x += w + 1;
            }
            return out;
        }
        static int lapisCol(int x, int y, int r) {
            int c = r == 0 ? mix(LAPIS[MID], LAPIS[HI], 0.6) : mix(LAPIS[LO], LAPIS[MID], 0.45);
            if (hash3(x, y, 71) > 0.86) c = mix(c, LAPIS[HI], 0.6);
            return c;
        }

        /** Both lines, full width: progress (5 rows) above, fuel (2 rows) below. Returns the lit width. */
        int lines(Layer L, ColorAt emptyP, ColorAt emptyF, ColorXY gapF, int fuel, int cap) {
            int n = LINE_LEN, fw = (int) Math.round(n * p.progress());
            int[][] sg = cap <= 16 ? segments(n, cap) : null;
            int ft = (int) Math.round((double) n * fuel / cap);
            for (int k = 0; k < n; k++) {
                int x = LINE_X + k;
                for (int r = 0; r < 5; r++) { int y = PROGRESS_Y + r; L.set(x, y, k < fw ? lit(k, r, 5, fw) : emptyP.at(x, y, r)); }
                int slot = -1;
                boolean filled;
                if (sg != null) {
                    for (int s = 0; s < sg.length; s++) if (k >= sg[s][0] && k < sg[s][0] + sg[s][1]) { slot = s; break; }
                    if (slot < 0) { for (int r = 0; r < 2; r++) L.set(x, FUEL_Y + r, gapF.at(x, FUEL_Y + r)); continue; }
                    filled = slot < fuel;
                } else filled = k < ft;
                for (int r = 0; r < 2; r++) {
                    int y = FUEL_Y + r, c = filled ? lapisCol(x, y, r) : emptyF.at(x, y, r);
                    if (filled && sg != null && r == 0 && k == sg[slot][0] + (sg[slot][1] >> 1) && hash3(slot, (int) Math.floor(t * 0.8), 7) > 0.75) c = LAPIS[BR];
                    L.set(x, y, c);
                }
            }
            if (live) for (int k = 0; k < fw; k++) L.glow(LINE_X + k, YT + 4, pal[MID], (0.1 + 0.04 * Math.sin(t * 1.6 - k * 0.09)) * 0.6);
            return fw;
        }

        void chevrons(Layer L, ColorXY inside, ColorXY edgeTop, ColorXY edgeBottom) {
            for (int x = 26; x <= 36; x++) for (int y = YT - 3; y <= YT + 12; y++) {
                int a = x - 26;
                if (Math.abs(y + 0.5 - YC) > a * 0.62 + 0.6) continue;
                boolean e = Math.abs(y + 0.5 - YC) > a * 0.62 - 0.6;
                L.sym(x, y, e ? (y + 0.5 < YC ? edgeTop.at(x, y) : edgeBottom.at(x, y)) : inside.at(x, y));
            }
        }

        void blackHole(Layer L, double cx, double cy, double r, int[] ring, double tt, double rw) {
            int R = (int) Math.ceil(r + rw + 1);
            for (int y = (int) Math.floor(cy - R); y <= cy + R; y++) for (int x = (int) Math.floor(cx - R); x <= cx + R; x++) {
                double dx = x + 0.5 - cx, dy = y + 0.5 - cy, d = Math.hypot(dx, dy);
                if (d <= r) L.set(x, y, d > r - 1 ? rgb(26, 14, 34) : rgb(6, 4, 10));
                else if (d <= r + rw) {
                    double k = 0.5 + 0.5 * Math.cos(Math.atan2(dy, dx) - tt * 0.9);
                    L.set(x, y, k > 0.8 ? mix(ring[HI], ring[BR], (k - 0.8) * 5) : mix(ring[LO], ring[HI], k / 0.8));
                    if (k > 0.75 && live) L.glow(x, y, ring[HI], 0.1);
                }
            }
        }

        void glyph(Layer L, String[] g, int x, int y, int col) {
            for (int r = 0; r < g.length; r++) for (int c = 0; c < g[r].length(); c++) if (g[r].charAt(c) == 'X') L.set(x + c, y + r, col);
        }
        void gem(int x, int y) {
            for (int r = 0; r < 5; r++) for (int c = 0; c < 5; c++)
                if (GEM[r].charAt(c) == 'X') buf.set(x + c, y + r, r + c < 4 ? LAPIS[HI] : r + c > 4 ? LAPIS[LO] : LAPIS[MID]);
            buf.set(x + 1, y + 1, LAPIS[BR]);
        }

        /** The plaque's icons: the ward shield before the time (while warded) and a gem before the lapis count. */
        void statIcons() {
            Layout l = layout(p);
            if (p.mode() == Mode.WARDED) {
                int x = l.timeX() - 8;
                for (int r = 0; r < 5; r++) for (int c = 0; c < 5; c++) if (SHIELD[r].charAt(c) == 'X') {
                    buf.add(x + c, STAT_Y + 1 + r, WARD, 0.1);
                    buf.set(x + c, STAT_Y + 2 + r, r < 2 && c < 2 ? rgb(230, 250, 255) : WARD);
                }
            }
            gem(l.fuelX() - 8, STAT_Y + 2);
            gem(31 - 2, YC - 2);
            gem(255 - 31 - 2, YC - 2);
        }

        /** The World Core's ward: a veined blue force field over the progress line only. */
        void wardField() {
            if (p.mode() != Mode.WARDED) return;
            final int x0 = 33, x1 = 222, y0 = YT + 1, y1 = YT + 7, CELL = 6;
            double pulse = 0.85 + 0.15 * Math.sin(t * 1.1);
            for (int y = y0; y <= y1; y++) for (int x = x0; x <= x1; x++) {
                if (!inField(x, y, x0, x1, y0, y1)) continue;
                int gxx = Math.floorDiv(x, CELL), gyy = Math.floorDiv(y, CELL);
                double f1 = 1e9, f2 = 1e9;
                for (int j = -1; j <= 1; j++) for (int i = -1; i <= 1; i++) {
                    int cx = gxx + i, cy = gyy + j;
                    double ph = hash3(cx, cy, 201) * 6.28;
                    double fx = (cx + 0.2 + 0.6 * hash3(cx, cy, 202) + 0.18 * Math.sin(t * 0.5 + ph)) * CELL;
                    double fy = (cy + 0.2 + 0.6 * hash3(cx, cy, 203) + 0.18 * Math.cos(t * 0.4 + ph)) * CELL;
                    double d = Math.hypot(x + 0.5 - fx, (y + 0.5 - fy) * 1.6);
                    if (d < f1) { f2 = f1; f1 = d; } else if (d < f2) f2 = d;
                }
                double vein = Math.max(0, 1 - (f2 - f1) / 1.3);
                boolean rim = !inField(x - 1, y, x0, x1, y0, y1) || !inField(x + 1, y, x0, x1, y0, y1) || !inField(x, y - 1, x0, x1, y0, y1) || !inField(x, y + 1, x0, x1, y0, y1);
                int c = mix(buf.get(x, y), rgb(80, 160, 255), 0.14 * pulse);
                if (vein > 0.35) c = mix(c, rgb(150, 225, 255), 0.6 * pulse * (vein - 0.35) / 0.65 + 0.15);
                if (rim) c = mix(c, rgb(190, 240, 255), 0.65 * pulse);
                buf.set(x, y, c);
                if (vein > 0.8 || rim) buf.add(x, y - 1, WARD, 0.05);
            }
        }
        private static boolean inField(int x, int y, int x0, int x1, int y0, int y1) {
            if (y < y0 || y > y1) return false;
            int end = Math.min(x - x0, x1 - x);
            if (end < 0) return false;
            double cy = (y0 + y1) / 2.0, ry = (y1 - y0) / 2.0 + 0.5;
            return end >= 5 || Math.pow((5 - end) / 5.0, 2) + Math.pow((y + 0.5 - cy - 0.5) / ry, 2) <= 1;
        }

        // ---------------------------------------------------------------------------------------------
        // Rift Eye: the anchor's side
        // ---------------------------------------------------------------------------------------------

        int riftFill(int x, int y) {
            double h = hash3(x, y, 90 + (int) Math.floor(t * 0.4 + hash3(x, y, 91) * 5));
            return h > 0.975 ? rgb(190, 210, 255) : h > 0.93 ? rgb(70, 52, 120) : mix(rgb(12, 10, 26), rgb(34, 20, 58), hash3(x >> 3, y >> 2, 92));
        }

        void riftEye() {
            int[] ring = ringPal(p.progress() * 1.2);
            Layer L = new Layer(), F = new Layer();
            anchorPanels(L);
            finish(L, buf);
            for (int y = 0; y <= 12; y++) for (int x = 119; x <= 136; x++) if (Math.hypot(x + 0.5 - XC, y + 0.5 - 4.6) <= 3.8) F.set(x, y, riftFill(x, y));
            blackHole(F, XC, 4.6, 3.8, ring, t, 1.3);
            for (int sx : new int[]{1, -1}) {
                for (double r = 7; r <= 13; r += 0.5) {
                    int x = (int) Math.floor(XC + sx * r), y = (int) Math.floor(5.5 - Math.sin((r - 7) / 6 * Math.PI) * 3 + (r - 7) * 0.3);
                    F.set(x, y, r > 12.4 ? G_HI : goldT(x, y)); F.set(x, y + 1, shade(goldT(x, y), 0.7));
                }
                for (double r = 7; r <= 11; r += 0.5) { int x = (int) Math.floor(XC + sx * r), y = (int) Math.floor(7 + (r - 7) * 0.6); F.set(x, y, goldT(x, y)); }
            }
            for (double dy : new double[]{-0.08, 0.05}) for (int s = 15; s <= 40; s++) {
                if (s % 4 == 3) continue;
                int x = (int) Math.round(XC + s);
                F.sym(x, BAN0 - 1 + (int) Math.round(dy * s), s > 38 ? G_HI : goldT(x, 6));
            }
            for (int y = 42; y <= 49; y++) for (int x = 120; x <= 135; x++) if (Math.hypot(x + 0.5 - XC, y + 0.5 - 44) <= 3.4) F.set(x, y, riftFill(x, y));
            blackHole(F, XC, 44, 3.4, ring, t * 1.3, 1.3);
            finish(F, buf);
            anchorBar();
            statIcons();
        }

        void anchorPanels(Layer L) {
            for (int y = BAN0; y <= BAN1; y++) for (int x = 30; x <= 127; x++) {
                int hw = banW(y); double d = Math.abs(x + 0.5 - XC); if (d > hw) continue;
                int c;
                if (y == BAN0) c = G_HI; else if (d > hw - 1) c = goldT(x, y);
                else if (y == BAN0 + 1 || d > hw - 2) c = shade(riftFill(x, y), 0.7); else c = riftFill(x, y);
                L.sym(x, y, c);
            }
            for (int y = PLQ0; y <= PLQ1; y++) for (int x = 30; x <= 127; x++) {
                int hw = plqW(y); double d = Math.abs(x + 0.5 - XC); if (d > hw) continue;
                int c;
                if (y == PLQ1) c = G_LO; else if (d > hw - 1) c = goldT(x, y);
                else if (y == PLQ1 - 1 || d > hw - 2) c = shade(riftFill(x, y), 0.7); else c = riftFill(x, y);
                L.sym(x, y, c);
            }
            int rc = live ? rgb(64, 120, 140) : rgb(48, 60, 72), gx = XC - bannerHalf() + 6;
            glyph(L, GLYPHS[3], gx, 11, rc); glyph(L, GLYPHS[1], 255 - gx - 3, 11, rc);
            for (int[] s : new int[][]{{XC - bannerHalf() + 2, 9}, {XC - bannerHalf() + 2, 16}, {52, 30}, {56, 37}}) L.sym(s[0], s[1], G_HI);
        }

        void anchorBar() {
            Layer B = new Layer();
            for (int x = 30; x <= 128; x++) {
                B.sym(x, YT + 1, goldT(x, YT + 1)); B.sym(x, YT + 7, shade(goldT(x, YT + 7), 0.8)); B.sym(x, YT + 10, shade(goldT(x, YT + 10), 0.66));
            }
            ColorAt empty = (x, y, r) -> r == 0 ? rgb(18, 19, 27) : rgb(30, 32, 42);
            lines(B, empty, empty, SiegeBarArt::goldT, p.fuel(), p.cap());
            chevrons(B, (x, y) -> tone(PLINTH, x, y, 3, 0.12), (x, y) -> G_HI, SiegeBarArt::goldT);
            finish(B, buf);
            wardField();
        }

        // ---------------------------------------------------------------------------------------------
        // Aurora Stones: the core's side
        // ---------------------------------------------------------------------------------------------

        int[] accent() {
            int[] s = SIEGE[p.kind().ordinal()];
            return live ? new int[]{s[LO], s[MID], s[HI]} : new int[]{rgb(80, 76, 90), rgb(110, 106, 120), rgb(150, 146, 160)};
        }

        void auroraStones() {
            Layer L = new Layer(), F = new Layer();
            hangingHeart();
            corePanels(L, 13);
            finish(L, buf);
            crystal(F, 125, 4, 0, 8);
            crystal(F, 116, 4, 2, 8);
            crystal(F, 107, 3, 4, 8);
            finish(F, buf);
            Layer R = new Layer();
            int[] ac = accent();
            int rx = XC - bannerHalf() + 3;
            glowRune(R, TINY[0], rx, 10, ac); glowRune(R, TINY[4], rx, 14, ac);
            blendGlows(R, buf);
            for (int i = 0; i < W * H; i++) if (R.c[i] != Layer.EMPTY) buf.set(i % W, i / W, R.c[i]);
            coreBar(13);
            heartEdgeGlow();
            statIcons();
        }

        void crystal(Layer F, int x0, int w, int top, int base) {
            for (int y = top; y <= base; y++) {
                int fromTip = y - top, ww = fromTip == 0 ? 1 : fromTip == 1 ? Math.min(w, 2) : w, xs = x0 + (w - ww) / 2;
                for (int i = 0; i < ww; i++) {
                    int x = xs + i;
                    double k = 0.86 + 0.14 * Math.sin(t * 0.9 + y * 0.5 + x0);
                    int c = fromTip == 0 ? rgb(230, 255, 245) : i == 0 ? AC_C : i == ww - 1 ? AC_A : AC_B;
                    if (ww >= 3 && i == 1 && (y + x0) % 3 == 0) c = mix(AC_B, AC_C, 0.5);
                    F.sym(x, y, shade(c, k));
                }
                if (fromTip == 0) F.symGlow(xs, y, AC_B, p.mode() == Mode.WARDED ? 0.3 : 0.12);
            }
        }

        void glowRune(Layer L, String[] g, int x, int y, int[] ac) {
            double pulse = live ? 0.82 + 0.18 * Math.sin(t * 1.2 + x * 0.1) : 0.7;
            for (int r = 0; r < g.length; r++) for (int c = 0; c < g[r].length(); c++) if (g[r].charAt(c) == 'X') {
                L.sym(x + c, y + r, mix(ac[1], ac[2], pulse - 0.6));
                if (live) { L.glow(x + c, y + r, ac[1], 0.07 * pulse); L.glow(255 - x - c, y + r, ac[1], 0.07 * pulse); }
            }
        }

        void corePanels(Layer L, int seed) {
            int bh = bannerHalf();
            for (int y = BAN0 - 1; y <= BAN1; y++) for (int x = 30; x <= 127; x++) {
                double d = Math.abs(x + 0.5 - XC);
                int hw = bh - bite(y, 2, 3, seed), top = BAN0 + bite(x, 1, 2, seed) - 1;
                panelPixel(L, x, y, d <= hw && y >= top, d <= hw - 3 && y >= BAN0 + 2, seed);
            }
            for (int y = PLQ0; y <= PLQ1 + 1; y++) for (int x = 30; x <= 127; x++) {
                double d = Math.abs(x + 0.5 - XC);
                int hw = plqW(Math.min(y, PLQ1)) - bite(y, 4, 3, seed), bot = PLQ1 + 1 - bite(x, 3, 2, seed);
                panelPixel(L, x, y, d <= hw && y <= bot, d <= hw - 3 && y <= PLQ1 - 2, seed);
            }
            for (int x = 30; x <= 127; x++) {
                double d = Math.abs(x + 0.5 - XC);
                if (d <= bh - 3 && d > 0) L.sym(x, BAN0 + 1, tone(CALC, x, 0, 61, 0.1));
                if (d <= plqW(PLQ1 - 1) - 3) L.sym(x, PLQ1 - 1, tone(CALC, x, 1, 61, 0.1));
            }
        }
        private static int bite(int i, int side, int amp, int seed) { return (int) Math.floor(hash3(Math.floorDiv(i, 3), side, seed) * amp); }
        private static void panelPixel(Layer L, int x, int y, boolean inside, boolean inner, int seed) {
            if (!inside) return;
            int c = inner ? shade(tone(rgb(70, 66, 80), x, y, seed, 0.08), 0.9 + hash3(x >> 2, y >> 1, seed) * 0.08) : rockAt(x, y, seed);
            if (!inner && y <= BAN0 + 1 && hash3(x, y, seed + 4) > 0.45) c = LICHEN;
            if (!inner && hash3(x, y, seed + 9) > 0.95) c = CALC;
            L.sym(x, y, c);
        }

        void coreBar(int seed) {
            Layer B = new Layer();
            for (int x = 30; x <= 128; x++) {
                B.sym(x, YT + 1, rockAt(x, YT + 1, seed)); B.sym(x, YT + 7, tone(CALC, x, 0, 61, 0.1)); B.sym(x, YT + 10, shade(rockAt(x, YT + 10, seed), 0.7));
                if (hash3(x >> 1, 1, seed) > 0.62) B.sym(x, YT, shade(rockAt(x, YT, seed), 1.1));
                if (hash3(x >> 1, 2, seed) > 0.7) B.sym(x, YT + 11, shade(rockAt(x, YT + 11, seed), 0.6));
                if (hash3(x, 3, seed) > 0.93) B.sym(x, YT, LICHEN);
            }
            ColorAt empty = (x, y, r) -> r == 0 ? rgb(22, 18, 30) : hash3(x, y, 52) > 0.9 ? rgb(62, 50, 82) : rgb(32, 28, 42);
            lines(B, empty, empty, (x, y) -> tone(CALC, x, y, 33, 0.08), p.fuel(), p.cap());
            rockChunk(B, 24, 37, 18, 33, seed);
            finish(B, buf);
            wardField();
        }

        void rockChunk(Layer L, int x0, int x1, int y0, int y1, int seed) {
            double cx = (x0 + x1) / 2.0, cy = (y0 + y1) / 2.0, rx = (x1 - x0) / 2.0, ry = (y1 - y0) / 2.0;
            for (int y = y0; y <= y1; y++) for (int x = x0; x <= x1; x++) {
                double d = Math.pow((x - cx) / rx, 2) + Math.pow((y - cy) / ry, 2) + hash3(x >> 1, y >> 1, seed) * 0.5;
                if (d > 1.15) continue;
                int c = shade(rockAt(x, y, seed), 1.12 - 0.25 * ((double) (y - y0) / (y1 - y0)));
                if (y - y0 < 2 && hash3(x, y, seed + 4) > 0.5) c = LICHEN;
                if (hash3(x, y, seed + 9) > 0.94) c = CALC;
                L.sym(x, y, c);
            }
        }

        int[] heartRing() {
            int[] s = SIEGE[p.kind().ordinal()];
            double kk = 0.3 + p.progress() * 0.7;
            int[] base = {rgb(40, 110, 230), rgb(120, 210, 255), rgb(160, 240, 255), rgb(235, 252, 255)};
            if (!live) return new int[]{rgb(60, 58, 70), rgb(100, 98, 112), rgb(130, 128, 142), rgb(150, 148, 160)};
            return new int[]{mix(base[LO], s[LO], kk), mix(base[MID], s[MID], kk), mix(base[HI], s[HI], kk), mix(base[BR], s[BR], kk)};
        }

        /** The core's black hole behind the plaque: its centre on the plaque's bottom edge, the near half of its disk showing. */
        void hangingHeart() {
            int[] ring = heartRing();
            final double TILT = 0.24, DISK = 30, IN = 11.2, R = 10, RW = 1.8, cy = PLQ1 + 1;
            Layer H2 = new Layer();
            for (int pass = 0; pass < 3; pass++) {
                if (pass == 1) {
                    for (int y = (int) Math.floor(cy - R - RW - 1); y <= cy + R + RW + 1; y++) for (int x = XC - 13; x <= XC + 13; x++) {
                        double dx = x + 0.5 - XC, dy = y + 0.5 - cy, d = Math.hypot(dx, dy);
                        if (d <= R) H2.set(x, y, d > R - 1 ? rgb(24, 12, 32) : rgb(4, 2, 8));
                        else if (d <= R + RW) {
                            double k = live ? 0.55 + 0.45 * Math.cos(Math.atan2(dy, dx) - t * 0.9) : 0.3;
                            H2.set(x, y, glowCol(ring, Math.min(1, k + (d < R + 0.8 ? 0.15 : 0))));
                            if (live) H2.glow(x, y, ring[MID], 0.08 + 0.06 * k);
                        }
                    }
                    continue;
                }
                boolean front = pass == 2;
                for (int y = (int) Math.floor(cy - DISK * TILT) - 1; y <= cy + DISK * TILT + 1; y++) for (int x = (int) (XC - DISK - 1); x <= XC + DISK; x++) {
                    double dx = x + 0.5 - XC, dy = y + 0.5 - cy;
                    if ((dy >= 0) != front) continue;
                    double rr = Math.hypot(dx, dy / TILT);
                    if (rr < IN || rr > DISK) continue;
                    double a = Math.atan2(dy / TILT, dx), tt = (rr - IN) / (DISK - IN);
                    double side = dx < 0 ? 1 : 0.68;
                    double streak = live ? 0.72 + 0.28 * Math.sin(a * 3 + rr * 0.55 - t * 1.6) : 0.6;
                    double k = Math.pow(1 - tt, 1.3) * side * streak * (front ? 1 : 0.85);
                    H2.set(x, y, k < 0.12 ? mix(rgb(30, 16, 40), ring[LO], k * 6) : glowCol(ring, Math.min(1, k * 1.2)));
                    if (live && k > 0.3) H2.glow(x, y, ring[MID], 0.06 * k);
                }
            }
            finish(H2, buf);
        }
        private static int glowCol(int[] ring, double k) { return k > 0.8 ? mix(ring[HI], ring[BR], (k - 0.8) * 5) : mix(ring[LO], ring[HI], k / 0.8); }

        void heartEdgeGlow() {
            if (!live) return;
            int mid = mix(rgb(120, 210, 255), SIEGE[p.kind().ordinal()][MID], 0.3 + p.progress() * 0.7);
            for (int d = 0; d < 18; d++) for (int sx : new int[]{1, -1}) {
                int x = (int) Math.floor(XC + sx * (30 - 6 + d));
                double k = (1 - d / 18.0) * (sx < 0 ? 1 : 0.68);
                buf.add(x, PLQ1 + 1, mid, 0.3 * k); buf.add(x, PLQ1, mid, 0.22 * k); buf.add(x, PLQ1 - 1, mid, 0.08 * k);
            }
        }
    }

    private SiegeBarArt() {}
}
