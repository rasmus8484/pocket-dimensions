package com.pocketdimensions.client;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class TooltipLinesTest {

    private static final Set<String> LANG = Set.of(
            "tooltip.pocketdimensions.world_anchor.0", "tooltip.pocketdimensions.world_anchor.1",
            "tooltip.pocketdimensions.world_anchor.more.0", "tooltip.pocketdimensions.world_anchor.more.1",
            "tooltip.pocketdimensions.world_core.0");

    @Test
    void theSummaryAndAHintShowWithoutShift() {
        assertEquals(List.of("tooltip.pocketdimensions.world_anchor.0", "tooltip.pocketdimensions.world_anchor.1",
                        TooltipLines.HOLD_SHIFT),
                TooltipLines.keys("world_anchor", false, LANG::contains));
    }

    @Test
    void holdingShiftAddsTheDetailsInsteadOfTheHint() {
        assertEquals(List.of("tooltip.pocketdimensions.world_anchor.0", "tooltip.pocketdimensions.world_anchor.1",
                        "tooltip.pocketdimensions.world_anchor.more.0", "tooltip.pocketdimensions.world_anchor.more.1"),
                TooltipLines.keys("world_anchor", true, LANG::contains));
    }

    @Test
    void anItemWithNoDetailsGetsNoHint() {
        assertEquals(List.of("tooltip.pocketdimensions.world_core.0"), TooltipLines.keys("world_core", false, LANG::contains));
    }

    @Test
    void anItemWithNoLinesGetsNothing() {
        assertEquals(List.of(), TooltipLines.keys("boundary_block", true, LANG::contains));
    }
}
