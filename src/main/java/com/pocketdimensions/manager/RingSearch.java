package com.pocketdimensions.manager;

/**
 * Searches square rings of chunks outward from a centre. Plain logic, unit tested; RealmManager uses it for the World
 * Core's dry-land search.
 */
public final class RingSearch {

    /** What a probe found in one chunk, and its squared distance from the centre. */
    public record Hit<T>(T value, double distSq) {}

    @FunctionalInterface
    public interface Probe<T> {
        /** The best find in the chunk at (dx, dz) from the centre, or null. */
        Hit<T> at(int dx, int dz);
    }

    private RingSearch() {}

    /**
     * The nearest find within maxRing rings of the centre. Rings are searched outward (ring 0 is the centre chunk, ring
     * k the chunks k away), and the search stops one ring past the first find: a find in a later ring can't be much
     * nearer, and the chunks beyond are never looked at (each probe may generate a chunk). Null if nothing is found.
     */
    public static <T> T nearest(int maxRing, Probe<T> probe) {
        Hit<T> best = null;
        int stopAfter = maxRing;
        for (int ring = 0; ring <= stopAfter; ring++) {
            for (int dx = -ring; dx <= ring; dx++)
                for (int dz = -ring; dz <= ring; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != ring) continue;   // only this ring's edge
                    Hit<T> h = probe.at(dx, dz);
                    if (h != null && (best == null || h.distSq() < best.distSq())) best = h;
                }
            if (best != null) stopAfter = Math.min(stopAfter, ring + 1);
        }
        return best == null ? null : best.value();
    }
}
