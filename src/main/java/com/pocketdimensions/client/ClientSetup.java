package com.pocketdimensions.client;

import com.pocketdimensions.blockentity.WorldBreacherBlockEntity;
import com.pocketdimensions.blockentity.WorldCoreBlockEntity;
import com.pocketdimensions.client.screen.SiegeBlockScreen;
import com.pocketdimensions.client.screen.WorldCoreScreen;
import com.pocketdimensions.init.ModBlockEntityTypes;
import com.pocketdimensions.init.ModMenuTypes;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.bus.BusGroup;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/**
 * Client-only setup. Only instantiated when running on the client
 * (guarded in PocketDimensionsMod via FMLEnvironment.dist.isClient()).
 */
public class ClientSetup {

    public ClientSetup(BusGroup modBusGroup) {
        EntityRenderersEvent.RegisterRenderers.BUS.addListener(this::onRegisterRenderers);
        FMLClientSetupEvent.getBus(modBusGroup).addListener(this::onClientSetup);
    }

    private void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.<WorldCoreBlockEntity, WorldCoreRenderState>registerBlockEntityRenderer(
                ModBlockEntityTypes.WORLD_CORE.get(),
                ctx -> new WorldCoreBlockEntityRenderer(ctx));
        event.<WorldBreacherBlockEntity, WorldCoreRenderState>registerBlockEntityRenderer(
                ModBlockEntityTypes.WORLD_BREACHER.get(),
                ctx -> new WorldBreacherBlockEntityRenderer(ctx));
    }

    private void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            MenuScreens.register(ModMenuTypes.WORLD_CORE.get(), WorldCoreScreen::new);
            MenuScreens.register(ModMenuTypes.SIEGE_BLOCK.get(), SiegeBlockScreen::new);
        });
    }
}
