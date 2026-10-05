package com.pocketdimensions.client;

import com.pocketdimensions.client.particle.DrainParticle;
import com.pocketdimensions.client.particle.RuneParticle;
import com.pocketdimensions.init.ModParticles;
import net.minecraftforge.client.event.RegisterParticleProvidersEvent;
import com.pocketdimensions.blockentity.WorldBreacherBlockEntity;
import com.pocketdimensions.blockentity.WorldAnchorBlockEntity;
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
        RegisterParticleProvidersEvent.BUS.addListener(e -> {
            e.registerSpriteSet(ModParticles.RUNE.get(), s -> new RuneParticle.Provider(s, RuneParticle.CYAN));
            e.registerSpriteSet(ModParticles.RUNE_PINK.get(), s -> new RuneParticle.Provider(s, RuneParticle.PINK));
            e.registerSpriteSet(ModParticles.RUNE_GOLD.get(), s -> new RuneParticle.Provider(s, RuneParticle.GOLD));
            e.registerSpriteSet(ModParticles.DRAIN.get(), DrainParticle.Provider::new);
        });
    }

    private void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.<WorldCoreBlockEntity, WorldCoreRenderState>registerBlockEntityRenderer(
                ModBlockEntityTypes.WORLD_CORE.get(),
                ctx -> new WorldCoreBlockEntityRenderer(ctx));
        event.<WorldAnchorBlockEntity, WorldAnchorRenderState>registerBlockEntityRenderer(
                ModBlockEntityTypes.WORLD_ANCHOR.get(),
                ctx -> new WorldAnchorBlockEntityRenderer(ctx));
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
