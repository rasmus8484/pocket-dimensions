package com.pocketdimensions.client;

/** The mod's beacon beams are thinner than vanilla's: a slender core with a soft glow (vanilla: 0.2 / 0.25 blocks). */
public final class ThinBeam {
    /** Radius of the solid core, in blocks. */
    public static final float SOLID_RADIUS = 0.07f;
    /** Radius of the translucent glow around it, in blocks. */
    public static final float GLOW_RADIUS = 0.16f;

    private ThinBeam() {}
}
