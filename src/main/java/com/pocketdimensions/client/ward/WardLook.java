package com.pocketdimensions.client.ward;

/**
 * How the ward's sigil looks: its colour for the siege (the World Core beam's colours), its brightness (the Core's
 * lapis, day and night) and which pieces show (an open breach cracks it, a lost anchor breaks it and stills it).
 * Plain logic, unit tested; WardSigilRenderer draws with it.
 */
public final class WardLook {

    public static final int NORMAL = 0, BREACHING = 1, BREAKING = 2, LOST = 3;

    private WardLook() {}

    public static int colour(int siege) {
        return switch (siege) {
            case BREACHING -> 0xFF5ADC;
            case BREAKING -> 0xFF5028;
            case LOST -> 0x8A2A1A;
            default -> 0x8CEBFF;
        };
    }

    /** 0..1: full with lapis at night, about half without; by day a quarter of that or more. starBrightness is 0..0.5. */
    public static float brightness(boolean warded, float starBrightness) {
        float night = Math.max(0f, Math.min(1f, starBrightness * 2f));
        return (warded ? 1f : 0.55f) * (0.3f + 0.7f * night);
    }

    /** Whether piece `index` shows: all at peace; a quarter missing (and some flickering) once breached; 40% when lost. */
    public static boolean shown(int index, int siege, boolean breachOpen, long tick) {
        double h = hash(index);
        if (siege == LOST) return h >= 0.4;
        if (breachOpen) {
            if (h < 0.25) return false;
            if (h < 0.35) return hash(index * 31 + (int) (tick / 4)) > 0.5;      // flickering at the cracks
        }
        return true;
    }

    /** Whether the rings turn: not once the anchor is lost. */
    public static boolean turns(int siege) { return siege != LOST; }

    private static double hash(int i) {
        int n = i * 374761393;
        n = (n ^ (n >>> 13)) * 1274126177;
        return ((n ^ (n >>> 16)) >>> 0 & 0x7FFFFFFF) / (double) 0x7FFFFFFF;
    }
}
