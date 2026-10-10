package com.pocketdimensions.client.ward;

/**
 * Where the ward field is sampled: a polar grid round the realm's centre, fixed for a layout (only the opacities change
 * each frame). Its walls run along the radius, so the grid is fine around the circle and coarse along it; its rings
 * crowd in by the core where the field is strongest. Points beyond the square are pulled in onto its edge, so the
 * field ends cleanly at the square. Positions are blocks from the centre, heights just under the dome. Plain logic,
 * unit tested; WardSigilRenderer draws it.
 */
public final class FieldGrid {

    /** Sectors round the circle and rings out from the beam (ring 0 at the field's inner edge). */
    public static final int SECTORS = 256, RINGS = 24;
    private static final double INNER = 16, CROWD = 1.5, UNDER_DOME = 5;
    /** Below this summed corner opacity a quad is left out. */
    private static final float FAINT = 0.016f;

    private final float[] x, z, y;

    private FieldGrid(float[] x, float[] z, float[] y) { this.x = x; this.z = z; this.y = y; }

    public static FieldGrid build(Vault.Layout l) {
        double square = l.half() - 15, far = square * Math.sqrt(2);
        int n = (RINGS + 1) * SECTORS;
        float[] x = new float[n], z = new float[n], y = new float[n];
        for (int ring = 0; ring <= RINGS; ring++) {
            double r = INNER + (far - INNER) * Math.pow((double) ring / RINGS, CROWD);
            for (int s = 0; s < SECTORS; s++) {
                double a = s * Math.PI * 2 / SECTORS, px = Math.cos(a) * r, pz = Math.sin(a) * r;
                double edge = Math.max(Math.abs(px), Math.abs(pz));
                if (edge > square) { px *= square / edge; pz *= square / edge; }
                int p = ring * SECTORS + s;
                x[p] = (float) px;
                z[p] = (float) pz;
                y[p] = (float) (l.dome(Math.hypot(px, pz)) + UNDER_DOME);
            }
        }
        return new FieldGrid(x, z, y);
    }

    public int points() { return x.length; }
    public int index(int ring, int sector) { return ring * SECTORS + Math.floorMod(sector, SECTORS); }
    public float x(int p) { return x[p]; }
    public float z(int p) { return z[p]; }
    public float y(int p) { return y[p]; }

    /** The field's opacity at every point at time t, times `bright`, into `out` (length points()). */
    public void alphas(double half, double t, float bright, float[] out) {
        for (int p = 0; p < x.length; p++) out[p] = (float) (WardField.alpha(half, x[p], z[p], t) * bright);
    }

    /**
     * Whether the quad between ring and ring + 1, sector and sector + 1 is drawn: not when it is all but clear, and not
     * when it lies wholly on the square's edge (its inner corners already pulled in, so it has no area).
     */
    public boolean drawn(float[] alpha, int ring, int sector) {
        int a = index(ring, sector), b = index(ring, sector + 1), c = index(ring + 1, sector + 1), d = index(ring + 1, sector);
        if (alpha[a] + alpha[b] + alpha[c] + alpha[d] < FAINT) return false;
        return !(onEdge(a) && onEdge(b));
    }

    private boolean onEdge(int p) {
        int ring = p / SECTORS;
        return ring < RINGS && x[p] == x[p + SECTORS] && z[p] == z[p + SECTORS];
    }
}
