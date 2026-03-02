package com.pocketdimensions.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.pocketdimensions.blockentity.WorldCoreBlockEntity;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BeaconRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Renders a beacon beam rising from the WorldCore block.
 * Beam colour reflects siege state: blue = normal, pink = breaching, red = breaking.
 * The beam stops at the first non-air block above.
 */
public class WorldCoreBlockEntityRenderer implements BlockEntityRenderer<WorldCoreBlockEntity, WorldCoreRenderState> {

    public WorldCoreBlockEntityRenderer(BlockEntityRendererProvider.Context ctx) {}

    @Override
    public WorldCoreRenderState createRenderState() {
        return new WorldCoreRenderState();
    }

    @Override
    public void extractRenderState(WorldCoreBlockEntity be, WorldCoreRenderState state,
                                   float partialTick, Vec3 cameraPos,
                                   ModelFeatureRenderer.CrumblingOverlay crumbling) {
        BlockEntityRenderState.extractBase(be, state, crumbling);
        Level level = be.getLevel();
        if (level == null) {
            state.beamHeight = 0;
            return;
        }
        BlockPos pos = be.getBlockPos();
        int h = 0;
        for (int i = 1; i <= 256; i++) {
            if (!level.getBlockState(pos.above(i)).isAir()) break;
            h = i;
        }
        state.beamHeight = h;
        state.animationTime = level.getGameTime() + partialTick;
        state.beamColor = be.getBeamColor();
    }

    @Override
    public void submit(WorldCoreRenderState state, PoseStack poseStack,
                       SubmitNodeCollector collector, CameraRenderState camera) {
        if (state.beamHeight == 0) return;
        BeaconRenderer.submitBeaconBeam(
                poseStack, collector,
                BeaconRenderer.BEAM_LOCATION,
                1.0f,                          // textureScale
                state.animationTime,           // animationTime (drives rotation)
                0,                             // yOffset
                state.beamHeight + 1,          // height
                state.beamColor,               // color (ARGB)
                BeaconRenderer.SOLID_BEAM_RADIUS,
                BeaconRenderer.BEAM_GLOW_RADIUS);
    }

    @Override
    public boolean shouldRenderOffScreen() {
        return true;
    }
}
