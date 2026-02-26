package com.pocketdimensions.client;

import com.pocketdimensions.blockentity.WorldCoreBlockEntity;
import com.pocketdimensions.init.ModBlockEntityTypes;
import net.minecraftforge.client.event.EntityRenderersEvent;

/**
 * Client-only setup. Only instantiated when running on the client
 * (guarded in PocketDimensionsMod via FMLEnvironment.dist.isClient()).
 */
public class ClientSetup {

    public ClientSetup() {
        EntityRenderersEvent.RegisterRenderers.BUS.addListener(this::onRegisterRenderers);
    }

    private void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.<WorldCoreBlockEntity, WorldCoreRenderState>registerBlockEntityRenderer(
                ModBlockEntityTypes.WORLD_CORE.get(),
                ctx -> new WorldCoreBlockEntityRenderer(ctx));
    }
}
