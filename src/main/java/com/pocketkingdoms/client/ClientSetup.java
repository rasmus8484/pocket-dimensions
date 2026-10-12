package com.pocketkingdoms.client;

import com.pocketkingdoms.client.particle.DrainParticle;
import com.pocketkingdoms.client.particle.HelixRuneParticle;
import com.pocketkingdoms.client.particle.RuneParticle;
import com.pocketkingdoms.init.ModParticles;
import net.minecraftforge.client.event.RegisterParticleProvidersEvent;
import com.pocketkingdoms.blockentity.AnchorBreakerBlockEntity;
import com.pocketkingdoms.blockentity.PocketAnchorBlockEntity;
import com.pocketkingdoms.blockentity.WorldBreacherBlockEntity;
import com.pocketkingdoms.blockentity.WorldAnchorBlockEntity;
import com.pocketkingdoms.blockentity.WorldCoreBlockEntity;
import com.pocketkingdoms.client.screen.SiegeBlockScreen;
import com.pocketkingdoms.client.screen.WorldCoreScreen;
import com.pocketkingdoms.init.ModBlockEntityTypes;
import com.pocketkingdoms.init.ModMenuTypes;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.bus.BusGroup;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/**
 * Client-only setup. Only instantiated when running on the client
 * (guarded in PocketKingdomsMod via FMLEnvironment.dist.isClient()).
 */
public class ClientSetup {

    public ClientSetup(BusGroup modBusGroup) {
        EntityRenderersEvent.RegisterRenderers.BUS.addListener(this::onRegisterRenderers);
        FMLClientSetupEvent.getBus(modBusGroup).addListener(this::onClientSetup);
        com.pocketkingdoms.client.siegebar.SiegeBarClient.register();
        ItemTooltips.register();
        WardClient.register();
        com.pocketkingdoms.client.ward.WardSigilRenderer.register();
        com.pocketkingdoms.client.veil.VeilFogRenderer.register();
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
        event.<com.pocketkingdoms.blockentity.RoomVoidBlockEntity, RoomVoidRenderState>registerBlockEntityRenderer(
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
