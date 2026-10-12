package com.pocketkingdoms;

import com.pocketkingdoms.client.ClientSetup;
import com.pocketkingdoms.command.PocketKingdomsCommand;
import com.pocketkingdoms.event.PocketEventHandler;
import com.pocketkingdoms.event.RealmEventHandler;
import com.pocketkingdoms.init.ModBlockEntityTypes;
import com.pocketkingdoms.init.ModBlocks;
import com.pocketkingdoms.init.ModChunkGenerators;
import com.pocketkingdoms.init.ModCreativeTabs;
import com.pocketkingdoms.init.ModItems;
import com.pocketkingdoms.init.ModMenuTypes;
import com.pocketkingdoms.init.ModParticles;
import com.pocketkingdoms.init.ModSounds;
import com.pocketkingdoms.network.ModNetworking;
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

@Mod(PocketKingdomsMod.MODID)
public class PocketKingdomsMod {

    public static final String MODID = "pocketkingdoms";

    public static final ResourceKey<Level> POCKET_DIM = ResourceKey.create(
            Registries.DIMENSION,
            Identifier.fromNamespaceAndPath(MODID, "pocket")
    );

    public static final ResourceKey<Level> REALM_DIM = ResourceKey.create(
            Registries.DIMENSION,
            Identifier.fromNamespaceAndPath(MODID, "realm")
    );

    public PocketKingdomsMod(FMLJavaModLoadingContext context) {
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, PocketKingdomsConfig.SPEC,
                "pocketkingdoms-common.toml");
        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, PocketKingdomsServerConfig.SPEC,
                "pocketkingdoms-server.toml");
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, PocketKingdomsClientConfig.SPEC,
                "pocketkingdoms-client.toml");

        var modBusGroup = context.getModBusGroup();
        ModBlocks.BLOCKS.register(modBusGroup);
        ModItems.ITEMS.register(modBusGroup);
        ModBlockEntityTypes.BLOCK_ENTITY_TYPES.register(modBusGroup);
        ModCreativeTabs.CREATIVE_TABS.register(modBusGroup);
        ModChunkGenerators.CHUNK_GENERATORS.register(modBusGroup);
        ModMenuTypes.MENU_TYPES.register(modBusGroup);
        ModParticles.PARTICLE_TYPES.register(modBusGroup);
        ModSounds.SOUND_EVENTS.register(modBusGroup);
        com.pocketkingdoms.init.ModTriggers.TRIGGERS.register(modBusGroup);

        ModNetworking.register();

        new PocketEventHandler();
        new com.pocketkingdoms.event.AnchorMiningHandler();
        new RealmEventHandler();
        new com.pocketkingdoms.event.WardSync();
        new com.pocketkingdoms.event.PocketSleepHandler();
        RegisterCommandsEvent.BUS.addListener(event ->
                PocketKingdomsCommand.register(event.getDispatcher()));

        if (FMLEnvironment.dist.isClient()) {
            new ClientSetup(modBusGroup);
        }
    }
}
