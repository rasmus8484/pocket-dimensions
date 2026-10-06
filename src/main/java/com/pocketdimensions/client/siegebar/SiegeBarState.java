package com.pocketdimensions.client.siegebar;

/**
 * One siege as the server last reported it, and what it reads like some ticks later. The server sends this every
 * second; in between, progress moves on at the reported rate (1 = every tick, N = every N ticks while the World Core
 * wards, 0 = standing still) so the bar moves smoothly. Pure logic, no Minecraft classes.
 */
public record SiegeBarState(SiegeBarArt.Kind kind, int progressTicks, int durationTicks, int rate,
                            int siegeFuel, int siegeCap, int coreFuel) {

    /** Progress ticks {@code ticksSince} ticks after the report, never past the end. */
    public double progressTicksAt(double ticksSince) {
        double p = progressTicks + (rate > 0 ? ticksSince / rate : 0);
        return Math.min(p, durationTicks);
    }

    /** Progress 0..1 {@code ticksSince} ticks after the report. */
    public double progressAt(double ticksSince) {
        return durationTicks <= 0 ? 1 : progressTicksAt(ticksSince) / durationTicks;
    }

    /** No lapis in the siege block: dormant, whatever the core does. Otherwise warded while the core holds lapis. */
    public SiegeBarArt.Mode mode() {
        if (siegeFuel <= 0) return SiegeBarArt.Mode.DORMANT;
        return coreFuel > 0 ? SiegeBarArt.Mode.WARDED : SiegeBarArt.Mode.ACTIVE;
    }

    /** Time left at the current pace ("7m 36s", "1h 0m 0s"), or "Dormant". */
    public String timeText(double ticksSince) {
        if (mode() == SiegeBarArt.Mode.DORMANT) return "Dormant";
        double left = durationTicks - progressTicksAt(ticksSince);
        long s = Math.round(left * Math.max(rate, 1) / 20.0);
        long h = s / 3600, m = (s % 3600) / 60;
        s %= 60;
        if (h > 0) return h + "h " + m + "m " + s + "s";
        if (m > 0) return m + "m " + s + "s";
        return s + "s";
    }

    /** Whole percent, rounded down so 100% only shows when the siege is done. */
    public String percentText(double ticksSince) {
        return (durationTicks <= 0 ? 100 : (int) Math.floor(progressTicksAt(ticksSince) * 100 / durationTicks)) + "%";
    }
}
