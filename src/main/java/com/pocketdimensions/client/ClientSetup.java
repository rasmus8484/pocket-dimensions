package com.pocketdimensions.client;

import com.pocketdimensions.client.particle.DrainParticle;
import com.pocketdimensions.client.particle.HelixRuneParticle;
import com.pocketdimensions.client.particle.RuneParticle;
import com.pocketdimensions.init.ModParticles;
import net.minecraftforge.client.event.RegisterParticleProvidersEvent;
import com.pocketdimensions.blockentity.AnchorBreakerBlockEntity;
import com.pocketdimensions.blockentity.PocketAnchorBlockEntity;
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
        com.pocketdimensions.client.siegebar.SiegeBarClient.register();
        RegisterParticleProvidersEvent.BUS.addListener(e -> {
            e.registerSpriteSet(ModParticles.RUNE.get(), s -> new RuneParticle.Provider(s, RuneParticle.CYAN));
            e.registerSpriteSet(ModParticles.RUNE_PINK.get(), s -> new RuneParticle.Provider(s, RuneParticle.PINK));
            e.registerSpriteSet(ModParticles.RUNE_GOLD.get(), s -> new RuneParticle.Provider(s, RuneParticle.GOLD));
            e.registerSpriteSet(ModParticles.RUNE_RED.get(), s -> new RuneParticle.Provider(s, RuneParticle.RED));
            e.registerSpriteSet(ModParticles.RUNE_HELIX.get(), HelixRuneParticle.Provider::new);
            e.registerSpriteSet(ModParticles.DRAIN.get(), s -> new DrainParticle.Provider(s, DrainParticle.PINK, DrainParticle.Path.DRAIN));
            e.registerSpriteSet(ModParticles.UNMAKE.get(), s -> new DrainParticle.Provider(s, DrainParticle.RED, DrainParticle.Path.OUT));
            e.registerSpriteSet(ModParticles.SIPHON.get(), s -> new DrainParticle.Provider(s, DrainParticle.RED, DrainParticle.Path.STRAIGHT));
        });
    }

    private void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.<WorldCoreBlockEntity, GeodeRenderState>registerBlockEntityRenderer(
                ModBlockEntityTypes.WORLD_CORE.get(),
                ctx -> new WorldCoreBlockEntityRenderer(ctx));
        event.<WorldAnchorBlockEntity, WorldAnchorRenderState>registerBlockEntityRenderer(
                ModBlockEntityTypes.WORLD_ANCHOR.get(),
                ctx -> new WorldAnchorBlockEntityRenderer(ctx));
        event.<AnchorBreakerBlockEntity, AnchorBreakerRenderState>registerBlockEntityRenderer(
                ModBlockEntityTypes.ANCHOR_BREAKER.get(),
                ctx -> new AnchorBreakerBlockEntityRenderer(ctx));
        event.<com.pocketdimensions.blockentity.RoomVoidBlockEntity, net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState>registerBlockEntityRenderer(
                ModBlockEntityTypes.ROOM_VOID.get(),
                ctx -> new RoomVoidRenderer(ctx));
        event.<PocketAnchorBlockEntity, PocketAnchorRenderState>registerBlockEntityRenderer(
                ModBlockEntityTypes.POCKET_ANCHOR.get(),
                ctx -> new PocketAnchorRenderer(ctx));
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
