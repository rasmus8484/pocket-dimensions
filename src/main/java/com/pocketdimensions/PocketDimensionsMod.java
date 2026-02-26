package com.pocketdimensions;

import com.pocketdimensions.client.ClientSetup;
import com.pocketdimensions.event.PocketEventHandler;
import com.pocketdimensions.event.RealmEventHandler;
import com.pocketdimensions.init.ModBlockEntityTypes;
import com.pocketdimensions.init.ModBlocks;
import com.pocketdimensions.init.ModCreativeTabs;
import com.pocketdimensions.init.ModItems;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.Level;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;

@Mod(PocketDimensionsMod.MODID)
public class PocketDimensionsMod {

    public static final String MODID = "pocketdimensions";

    public static final ResourceKey<Level> POCKET_DIM = ResourceKey.create(
            Registries.DIMENSION,
            Identifier.fromNamespaceAndPath(MODID, "pocket")
    );

    public static final ResourceKey<Level> REALM_DIM = ResourceKey.create(
            Registries.DIMENSION,
            Identifier.fromNamespaceAndPath(MODID, "realm")
    );

    public PocketDimensionsMod(FMLJavaModLoadingContext context) {
        var modBusGroup = context.getModBusGroup();
        ModBlocks.BLOCKS.register(modBusGroup);
        ModItems.ITEMS.register(modBusGroup);
        ModBlockEntityTypes.BLOCK_ENTITY_TYPES.register(modBusGroup);
        ModCreativeTabs.CREATIVE_TABS.register(modBusGroup);

        new PocketEventHandler();
        new RealmEventHandler();

        if (FMLEnvironment.dist.isClient()) {
            new ClientSetup();
        }
    }
}
