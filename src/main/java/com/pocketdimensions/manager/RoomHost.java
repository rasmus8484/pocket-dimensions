package com.pocketdimensions.manager;

import org.jetbrains.annotations.Nullable;

/**
 * Which dimension a pocket room belongs to. Lore-wise the room is folded space inside the world around its anchor, not
 * a world of its own, so it takes that world's nights and sleeps with that world's players: the anchor's dimension when
 * it is placed, else (the anchor carried off) where its occupant came in from, else the overworld. Pure, tested.
 */
public final class RoomHost {

    private RoomHost() {}

    public static <T> T pick(@Nullable T anchorDimension, @Nullable T entryDimension, T fallback) {
        if (anchorDimension != null) return anchorDimension;
        return entryDimension != null ? entryDimension : fallback;
    }
}
