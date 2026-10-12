package com.pocketkingdoms.client.ward;

import org.jspecify.annotations.Nullable;

import java.util.Arrays;
import java.util.Locale;

/**
 * /pk test wardTime: measures how long the ward sigil takes to build and hand over each frame (CPU time, on the render
 * thread) for a few seconds, then reports once. The figure is in milliseconds and as a share of a 60 fps frame, so it
 * means the same on any computer. Plain logic, unit tested; WardSigilRenderer feeds it, WardTimerClient reports it.
 */
public final class WardTimer {

    private static final double FRAME_60_MS = 1000.0 / 60;

    /** The result: frames drawn in the window, CPU time per frame (average, 95th percentile, worst), average vertices. */
    public record Report(double seconds, int frames, double averageMs, double p95Ms, double worstMs, int vertices) {
        public String describe() {
            if (frames == 0)
                return String.format(Locale.ROOT, "Ward sigil: not drawn in %.1f s (stand in a realm, under the open sky).", seconds);
            return String.format(Locale.ROOT,
                    "Ward sigil over %.1f s, %d frames: %.2f ms a frame on average (95%% under %.2f ms, worst %.2f ms), "
                            + "%,d vertices. That is %.1f%% of a 60 fps frame.",
                    seconds, frames, averageMs, p95Ms, worstMs, vertices, averageMs / FRAME_60_MS * 100);
        }
    }

    private long[] nanos = new long[256];
    private long vertexSum, until;
    private int frames;
    private double seconds;
    private boolean measuring;

    /** Starts a window of `seconds` from `now` (System.nanoTime), dropping any window still open. */
    public void start(double seconds, long now) {
        this.seconds = seconds;
        until = now + (long) (seconds * 1e9);
        frames = 0;
        vertexSum = 0;
        measuring = true;
    }

    public boolean measuring() { return measuring; }

    /** One frame of the ward: its CPU time and how many vertices it drew. Ignored outside a window. */
    public void frame(long cpuNanos, int vertices) {
        if (!measuring) return;
        if (frames == nanos.length) nanos = Arrays.copyOf(nanos, frames * 2);
        nanos[frames++] = cpuNanos;
        vertexSum += vertices;
    }

    /** The report once the window has closed at `now`, exactly once; null before that or when nothing is measured. */
    public @Nullable Report poll(long now) {
        if (!measuring || now < until) return null;
        measuring = false;
        if (frames == 0) return new Report(seconds, 0, 0, 0, 0, 0);
        long[] sorted = Arrays.copyOf(nanos, frames);
        Arrays.sort(sorted);
        long sum = 0;
        for (long n : sorted) sum += n;
        int p95 = Math.max(0, (int) Math.ceil(frames * 0.95) - 1);
        return new Report(seconds, frames, sum / 1e6 / frames, sorted[p95] / 1e6, sorted[frames - 1] / 1e6,
                (int) Math.round((double) vertexSum / frames));
    }
}
