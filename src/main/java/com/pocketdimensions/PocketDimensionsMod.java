package com.pocketdimensions;

import com.pocketdimensions.init.ModBlockEntityTypes;
import com.pocketdimensions.init.ModBlocks;
import com.pocketdimensions.init.ModCreativeTabs;
import com.pocketdimensions.init.ModItems;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;

@Mod(PocketDimensionsMod.MODID)
public class PocketDimensionsMod {

    public static final String MODID = "pocketdimensions";

    public PocketDimensionsMod(IEventBus modEventBus) {
        ModBlocks.BLOCKS.register(modEventBus);
        ModItems.ITEMS.register(modEventBus);
        ModBlockEntityTypes.BLOCK_ENTITY_TYPES.register(modEventBus);
        ModCreativeTabs.CREATIVE_TABS.register(modEventBus);
    }
}
