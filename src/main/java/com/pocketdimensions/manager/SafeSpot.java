package com.pocketdimensions.manager;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.function.Predicate;

/**
 * Where to put a player arriving somewhere (a pocket room, a realm): the nearest spot to a preferred one with room for
 * feet and head and something to stand on. Shared so every arrival uses the same rules; the search itself is
 * SpawnSearch (plain integers, unit tested).
 */
public final class SafeSpot {

    private SafeSpot() {}

    /**
     * The nearest standable spot to preferred inside min..max (inclusive), skipping any cell excluded says no to, or
     * null if there is nowhere to stand.
     */
    @Nullable
    public static BlockPos nearest(ServerLevel level, BlockPos preferred, BlockPos min, BlockPos max, Predicate<BlockPos> excluded) {
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        int[] p = SpawnSearch.find(preferred.getX(), preferred.getY(), preferred.getZ(),
                min.getX(), min.getY(), min.getZ(), max.getX(), max.getY(), max.getZ(),
                (x, y, z) -> !excluded.test(m.set(x, y, z)) && isFreeToStandIn(level, m),
                (x, y, z) -> !level.getBlockState(m.set(x, y, z)).getCollisionShape(level, m).isEmpty());
        return p == null ? null : new BlockPos(p[0], p[1], p[2]);
    }

    /** Nothing to collide with, no fluid, no fire. */
    public static boolean isFreeToStandIn(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.getCollisionShape(level, pos).isEmpty() && state.getFluidState().isEmpty() && !state.is(BlockTags.FIRE);
    }

    /** Last resort: break the two blocks at pos (they drop as items) so there is room to stand. */
    public static BlockPos breakOpen(ServerLevel level, BlockPos pos) {
        level.destroyBlock(pos, true);
        level.destroyBlock(pos.above(), true);
        return pos;
    }
}
