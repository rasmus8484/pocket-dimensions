package com.pocketdimensions.client.siegebar;

import com.pocketdimensions.client.siegebar.SiegeBarArt.Kind;
import com.pocketdimensions.client.siegebar.SiegeBarArt.Mode;
import com.pocketdimensions.client.siegebar.SiegeBarArt.Params;
import com.pocketdimensions.client.siegebar.SiegeBarArt.Side;
import org.junit.jupiter.api.Test;

import static com.pocketdimensions.client.siegebar.SiegeBarArt.*;
import static org.junit.jupiter.api.Assertions.*;

class SiegeBarArtTest {

    private static Params params(Side side, Kind kind, Mode mode, double progress, int fuel, int cap) {
        return new Params(side, kind, mode, progress, fuel, cap, 1.25, 95, 18, 30);
    }

    private static int[] draw(Params p) {
        int[] out = new int[W * H];
        SiegeBarArt.draw(p, out);
        return out;
    }

    private static int a(int argb) { return argb >>> 24; }
    private static int r(int argb) { return (argb >> 16) & 255; }
    private static int g(int argb) { return (argb >> 8) & 255; }
    private static int b(int argb) { return argb & 255; }
    private static int at(int[] img, int x, int y) { return img[y * W + x]; }

    @Test
    void theBarIsSolidAndTheCornersAreClear() {
        for (Side side : Side.values()) {
            int[] img = draw(params(side, Kind.BREACHER, Mode.ACTIVE, 0.5, 3, 5));
            assertEquals(255, a(at(img, 100, PROGRESS_Y + 2)), side + " progress line");
            assertEquals(255, a(at(img, 100, FUEL_Y)), side + " fuel line");
            assertEquals(0, a(at(img, 0, 0)), side + " top left corner");
            assertEquals(0, a(at(img, W - 1, H - 1)), side + " bottom right corner");
        }
    }

    @Test
    void progressFillsFromTheLeftInTheSiegeColour() {
        int[] img = draw(params(Side.ANCHOR, Kind.BREAKER, Mode.ACTIVE, 0.5, 3, 5));
        int lit = at(img, LINE_X + 20, PROGRESS_Y + 2), unlit = at(img, LINE_X + 160, PROGRESS_Y + 2);
        assertTrue(r(lit) > 150 && r(lit) > g(lit) + 60, "the Breaker burns red-orange: " + Integer.toHexString(lit));
        assertTrue(r(unlit) < 60, "past the head the line is dark: " + Integer.toHexString(unlit));
        int[] breach = draw(params(Side.ANCHOR, Kind.BREACHER, Mode.ACTIVE, 0.5, 3, 5));
        int pink = at(breach, LINE_X + 20, PROGRESS_Y + 2);
        assertTrue(b(pink) > 120 && r(pink) > 150, "the Breacher burns pink: " + Integer.toHexString(pink));
    }

    @Test
    void slottedFuelShowsOneLitSlotPerLapis() {
        int[] img = draw(params(Side.ANCHOR, Kind.BREACHER, Mode.ACTIVE, 0.3, 3, 5));
        assertEquals(3, litRuns(img), "three of five slots lit");
    }

    @Test
    void aBigFuelMaxGivesOneContinuousLine() {
        int[] img = draw(params(Side.CORE, Kind.BREACHER, Mode.ACTIVE, 0.3, 32, 64));
        assertEquals(1, litRuns(img));
        int lit = 0;
        for (int k = 0; k < LINE_LEN; k++) if (isLapis(at(img, LINE_X + k, FUEL_Y + 1))) lit++;
        assertEquals(LINE_LEN / 2, lit, 1);
    }

    @Test
    void theWardCoversOnlyTheProgressLine() {
        Params open = params(Side.CORE, Kind.BREAKER, Mode.ACTIVE, 0.6, 3, 5);
        Params warded = params(Side.CORE, Kind.BREAKER, Mode.WARDED, 0.6, 3, 5);
        int[] o = draw(open), w = draw(warded);
        int changedOnProgress = 0;
        for (int x = LINE_X; x < LINE_X + LINE_LEN; x++) {
            for (int y = PROGRESS_Y; y < PROGRESS_Y + 5; y++) if (o[y * W + x] != w[y * W + x]) changedOnProgress++;
            for (int y = FUEL_Y; y < FUEL_Y + 2; y++) assertEquals(o[y * W + x], w[y * W + x], "fuel line untouched at " + x + "," + y);
        }
        assertTrue(changedOnProgress > LINE_LEN * 3, "the field lies over the progress line: " + changedOnProgress);
    }

    @Test
    void aDormantSiegeGoesGrey() {
        int[] img = draw(params(Side.ANCHOR, Kind.BREAKER, Mode.DORMANT, 0.5, 0, 5));
        int lit = at(img, LINE_X + 20, PROGRESS_Y + 2);
        int spread = Math.max(r(lit), Math.max(g(lit), b(lit))) - Math.min(r(lit), Math.min(g(lit), b(lit)));
        assertTrue(spread < 40, "grey, not ember: " + Integer.toHexString(lit));
    }

    @Test
    void theBannerWidensForALongTitle() {
        assertEquals(64, bannerHalfWidth(95));
        assertEquals(76, bannerHalfWidth(118));
    }

    @Test
    void textSitsWhereTheArtLeavesRoomForIt() {
        Layout l = SiegeBarArt.layout(params(Side.ANCHOR, Kind.BREACHER, Mode.WARDED, 0.5, 3, 5));
        assertEquals(XC - 95 / 2, l.titleX());
        assertEquals(XC - 66, l.pctX());
        assertEquals(XC - 66 + 18 + 7 + 8, l.timeX(), "the shield sits between percent and time");
        assertEquals(XC + 66 - 30, l.fuelX());
        Layout open = SiegeBarArt.layout(params(Side.ANCHOR, Kind.BREACHER, Mode.ACTIVE, 0.5, 3, 5));
        assertEquals(XC - 66 + 18 + 7, open.timeX());
    }

    private static boolean isLapis(int c) { return a(c) == 255 && b(c) > 120 && b(c) > r(c) + 60; }

    private static int litRuns(int[] img) {
        int runs = 0; boolean in = false;
        for (int k = 0; k < LINE_LEN; k++) {
            boolean lit = isLapis(at(img, LINE_X + k, FUEL_Y + 1));
            if (lit && !in) runs++;
            in = lit;
        }
        return runs;
    }
}
