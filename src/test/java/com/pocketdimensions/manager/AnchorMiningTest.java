package com.pocketdimensions.manager;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** Who is mining which Pocket Anchor, how far along they are, and how many sets of cracks the room shows. */
class AnchorMiningTest {

    private static final UUID A = new UUID(0, 1), B = new UUID(0, 2);

    @Test
    void startingRecordsTheDigAndStoppingForgetsIt() {
        AnchorMining<String> m = new AnchorMining<>();
        m.start(A, "anchor", 100);
        assertEquals(new AnchorMining.Dig<>("anchor", 100), m.digs().get(A));
        m.stop(A);
        assertTrue(m.digs().isEmpty());
    }

    @Test
    void aMinerDigsOneBlockAtATime() {
        AnchorMining<String> m = new AnchorMining<>();
        m.start(A, "first", 100);
        m.start(A, "second", 140);
        assertEquals(1, m.digs().size());
        assertEquals(new AnchorMining.Dig<>("second", 140), m.digs().get(A), "starting on another block restarts the clock");
    }

    @Test
    void twoMinersCanWorkTheSameAnchor() {
        AnchorMining<String> m = new AnchorMining<>();
        m.start(A, "anchor", 100);
        m.start(B, "anchor", 120);
        m.stop(A);
        assertEquals(1, m.digs().size(), "B is still at it");
    }

    @Test
    void progressCountsTheWayTheServerDoes() {
        // ServerPlayerGameMode: progress per tick * (ticks since start + 1)
        assertEquals(0.5f, AnchorMining.progress(0.05f, 100, 109), 1e-6);
        assertEquals(0.05f, AnchorMining.progress(0.05f, 100, 100), 1e-6, "the first tick already counts");
    }

    @Test
    void aSetOfCracksAppearsEveryQuarterAndNeverMoreThanThree() {
        assertEquals(0, AnchorMining.cracks(0f));
        assertEquals(0, AnchorMining.cracks(0.249f));
        assertEquals(1, AnchorMining.cracks(0.25f));
        assertEquals(2, AnchorMining.cracks(0.5f));
        assertEquals(3, AnchorMining.cracks(0.75f));
        assertEquals(3, AnchorMining.cracks(1.4f), "the anchor breaks at 100 %; the room never shows a fourth set");
    }
}
