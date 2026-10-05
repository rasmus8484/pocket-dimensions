package com.pocketdimensions.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.pocketdimensions.block.WorldAnchorBlock;
import com.pocketdimensions.blockentity.WorldAnchorBlockEntity;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

/** Draws the linked World Anchor's black hole (shared BlackHoleRenderer), beam and seed. */
public class WorldAnchorBlockEntityRenderer implements BlockEntityRenderer<WorldAnchorBlockEntity, WorldAnchorRenderState> {

    private static final float PX = 1f / 16f;
    private static final float SC = 13.5f;
    /** Beam colour (always the calm cyan). */
    private static final float[] GLOW = {110 / 255f, 210 / 255f, 1f};

    /** Face order shared by SEED_DIRS and cubeFace: -z, +z, -x, +x, +y, -y. */
    private static final int[][] SEED_DIRS = {{0, 0, -1}, {0, 0, 1}, {-1, 0, 0}, {1, 0, 0}, {0, 1, 0}, {0, -1, 0}};

    private static final List<int[]> SEED = seedVoxels();

    public WorldAnchorBlockEntityRenderer(BlockEntityRendererProvider.Context ctx) {}

    @Override
    public WorldAnchorRenderState createRenderState() { return new WorldAnchorRenderState(); }

    @Override
    public void extractRenderState(WorldAnchorBlockEntity be, WorldAnchorRenderState s, float partialTick,
                                   Vec3 cameraPos, ModelFeatureRenderer.CrumblingOverlay crumbling) {
        BlockEntityRenderState.extractBase(be, s, crumbling);
        s.linked = be.getBlockState().getValue(WorldAnchorBlock.LINKED);
        // Under siege (a breacher is attached): the accretion glow turns ember
        var st = be.getBlockState();
        s.palette = st.getValue(WorldAnchorBlock.INFLUENCE) > 0 || st.getValue(WorldAnchorBlock.DAMAGE) > 0 ? BlackHoleRenderer.RingPalette.EMBER : BlackHoleRenderer.RingPalette.GLOW;
        // Wrap before converting to float: past ~2^24 ticks a float can no longer hold the partial tick and animation stutters.
        s.time = be.getLevel() == null ? 0 : (Math.floorMod(be.getLevel().getGameTime(), 24000L * 20) + partialTick) / 20f;
    }

    @Override
    public void submit(WorldAnchorRenderState s, PoseStack pose, SubmitNodeCollector out, CameraRenderState camera) {
        if (!s.linked) return;
        float t = s.time;
        BlackHoleRenderer.submit(pose, out, camera, 0.5f, SC * PX, 0.5f, s.palette, t);

        // Beam from the cavity roof up to the seed
        float beamA = 0.55f + 0.35f * (0.5f + 0.5f * (float) Math.sin(t * 1.4));
        pose.pushPose();
        pose.translate(0.5f, 20.7f * PX, 0.5f);
        out.submitCustomGeometry(pose, RenderTypes.lightning(), (p, vc) -> box(vc, p.pose(), 0.6f * PX, 3f * PX, GLOW, beamA));
        pose.popPose();

        // Seed: floats and turns in the claws
        pose.pushPose();
        pose.translate(0.5f, (27.5f + 0.6f * (float) Math.sin(t * 1.4)) * PX, 0.5f);
        pose.mulPose(com.mojang.math.Axis.YP.rotation(t * 0.7f));
        out.submitCustomGeometry(pose, RenderTypes.lightning(), (p, vc) -> {
            for (int[] f : SEED) {
                float k = (f[1] + 4) / 7f;
                float[] c = {(40 + k * 120) / 255f, (200 + k * 55) / 255f, (140 + k * 75) / 255f};
                cubeFace(vc, p.pose(), f[0], f[1], f[2], f[3], c);
            }
        });
        pose.popPose();
    }

    // ---- geometry helpers -------------------------------------------------------------------

    /** Axis-aligned box centred on x/z, bottom at y=0. */
    private static void box(VertexConsumer vc, Matrix4f m, float half, float h, float[] c, float a) {
        float[][] q = {
            {-half, 0, -half, half, 0, -half, half, h, -half, -half, h, -half},
            {half, 0, half, -half, 0, half, -half, h, half, half, h, half},
            {-half, 0, half, -half, 0, -half, -half, h, -half, -half, h, half},
            {half, 0, -half, half, 0, half, half, h, half, half, h, -half}};
        for (float[] f : q) for (int i = 0; i < 4; i++) vc.addVertex(m, f[i * 3], f[i * 3 + 1], f[i * 3 + 2]).setColor(c[0], c[1], c[2], a);
    }

    /** One outward face of a seed voxel (pixel units, centred). */
    private static void cubeFace(VertexConsumer vc, Matrix4f m, int x, int y, int z, int face, float[] c) {
        float x0 = x * PX, x1 = (x + 1) * PX, y0 = y * PX, y1 = (y + 1) * PX, z0 = z * PX, z1 = (z + 1) * PX;
        float[][] q = {
            {x0, y0, z0, x0, y1, z0, x1, y1, z0, x1, y0, z0}, {x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1},
            {x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0}, {x1, y0, z0, x1, y1, z0, x1, y1, z1, x1, y0, z1},
            {x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0}, {x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1}};
        float[] f = q[face];
        for (int i = 0; i < 4; i++) vc.addVertex(m, f[i * 3], f[i * 3 + 1], f[i * 3 + 2]).setColor(c[0], c[1], c[2], 1f);
    }

    // ---- static shape tables (pixel units, centred on the black hole) -------------------------

    private static boolean inSeed(int i, int j, int k) {
        return Math.abs(i + .5) + Math.abs(j + .5) * 0.75 + Math.abs(k + .5) < 2.7;
    }

    /**
     * Exposed faces of the gem-shaped seed {x, y, z, face}, centred. Inner faces are skipped: with additive
     * blending they would stack and wash the gem out to white.
     */
    private static List<int[]> seedVoxels() {
        List<int[]> faces = new ArrayList<>();
        for (int i = -3; i < 3; i++) for (int j = -4; j < 4; j++) for (int k = -3; k < 3; k++) {
            if (!inSeed(i, j, k)) continue;
            for (int f = 0; f < 6; f++) {
                int[] d = SEED_DIRS[f];
                if (!inSeed(i + d[0], j + d[1], k + d[2])) faces.add(new int[]{i, j, k, f});
            }
        }
        return faces;
    }

    @Override
    public boolean shouldRenderOffScreen() { return true; }
}
