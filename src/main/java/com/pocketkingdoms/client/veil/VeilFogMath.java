package com.pocketkingdoms.client.veil;

/**
 * The Veil fog's closed forms, as in veil_fog.fsh (keep the two in step): smoke density falls off as e^(-d/H) with d the
 * distance from a wall's face into the realm, so the smoke along a straight view has a formula and needs no steps. A
 * view is described per wall by dc (the camera's d) and rx (how much d changes per block along the view). Plain logic,
 * unit tested; the renderer uses the top fade, the rest pins down what the shader computes.
 */
public final class VeilFogMath {

    public static final double H = 2.4, SIGMA = 0.8, XMAX = 24, NEAR = 10;

    private VeilFogMath() {}

    /** The smoke between ta and tb along the view: the integral of e^(-d/H). */
    public static double amount(double dc, double rx, double ta, double tb) {
        if (tb <= ta) return 0;
        double da = dc + rx * ta, db = dc + rx * tb;
        if (Math.abs(rx) < 1e-3) return Math.exp(-0.5 * (da + db) / H) * (tb - ta);   // nearly along the wall: midpoint
        return (-H / rx) * (Math.exp(-db / H) - Math.exp(-da / H));
    }

    /** Where along ta..tb half the smoke lies in front and half behind: where the shader takes colour and fog. */
    public static double middle(double dc, double rx, double ta, double tb) {
        if (Math.abs(rx) < 1e-3 || tb <= ta) return 0.5 * (ta + tb);
        double da = dc + rx * ta, db = dc + rx * tb;
        double dm = -H * Math.log(0.5 * (Math.exp(-da / H) + Math.exp(-db / H)));
        return Math.max(ta, Math.min(tb, (dm - dc) / rx));
    }

    /** The part of ta..tb where lo < d < hi, as {from, to}; empty when to <= from. */
    public static double[] clip(double lo, double hi, double dc, double rx, double ta, double tb) {
        if (Math.abs(rx) < 1e-4) return dc >= lo && dc <= hi ? new double[] {ta, tb} : new double[] {0, -1};
        double a = (lo - dc) / rx, b = (hi - dc) / rx;
        return new double[] {Math.max(ta, Math.min(a, b)), Math.min(tb, Math.max(a, b))};
    }

    /** The smoke covers the whole Veil and only thins out over its last blocks, starting here. */
    public static double fadeStart(int minY, int height) { return minY + height - TOP_FADE; }

    /** And is gone at the Veil's top, the top of the world. */
    public static double fadeEnd(int minY, int height) { return minY + height; }

    private static final double TOP_FADE = 16;
}
