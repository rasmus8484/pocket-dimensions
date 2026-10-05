package com.pocketdimensions.block;

import com.pocketdimensions.SiegeTuning;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Mining rule shared by the Pocket Anchor, World Breacher and Anchor Breaker: only a pickaxe of diamond tier or better
 * (vanilla or mod-added) can break them, and then it takes a fixed time from the server config. Anything else can't.
 */
final class DiamondPickaxeMining {

    private DiamondPickaxeMining() {}

    static float progressPerTick(Player player, BlockState state, double seconds) {
        ItemStack tool = player.getMainHandItem();
        // the block is in needs_diamond_tool, so isCorrectToolForDrops is true only for diamond tier and up
        return tool.is(ItemTags.PICKAXES) && tool.isCorrectToolForDrops(state) ? SiegeTuning.mineProgressPerTick(seconds) : 0f;
    }
}
