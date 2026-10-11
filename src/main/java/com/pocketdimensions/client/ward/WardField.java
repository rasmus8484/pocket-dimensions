package com.pocketdimensions.client.ward;

/**
 * The ward field: the power of the ward as soft light filling the square out to the band, under the runes. Its walls
 * are the flowing contour lines of a smooth noise field (like the bands in marble): curved, meandering, never meeting at
 * joints. The field is laid out round the circle with its features stretched along the radius, and streams outward
 * from the core; each wall is a bell-shaped swell fading to clear on either side. It is clear by the core and falls off
 * steeply to very faint by the outer tier. Coordinates are blocks from the realm's centre, time in seconds. Plain
 * logic, unit tested; the reference is design/ward-sigil-studies.html.
 */
public final class WardField {

    /** Blocks per second the field streams outward. */
    public static final double SPEED = 9;
    private static final double AROUND = 2.6, ALONG = 190, WIDTH = 0.055, INNER = 16;

    private WardField() {}

    /** The field's peak opacity at r blocks from the core: 0.065 far out, rising steeply (exponentially) toward it. */
    public static double strength(double r) {
        return 0.065 * (1 + 10 * Math.exp(-r / 38));
    }

    /** 0..1: how strongly a wall of the field lies at (x, z) at time t (1 on a wall, falling to 0 between them). */
    public static double wave(double x, double z, double t) {
        double r = Math.hypot(x, z);
        if (r < 1e-9) return 0;
        double cx = x / r * AROUND, cz = z / r * AROUND, u = (r - t * SPEED) / ALONG;
        double f = 0.68 * noise(cx, cz, u) + 0.32 * noise(cx * 2.1 + 9, cz * 2.1 + 4, u * 2.1);
        double sw = (f - 0.5) / WIDTH;
        return Math.exp(-sw * sw);
    }

    /**
     * The field's opacity at (x, z) at time t, for a plot `half` blocks across from its centre: strength times wave,
     * with a slow pulse and a short fade-in right round the beam; 0 outside the square (half - 15) and at the beam.
     */
    public static double alpha(double half, double x, double z, double t) {
        double r = Math.hypot(x, z), square = half - 15;
        if (r < INNER || Math.max(Math.abs(x), Math.abs(z)) > square) return 0;
        double pulse = 0.85 + 0.15 * Math.sin(t * 1.1);
        return strength(r) * wave(x, z, t) * pulse * Math.min(1, (r - INNER) / 24);
    }

    /** Smooth value noise in three dimensions, 0..1. */
    static double noise(double x, double y, double z) {
        int xi = (int) Math.floor(x), yi = (int) Math.floor(y), zi = (int) Math.floor(z);
        double fx = x - xi, fy = y - yi, fz = z - zi;
        double sx = fx * fx * (3 - 2 * fx), sy = fy * fy * (3 - 2 * fy), sz = fz * fz * (3 - 2 * fz);
        double x00 = lerp(h(xi, yi, zi), h(xi + 1, yi, zi), sx), x10 = lerp(h(xi, yi + 1, zi), h(xi + 1, yi + 1, zi), sx);
        double x01 = lerp(h(xi, yi, zi + 1), h(xi + 1, yi, zi + 1), sx), x11 = lerp(h(xi, yi + 1, zi + 1), h(xi + 1, yi + 1, zi + 1), sx);
        return lerp(lerp(x00, x10, sy), lerp(x01, x11, sy), sz);
    }

    private static double lerp(double a, double b, double k) { return a + (b - a) * k; }

    private static double h(int a, int b, int c) {
        int n = a * 73856093 ^ b * 19349663 ^ c * 83492791;
        n = (n ^ (n >>> 13)) * 1274126177;
        n ^= n >>> 16;
        return (n & 0x7FFFFFFF) / (double) 0x7FFFFFFF;
    }
}
