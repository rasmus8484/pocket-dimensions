package com.pocketdimensions.client.veil;

import org.jspecify.annotations.Nullable;

import java.util.Arrays;
import java.util.Locale;

/**
 * /pd test veilTime: collects the Veil fog pass's GPU time per frame (from timer queries) for a few seconds, then
 * reports once, in milliseconds and as a share of a 60 fps frame, so it means the same on any computer. Plain logic,
 * unit tested; VeilFogRenderer feeds it.
 */
public final class VeilFogTimer {

    private static final double FRAME_60_MS = 1000.0 / 60;

    /**
     * The result: frames measured, GPU time per frame (average, 95th percentile, worst), and frames that could not be
     * timed because another GPU timer was running (the F3 GPU display or the F3+L profiler).
     */
    public record Report(double seconds, int frames, double averageMs, double p95Ms, double worstMs, int skipped) {
        public String describe() {
            if (frames == 0 && skipped > 0)
                return String.format(Locale.ROOT, "Veil fog: could not be timed in %.1f s while the F3 GPU display or the "
                        + "F3+L profiler is running. Turn them off and measure again.", seconds);
            if (frames == 0)
                return String.format(Locale.ROOT, "Veil fog: not drawn in %.1f s (stand in a realm with the Veil in view).", seconds);
            return String.format(Locale.ROOT,
                    "Veil fog over %.1f s, %d frames: %.2f ms of graphics card time a frame on average (95%% under %.2f ms, "
                            + "worst %.2f ms). That is %.1f%% of a 60 fps frame.",
                    seconds, frames, averageMs, p95Ms, worstMs, averageMs / FRAME_60_MS * 100);
        }
    }

    private long[] nanos = new long[256];
    private long until;
    private int frames, skipped;
    private double seconds;
    private boolean measuring;

    /** Starts a window of `seconds` from `now` (System.nanoTime), dropping any window still open. */
    public void start(double seconds, long now) {
        this.seconds = seconds;
        until = now + (long) (seconds * 1e9);
        frames = 0;
        skipped = 0;
        measuring = true;
    }

    public boolean measuring() { return measuring; }

    /** One frame's GPU time. Ignored outside a window. */
    public void frame(long gpuNanos) {
        if (!measuring) return;
        if (frames == nanos.length) nanos = Arrays.copyOf(nanos, frames * 2);
        nanos[frames++] = gpuNanos;
    }

    /** A frame drawn while another GPU timer was running, so it could not be timed. Ignored outside a window. */
    public void skipped() {
        if (measuring) skipped++;
    }

    /** The report once the window has closed at `now`, exactly once; null before that or when nothing is measured. */
    public @Nullable Report poll(long now) {
        if (!measuring || now < until) return null;
        measuring = false;
        if (frames == 0) return new Report(seconds, 0, 0, 0, 0, skipped);
        long[] sorted = Arrays.copyOf(nanos, frames);
        Arrays.sort(sorted);
        long sum = 0;
        for (long n : sorted) sum += n;
        int p95 = Math.max(0, (int) Math.ceil(frames * 0.95) - 1);
        return new Report(seconds, frames, sum / 1e6 / frames, sorted[p95] / 1e6, sorted[frames - 1] / 1e6, skipped);
    }
}
