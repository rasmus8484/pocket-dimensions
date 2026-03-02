package com.pocketdimensions;

import com.pocketdimensions.client.ClientSetup;
import com.pocketdimensions.command.PocketDimensionsCommand;
import com.pocketdimensions.event.PocketEventHandler;
import com.pocketdimensions.event.RealmEventHandler;
import com.pocketdimensions.init.ModBlockEntityTypes;
import com.pocketdimensions.init.ModBlocks;
import com.pocketdimensions.init.ModChunkGenerators;
import com.pocketdimensions.init.ModCreativeTabs;
import com.pocketdimensions.init.ModItems;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
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
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, PocketDimensionsConfig.SPEC,
                "pocketdimensions-common.toml");

        var modBusGroup = context.getModBusGroup();
        ModBlocks.BLOCKS.register(modBusGroup);
        ModItems.ITEMS.register(modBusGroup);
        ModBlockEntityTypes.BLOCK_ENTITY_TYPES.register(modBusGroup);
        ModCreativeTabs.CREATIVE_TABS.register(modBusGroup);
        ModChunkGenerators.CHUNK_GENERATORS.register(modBusGroup);

        new PocketEventHandler();
        new RealmEventHandler();
        RegisterCommandsEvent.BUS.addListener(event ->
                PocketDimensionsCommand.register(event.getDispatcher()));

        if (FMLEnvironment.dist.isClient()) {
            new ClientSetup();
        }
    }
}
