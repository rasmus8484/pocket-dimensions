package com.pocketdimensions.block;

import com.pocketdimensions.manager.VeilLayout;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

/**
 * The Veil: the realm's ward made visible, a wall of drifting mist along every edge of a realm from the bottom of the
 * world to the top. It can't be broken, blown up or moved, and it hides whatever lies beyond. BAND picks its colour by
 * height (VeilLayout); RealmChunkGenerator raises it as the realm generates. Players never get it as an item.
 */
public class VeilBlock extends Block {

    public static final IntegerProperty BAND = IntegerProperty.create("band", 0, VeilLayout.BANDS - 1);

    public VeilBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(BAND, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(BAND);
    }

    /** The Veil block for a height band. */
    public BlockState state(int band) {
        return defaultBlockState().setValue(BAND, band);
    }
}
