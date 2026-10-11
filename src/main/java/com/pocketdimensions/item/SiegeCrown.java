package com.pocketdimensions.item;

/** Where a siege block crowns a World Anchor (pure logic, tested; SiegeBlockItem uses it). */
public final class SiegeCrown {

    private SiegeCrown() {}

    /** The height of the block that crowns an anchor, given the height and half of the anchor block clicked. */
    public static int crownY(int clickedY, boolean upperHalf) {
        return upperHalf ? clickedY + 1 : clickedY + 2;
    }
}
