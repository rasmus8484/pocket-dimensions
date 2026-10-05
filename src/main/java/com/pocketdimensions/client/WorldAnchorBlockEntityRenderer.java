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

/** Draws the linked World Anchor's black hole (rift sphere, photon ring, accretion disk), beam and seed. */
public class WorldAnchorBlockEntityRenderer implements BlockEntityRenderer<WorldAnchorBlockEntity, WorldAnchorRenderState> {

    private static final float PX = 1f / 16f;
    private static final float SC = 13.5f;
    private static final float[] DEEP = {40 / 255f, 110 / 255f, 230 / 255f};
    /** Disk spin in radians per second; the ring shimmer completes one lap in the same time. */
    private static final float SPIN = 0.6f;

    /**
     * How over-bright ring colours become bytes.
     * GLOW  - clamped: the cool blue glow of the approved concept (default).
     * EMBER - wrapped like an unclamped byte cast: channels pushed past 255 roll over to near zero,
     *         turning the rings a flickering red-yellow, like a real black hole's accretion glow.
     *         Found by accident in the first build and kept on purpose for the siege look.
     * Chosen per anchor through WorldAnchorRenderState.palette.
     */
    public enum RingPalette {
        GLOW(new float[]{110 / 255f, 210 / 255f, 1f}, 0.78f, 0.22f),
        EMBER(new float[]{215 / 255f, 248 / 255f, 1f}, 0.92f * 1.06f, 0.16f * 1.06f);

        /** Inner ring colour; the disk fades from it to DEEP. */
        final float[] glow;
        final float base, spread;

        RingPalette(float[] glow, float base, float spread) { this.glow = glow; this.base = base; this.spread = spread; }

        /** Per-pixel brightness from a 0..1 hash. */
        float shimmer(float h) { return base + spread * h; }

        /** One colour channel (0..1, may exceed 1) to a byte value; EMBER deliberately wraps instead of clamping. */
        int channel(float v) {
            int i = (int) (v * 255f);
            return this == EMBER ? i & 0xFF : Math.max(0, Math.min(255, i));
        }
    }

    /** Beam colour (always the calm cyan). */
    private static final float[] GLOW = RingPalette.GLOW.glow;

    /** Face order shared by SEED_DIRS and cubeFace: -z, +z, -x, +x, +y, -y. */
    private static final int[][] SEED_DIRS = {{0, 0, -1}, {0, 0, 1}, {-1, 0, 0}, {1, 0, 0}, {0, 1, 0}, {0, -1, 0}};

    private static final List<int[]> SPHERE_FACES = blockySphereFaces(3.2);
    private static final List<int[]> RING = ringPixels();
    private static final List<int[]> DISK = diskPixels();
    private static final List<int[]> SEED = seedVoxels();

    public WorldAnchorBlockEntityRenderer(BlockEntityRendererProvider.Context ctx) {}

    @Override
    public WorldAnchorRenderState createRenderState() { return new WorldAnchorRenderState(); }

    @Override
    public void extractRenderState(WorldAnchorBlockEntity be, WorldAnchorRenderState s, float partialTick,
                                   Vec3 cameraPos, ModelFeatureRenderer.CrumblingOverlay crumbling) {
        BlockEntityRenderState.extractBase(be, s, crumbling);
        s.linked = be.getBlockState().getValue(WorldAnchorBlock.LINKED);
        // Wrap before converting to float: past ~2^24 ticks a float can no longer hold the partial tick and animation stutters.
        s.time = be.getLevel() == null ? 0 : (Math.floorMod(be.getLevel().getGameTime(), 24000L * 20) + partialTick) / 20f;
    }

    @Override
    public void submit(WorldAnchorRenderState s, PoseStack pose, SubmitNodeCollector out, CameraRenderState camera) {
        if (!s.linked) return;
        float t = s.time;
        RingPalette pal = s.palette;
        float[] glow = pal.glow;

        // Rift sphere, turned to face the camera like the ring so their pixel edges line up
        pose.pushPose();
        pose.translate(0.5f, SC * PX, 0.5f);
        pose.mulPose(camera.orientation);
        out.submitCustomGeometry(pose, RenderTypes.endGateway(), (p, vc) -> {
            Matrix4f m = p.pose();
            for (int[] f : SPHERE_FACES) {
                for (int i = 0; i < 4; i++) vc.addVertex(m, f[i * 3] * PX, f[i * 3 + 1] * PX, f[i * 3 + 2] * PX);
            }
        });
        // Photon ring: camera-facing, 0.6 px toward the viewer, shimmer shifts one pixel 6x a second
        pose.translate(0f, 0f, 0.6f * PX);
        // Shimmer travels one full lap per disk revolution, so both rings turn at the same rate
        int step = (int) Math.floor(t * SPIN / (2 * Math.PI) * RING.size());
        out.submitCustomGeometry(pose, RenderTypes.lightning(), (p, vc) -> {
            for (int[] px : RING) {
                int slot = Math.floorMod(px[2] - step, RING.size());
                float b = pal.shimmer(hash(slot, 3, 11));
                quadXY(vc, p.pose(), pal, px[0], px[1], glow[0] * b, glow[1] * b, glow[2] * b, 1f);
            }
        });
        pose.popPose();

        // Accretion disk: flat ring spinning about Y, drawn from both sides
        pose.pushPose();
        pose.translate(0.5f, SC * PX, 0.5f);
        pose.mulPose(com.mojang.math.Axis.YP.rotation(t * SPIN));
        out.submitCustomGeometry(pose, RenderTypes.lightning(), (p, vc) -> {
            for (int[] px : DISK) {
                float r = (float) Math.hypot(px[0] + 0.5, px[1] + 0.5);
                float f = Math.min(1f, Math.max(0f, (r - 4.5f) / 1.7f));
                float b = pal.shimmer(hash(px[0], 7, px[1]));
                float cr = (glow[0] + (DEEP[0] - glow[0]) * f) * b, cg = (glow[1] + (DEEP[1] - glow[1]) * f) * b, cb = (glow[2] + (DEEP[2] - glow[2]) * f) * b;
                quadXZ(vc, p.pose(), pal, px[0], px[1], cr, cg, cb);
            }
        });
        pose.popPose();

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

    private static void quadXY(VertexConsumer vc, Matrix4f m, RingPalette pal, int x, int y, float r, float g, float b, float a) {
        vc.addVertex(m, x * PX, y * PX, 0).setColor(pal.channel(r), pal.channel(g), pal.channel(b), pal.channel(a));
        vc.addVertex(m, (x + 1) * PX, y * PX, 0).setColor(pal.channel(r), pal.channel(g), pal.channel(b), pal.channel(a));
        vc.addVertex(m, (x + 1) * PX, (y + 1) * PX, 0).setColor(pal.channel(r), pal.channel(g), pal.channel(b), pal.channel(a));
        vc.addVertex(m, x * PX, (y + 1) * PX, 0).setColor(pal.channel(r), pal.channel(g), pal.channel(b), pal.channel(a));
    }

    private static void quadXZ(VertexConsumer vc, Matrix4f m, RingPalette pal, int x, int z, float r, float g, float b) {
        float x0 = x * PX, x1 = (x + 1) * PX, z0 = z * PX, z1 = (z + 1) * PX;
        vc.addVertex(m, x0, 0, z0).setColor(pal.channel(r), pal.channel(g), pal.channel(b), 255); vc.addVertex(m, x0, 0, z1).setColor(pal.channel(r), pal.channel(g), pal.channel(b), 255);
        vc.addVertex(m, x1, 0, z1).setColor(pal.channel(r), pal.channel(g), pal.channel(b), 255); vc.addVertex(m, x1, 0, z0).setColor(pal.channel(r), pal.channel(g), pal.channel(b), 255);
        vc.addVertex(m, x0, 0, z0).setColor(pal.channel(r), pal.channel(g), pal.channel(b), 255); vc.addVertex(m, x1, 0, z0).setColor(pal.channel(r), pal.channel(g), pal.channel(b), 255);
        vc.addVertex(m, x1, 0, z1).setColor(pal.channel(r), pal.channel(g), pal.channel(b), 255); vc.addVertex(m, x0, 0, z1).setColor(pal.channel(r), pal.channel(g), pal.channel(b), 255);
    }

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

    private static float hash(int x, int y, int z) {
        int n = x * 374761393 ^ y * 668265263 ^ z * 1274126177;
        n = (n ^ (n >>> 13)) * 1274126177;
        return ((n ^ (n >>> 16)) >>> 0 & 0xFFFFFFFFL) / 4294967295f;
    }

    // ---- static shape tables (pixel units, centred on the black hole) -------------------------

    /** Outward faces of a voxel sphere, each as 4 vertices (x,y,z) in counter-clockwise order. */
    private static List<int[]> blockySphereFaces(double R) {
        List<int[]> out = new ArrayList<>();
        int n = (int) Math.ceil(R) + 1;
        for (int i = -n; i < n; i++) for (int j = -n; j < n; j++) for (int k = -n; k < n; k++) {
            if (!inSphere(i, j, k, R)) continue;
            int[] cell = {i, j, k};
            for (int a = 0; a < 3; a++) for (int sgn : new int[]{1, -1}) {
                int[] nb = cell.clone(); nb[a] += sgn;
                if (inSphere(nb[0], nb[1], nb[2], R)) continue;
                int b = (a + 1) % 3, c = (a + 2) % 3;
                int[] p = cell.clone(); if (sgn > 0) p[a] += 1;
                int[][] corners = {{0, 0}, {1, 0}, {1, 1}, {0, 1}};
                if (sgn < 0) corners = new int[][]{{0, 0}, {0, 1}, {1, 1}, {1, 0}};
                int[] f = new int[12];
                for (int v = 0; v < 4; v++) {
                    int[] q = p.clone(); q[b] += corners[v][0]; q[c] += corners[v][1];
                    f[v * 3] = q[0]; f[v * 3 + 1] = q[1]; f[v * 3 + 2] = q[2];
                }
                out.add(f);
            }
        }
        return out;
    }

    private static boolean inSphere(int i, int j, int k, double R) {
        return Math.sqrt((i + .5) * (i + .5) + (j + .5) * (j + .5) + (k + .5) * (k + .5)) < R;
    }

    /** Photon ring pixels {x, y, slot}; slot = position around the ring, used by the shimmer. */
    private static List<int[]> ringPixels() {
        List<int[]> px = new ArrayList<>();
        for (int x = -6; x < 6; x++) for (int y = -6; y < 6; y++) {
            double fx = x + .5, fy = y + .5, r = Math.hypot(fx, fy * 1.12);
            boolean ring = r >= 2.8 && r < 4.2;
            boolean flare = Math.abs(fy) < 1 && Math.abs(fx) < 5.0 && Math.abs(fx) >= 2.8;
            if (ring || flare) px.add(new int[]{x, y, 0});
        }
        px.sort((a, b) -> Double.compare(Math.atan2(a[1] + .5, a[0] + .5), Math.atan2(b[1] + .5, b[0] + .5)));
        for (int i = 0; i < px.size(); i++) px.get(i)[2] = i;
        return px;
    }

    private static List<int[]> diskPixels() {
        List<int[]> px = new ArrayList<>();
        for (int x = -7; x < 7; x++) for (int z = -7; z < 7; z++) {
            double r = Math.hypot(x + .5, z + .5);
            if (r >= 4.2 && r < 6.3) px.add(new int[]{x, z});
        }
        return px;
    }

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
