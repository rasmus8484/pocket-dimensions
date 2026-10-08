package com.pocketdimensions.client;

import com.pocketdimensions.block.WorldBreacherBlock;
import com.mojang.blaze3d.vertex.PoseStack;
import com.pocketdimensions.blockentity.WorldBreacherBlockEntity;
import net.minecraft.client.Minecraft;
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
 * Renders a pink beacon beam rising out of the WorldBreacher's eye while the breach is complete and fueled.
 * Reuses WorldCoreRenderState (beamHeight, animationTime, beamColor).
 */
public class WorldBreacherBlockEntityRenderer implements BlockEntityRenderer<WorldBreacherBlockEntity, WorldCoreRenderState> {

    /** Pink beacon beam colour (ARGB). */
    private static final int PINK = 0xFFFF44FF;

    public WorldBreacherBlockEntityRenderer(BlockEntityRendererProvider.Context ctx) {}

    @Override
    public WorldCoreRenderState createRenderState() {
        return new WorldCoreRenderState();
    }

    @Override
    public void extractRenderState(WorldBreacherBlockEntity be, WorldCoreRenderState state,
                                   float partialTick, Vec3 cameraPos,
                                   ModelFeatureRenderer.CrumblingOverlay crumbling) {
        BlockEntityRenderState.extractBase(be, state, crumbling);

        // The beam shows the breach is open: complete (same rule as realm access; it needs no fuel once open)
        // COMPLETE is a synced block state; isBreachComplete() would use the client's own (unsynced) config value
        if (!be.getBlockState().getValue(WorldBreacherBlock.COMPLETE)) {
            state.beamHeight = 0;
            return;
        }

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
        state.beamColor = PINK;
    }

    @Override
    public void submit(WorldCoreRenderState state, PoseStack poseStack,
                       SubmitNodeCollector collector, CameraRenderState camera) {
        if (state.beamHeight == 0) return;
        poseStack.pushPose();
        poseStack.translate(0f, 0.75f, 0f);   // rise out of the Mandible's eye
        BeaconRenderer.submitBeaconBeam(
                poseStack, collector,
                BeaconRenderer.BEAM_LOCATION,
                1.0f,
                state.animationTime,
                0,
                state.beamHeight,
                state.beamColor,
                ThinBeam.SOLID_RADIUS,
                ThinBeam.GLOW_RADIUS);
        poseStack.popPose();
    }

    @Override
    public boolean shouldRenderOffScreen() {
        return true;
    }

    @Override
    public int getViewDistance() {
        return Minecraft.getInstance().options.getEffectiveRenderDistance() * 16;
    }

    @Override
    public boolean shouldRender(WorldBreacherBlockEntity be, Vec3 cameraPos) {
        return Vec3.atCenterOf(be.getBlockPos()).multiply(1.0, 0.0, 1.0)
                .closerThan(cameraPos.multiply(1.0, 0.0, 1.0), this.getViewDistance());
    }
}
