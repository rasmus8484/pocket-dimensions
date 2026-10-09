package com.pocketdimensions.client.ward;

import java.util.ArrayList;
import java.util.List;

/**
 * Where the ward's sigil puts its pieces, flat at PLANE_Y over the realm, centred on the realm (not the Core, which can
 * stand off centre): an inner seal with a hexagram turning in it, a conduit carrying the Core's beam across to the seal
 * when the Core is off centre, four rings of runes (the outermost inside the edge), a
 * square band of runes just inside the Veil, and four spiral arms curling out from the seal to the band like the
 * accretion disk round the Core's black hole. Rings are drawn turned by turn(ring, ticks), the arms by turnArms, the
 * hexagram by turnSeal. Plain logic, unit tested; built once per ward sync, drawn every frame.
 */
public final class SigilLayout {

    public static final int PLANE_Y = 340;
    private static final double EDGE_MARGIN = 6, SEAL_RADIUS = 20, RING_TURN = 0.0004;     // radians per tick
    private static final double ARM_TURN = 0.0006, SEAL_TURN = -0.0015;                     // radians per tick
    private static final int ARMS = 4;
    /** How fast a spiral arm's radius grows per radian it turns (r = r0 * e^(GROWTH * angle)). */
    private static final double GROWTH = 1.75;

    public enum Kind { SEAL, RING, ARM, STAR, BAND, CONDUIT }
    public record Rune(Kind kind, int ring, double x, double z, double size, double angle, int glyph, double radius) {}
    /** A stroke; `ring` is the ring for RING lines and the arm for ARM lines; `fade` 1..0.3 dims an arm toward its end,
     *  `along` is how far along its arm a segment starts (for the pulses). */
    public record Line(Kind kind, int ring, double x1, double z1, double x2, double z2, double width, double fade, double along) {}
    public record Sigil(double cx, double cz, List<Rune> runes, List<Line> lines) {}

    private SigilLayout() {}

    /** The sigil over the plot [minX, maxX) x [minZ, maxZ), whose World Core's beam rises at (coreX, coreZ). */
    public static Sigil build(double coreX, double coreZ, int minX, int minZ, int maxX, int maxZ) {
        double cx = (minX + maxX) / 2.0, cz = (minZ + maxZ) / 2.0;
        List<Rune> runes = new ArrayList<>();
        List<Line> lines = new ArrayList<>();
        double edge = Math.min(Math.min(cx - minX, maxX - cx), Math.min(cz - minZ, maxZ - cz)) - EDGE_MARGIN;
        double size = Math.max(3, Math.min(16, edge / 24));
        int glyph = 0;

        // the seal: eight runes round the beam's top, and a circle
        double seal = Math.min(SEAL_RADIUS, edge * 0.12);
        for (int i = 0; i < 8; i++) {
            double a = i * Math.PI / 4;
            runes.add(new Rune(Kind.SEAL, -1, cx + Math.cos(a) * seal, cz + Math.sin(a) * seal, size * 0.7, a, glyph++ % 6, seal));
        }
        circle(lines, Kind.SEAL, -1, cx, cz, seal + size * 0.6, size * 0.12, 48);

        // four turning rings, the outermost clear of the nearest edge at any angle
        double outer = edge - size;
        for (int ring = 0; ring < 4; ring++) {
            double r = outer * (0.28 + 0.24 * ring);
            int count = (int) Math.floor(2 * Math.PI * r / (size * 1.6));
            for (int i = 0; i < count; i++) {
                double a = 2 * Math.PI * i / count;
                runes.add(new Rune(Kind.RING, ring, cx + Math.cos(a) * r, cz + Math.sin(a) * r, size, a + Math.PI / 2, glyph++ % 6, r));
            }
            circle(lines, Kind.RING, ring, cx, cz, r - size * 0.75, size * 0.08, 96);
            circle(lines, Kind.RING, ring, cx, cz, r + size * 0.75, size * 0.08, 96);
        }

        // the band: runes along a square just inside the plot
        double bx0 = minX + EDGE_MARGIN + size / 2, bx1 = maxX - EDGE_MARGIN - size / 2;
        double bz0 = minZ + EDGE_MARGIN + size / 2, bz1 = maxZ - EDGE_MARGIN - size / 2;
        double step = size * 1.4;
        for (double x = bx0; x <= bx1 + 1e-9; x += step) {
            runes.add(new Rune(Kind.BAND, -2, x, bz0, size, 0, glyph++ % 6, 0));
            runes.add(new Rune(Kind.BAND, -2, x, bz1, size, Math.PI, glyph++ % 6, 0));
        }
        for (double z = bz0 + step; z <= bz1 - step + 1e-9; z += step) {
            runes.add(new Rune(Kind.BAND, -2, bx0, z, size, -Math.PI / 2, glyph++ % 6, 0));
            runes.add(new Rune(Kind.BAND, -2, bx1, z, size, Math.PI / 2, glyph++ % 6, 0));
        }

        // the conduit: the Core's beam, reaching the sigil's plane off centre, carried across to the seal
        double toCore = Math.hypot(coreX - cx, coreZ - cz), sealEdge = seal + size;
        if (toCore > sealEdge) {
            double ux = (cx - coreX) / toCore, uz = (cz - coreZ) / toCore, len = toCore - sealEdge, piece = Math.max(4, size);
            for (double d = 0; d < len; d += piece) {
                double e = Math.min(len, d + piece);
                lines.add(new Line(Kind.CONDUIT, -5, coreX + ux * d, coreZ + uz * d, coreX + ux * e, coreZ + uz * e, size * 0.12, 1, d));
            }
        }

        // a hexagram turning in the seal: two triangles
        double star = seal + size * 0.6;
        for (int t = 0; t < 2; t++)
            for (int i = 0; i < 3; i++) {
                double a0 = t * Math.PI / 3 + i * 2 * Math.PI / 3, a1 = a0 + 2 * Math.PI / 3;
                lines.add(new Line(Kind.STAR, -4, cx + Math.cos(a0) * star, cz + Math.sin(a0) * star,
                        cx + Math.cos(a1) * star, cz + Math.sin(a1) * star, size * 0.1, 1, 0));
            }

        // four spiral arms from the seal out to the band, in segments about a rune long, fading toward their ends
        double r0 = seal + size, seg = Math.max(4, size), stretch = Math.sqrt(1 + GROWTH * GROWTH);
        for (int arm = 0; arm < ARMS; arm++) {
            double base = arm * 2 * Math.PI / ARMS, th = 0, along = 0;
            List<Line> segs = new ArrayList<>();
            while (true) {
                double r = r0 * Math.exp(GROWTH * th);
                double th1 = th + seg / (r * stretch), r1 = r0 * Math.exp(GROWTH * th1);
                double a1 = base + th1;
                if (r1 > toSquare(cx, cz, Math.cos(a1), Math.sin(a1), bx0, bz0, bx1, bz1) - size) break;
                double a = base + th;
                segs.add(new Line(Kind.ARM, arm, cx + Math.cos(a) * r, cz + Math.sin(a) * r,
                        cx + Math.cos(a1) * r1, cz + Math.sin(a1) * r1, size * 0.07, 0, along));
                along += Math.hypot(Math.cos(a1) * r1 - Math.cos(a) * r, Math.sin(a1) * r1 - Math.sin(a) * r);
                th = th1;
            }
            for (int i = 0; i < segs.size(); i++) {
                Line l = segs.get(i);
                lines.add(new Line(l.kind(), l.ring(), l.x1(), l.z1(), l.x2(), l.z2(), l.width(), 1 - 0.7 * i / Math.max(1, segs.size()), l.along()));
            }
        }
        return new Sigil(cx, cz, runes, lines);
    }

    /** Radians ring `ring` has turned after `ticks`: even rings one way, odd rings the other, inner rings faster. */
    public static double turn(int ring, double ticks) {
        double dir = ring % 2 == 0 ? 1 : -1;
        return dir * RING_TURN * ticks / (1 + ring * 0.5);
    }

    /** Radians the spiral arms have turned after `ticks`. */
    public static double turnArms(double ticks) { return ARM_TURN * ticks; }

    /** Radians the seal's hexagram has turned after `ticks` (against the arms). */
    public static double turnSeal(double ticks) { return SEAL_TURN * ticks; }

    private static void circle(List<Line> out, Kind kind, int ring, double cx, double cz, double r, double width, int segments) {
        for (int i = 0; i < segments; i++) {
            double a0 = 2 * Math.PI * i / segments, a1 = 2 * Math.PI * (i + 1) / segments;
            out.add(new Line(kind, ring, cx + Math.cos(a0) * r, cz + Math.sin(a0) * r, cx + Math.cos(a1) * r, cz + Math.sin(a1) * r, width, 1, 0));
        }
    }

    /** Distance from (cx, cz) along (dx, dz) to the square [x0, x1] x [z0, z1]. */
    private static double toSquare(double cx, double cz, double dx, double dz, double x0, double z0, double x1, double z1) {
        double tx = dx > 1e-9 ? (x1 - cx) / dx : dx < -1e-9 ? (x0 - cx) / dx : Double.MAX_VALUE;
        double tz = dz > 1e-9 ? (z1 - cz) / dz : dz < -1e-9 ? (z0 - cz) / dz : Double.MAX_VALUE;
        return Math.min(tx, tz);
    }
}
