package com.pocketdimensions.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.pocketdimensions.block.AnchorBreakerBlock;
import com.pocketdimensions.block.WorldAnchorBlock;
import com.pocketdimensions.blockentity.AnchorBreakerBlockEntity;
import com.pocketdimensions.init.ModBlocks;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * Draws the Unmaker's siphon stream (red light pulled from the anchor's black hole up into the funnel) and its
 * frozen lightning: a new set of four branching, vanilla-style bolts bursts out of the black hole at 25 / 50 / 75 %
 * and stays, static, while the breaker is attached. Coordinates are in pixels relative to the breaker block.
 */
public class AnchorBreakerBlockEntityRenderer implements BlockEntityRenderer<AnchorBreakerBlockEntity, AnchorBreakerRenderState> {

    private static final float PX = 1f / 16f;
    /** The anchor's black hole, in breaker-local pixels (anchor lower half sits 2 blocks below). */
    private static final float[] CORE = {8f, 13.5f - 32f, 8f};
    private static final int PER = 4, SEGS = 7;
    private static final float AMP = 1.2f;
    private static final float[] BOLT = {215 / 255f, 225 / 255f, 1f}, BOLT_GLOW = {0.45f / 1.6f, 0.55f / 1.6f, 1f};
    private static final float[] HOT = {1f, 80 / 255f, 40 / 255f}, HOT_CORE = {1f, 200 / 255f, 170 / 255f};

    public AnchorBreakerBlockEntityRenderer(BlockEntityRendererProvider.Context ctx) {}

    @Override
    public AnchorBreakerRenderState createRenderState() {
        return new AnchorBreakerRenderState();
    }

    @Override
    public void extractRenderState(AnchorBreakerBlockEntity be, AnchorBreakerRenderState s, float partialTick,
                                   Vec3 cameraPos, ModelFeatureRenderer.CrumblingOverlay crumbling) {
        BlockEntityRenderState.extractBase(be, s, crumbling);
        Level level = be.getLevel();
        BlockState below = level == null ? null : level.getBlockState(be.getBlockPos().below());
        if (below == null || !below.is(ModBlocks.WORLD_ANCHOR.get()) || !below.getValue(WorldAnchorBlock.LINKED)) {
            s.boltSets = 0;
            s.stream = false;
            return;
        }
        s.boltSets = Math.max(0, be.getBlockState().getValue(AnchorBreakerBlock.CHARGE) - 1);
        s.stream = be.hasFuel();
        s.seed = be.getBlockPos().asLong();
    }

    @Override
    public void submit(AnchorBreakerRenderState s, PoseStack pose, SubmitNodeCollector out, CameraRenderState camera) {
        if (s.stream) {
            out.submitCustomGeometry(pose, RenderTypes.lightning(), (p, vc) -> {
                float[] top = {CORE[0], 0f, CORE[2]};
                prism(vc, p.pose(), CORE, top, 0.55f, HOT, 0.85f);
                prism(vc, p.pose(), CORE, top, 0.225f, HOT_CORE, 0.9f);
            });
        }
        submitBolts(pose, out, CORE, s.boltSets, s.seed);
    }

    /**
     * Frozen lightning bursting out of a black hole at {@code core} (pixels, relative to the pose): {@code sets} sets
     * (0..3) of four branching bolts reaching 33 / 51 / 69 px, jagged by a hash of {@code seed} so they never flicker.
     * Shared with the World Core, which shows the same cracks while its anchor is being broken.
     */
    public static void submitBolts(PoseStack pose, SubmitNodeCollector out, float[] core, int sets, long seed) {
        if (sets <= 0) return;
        out.submitCustomGeometry(pose, RenderTypes.lightning(), (p, vc) -> {
            Matrix4f m = p.pose();
            for (int st = 0; st < sets; st++) {
                float reach = (11 + st * 6) * 3;   // 33 / 51 / 69 px from the black hole
                for (int i = 0; i < PER; i++) {
                    int n = st * PER + i;
                    // Spread over a sphere (golden-angle spiral), squashed a little vertically
                    float yv = 1 - 2 * ((i + 0.5f) / PER), r = (float) Math.sqrt(1 - yv * yv);
                    double a = i * 2.39996 + st * 1.1;
                    float[] d = {(float) Math.cos(a) * r, yv * 0.8f, (float) Math.sin(a) * r};
                    bolt(vc, m, core, at(core, d, reach), seed, n * 3);
                    // Two side branches at 45 % and 70 % of the way out
                    for (int b = 0; b < 2; b++) {
                        float f = b == 0 ? 0.45f : 0.7f;
                        float[] from = at(core, d, reach * f), dir = new float[3];
                        for (int c = 0; c < 3; c++) dir[c] = d[c] + (hash(seed, n, b, c, 99) - 0.5f) * 0.9f;
                        bolt(vc, m, from, at(from, dir, reach * 0.3f), seed, n * 3 + 1 + b);
                    }
                }
            }
        });
    }

    private static float[] at(float[] o, float[] d, float len) {
        return new float[]{o[0] + d[0] * len, o[1] + d[1] * len, o[2] + d[2] * len};
    }

    /** One jagged bolt: a white core and a wide faint blue glow along the same static zig-zag. */
    private static void bolt(VertexConsumer vc, Matrix4f m, float[] a, float[] b, long seed, int id) {
        float[][] pts = new float[SEGS + 1][];
        float[] dir = {b[0] - a[0], b[1] - a[1], b[2] - a[2]};
        float[][] uv = perpendiculars(dir);
        for (int i = 0; i <= SEGS; i++) {
            float t = (float) i / SEGS;
            float ou = 0, ov = 0;
            if (i > 0 && i < SEGS) {
                ou = (hash(seed, id, i, 0, 7) - 0.5f) * 2 * AMP;
                ov = (hash(seed, id, i, 1, 7) - 0.5f) * 2 * AMP;
            }
            pts[i] = new float[3];
            for (int c = 0; c < 3; c++) pts[i][c] = a[c] + dir[c] * t + uv[0][c] * ou + uv[1][c] * ov;
        }
        for (int i = 0; i < SEGS; i++) {
            prism(vc, m, pts[i], pts[i + 1], 0.8f, BOLT_GLOW, 0.35f);
            prism(vc, m, pts[i], pts[i + 1], 0.15f, BOLT, 1f);
        }
    }

    /** Two unit vectors perpendicular to d (and to each other). */
    private static float[][] perpendiculars(float[] d) {
        float len = (float) Math.sqrt(d[0] * d[0] + d[1] * d[1] + d[2] * d[2]);
        float dx = d[0] / len, dy = d[1] / len, dz = d[2] / len;
        float[] ref = Math.abs(dy) < 0.9f ? new float[]{0, 1, 0} : new float[]{1, 0, 0};
        float[] u = {dy * ref[2] - dz * ref[1], dz * ref[0] - dx * ref[2], dx * ref[1] - dy * ref[0]};
        float ul = (float) Math.sqrt(u[0] * u[0] + u[1] * u[1] + u[2] * u[2]);
        for (int c = 0; c < 3; c++) u[c] /= ul;
        float[] v = {dy * u[2] - dz * u[1], dz * u[0] - dx * u[2], dx * u[1] - dy * u[0]};
        return new float[][]{u, v};
    }

    /**
     * A square prism of half-width {@code half} (pixels) from a to b, side faces only, emitted in both windings
     * because the lightning render type culls back faces.
     */
    private static void prism(VertexConsumer vc, Matrix4f m, float[] a, float[] b, float half, float[] c, float alpha) {
        float[][] uv = perpendiculars(new float[]{b[0] - a[0], b[1] - a[1], b[2] - a[2]});
        float[] u = uv[0], v = uv[1];
        float[][] ring = new float[4][3];
        int[][] sgn = {{1, 1}, {-1, 1}, {-1, -1}, {1, -1}};
        for (int k = 0; k < 4; k++) for (int i = 0; i < 3; i++) ring[k][i] = (u[i] * sgn[k][0] + v[i] * sgn[k][1]) * half;
        for (int k = 0; k < 4; k++) {
            float[] r0 = ring[k], r1 = ring[(k + 1) % 4];
            float[][] q = {
                {a[0] + r0[0], a[1] + r0[1], a[2] + r0[2]}, {a[0] + r1[0], a[1] + r1[1], a[2] + r1[2]},
                {b[0] + r1[0], b[1] + r1[1], b[2] + r1[2]}, {b[0] + r0[0], b[1] + r0[1], b[2] + r0[2]},
            };
            for (int i = 0; i < 4; i++) vertex(vc, m, q[i], c, alpha);
            for (int i = 3; i >= 0; i--) vertex(vc, m, q[i], c, alpha);
        }
    }

    private static void vertex(VertexConsumer vc, Matrix4f m, float[] p, float[] c, float alpha) {
        vc.addVertex(m, p[0] * PX, p[1] * PX, p[2] * PX).setColor(c[0], c[1], c[2], alpha);
    }

    /** Deterministic 0..1 hash; the same inputs give the same jag every frame, so the bolts never flicker. */
    private static float hash(long seed, int a, int b, int c, int d) {
        long h = seed * 0x9E3779B97F4A7C15L + a * 0xBF58476D1CE4E5B9L + b * 0x94D049BB133111EBL + c * 0x2545F4914F6CDD1DL + d;
        h ^= h >>> 31; h *= 0x7FB5D329728EA185L; h ^= h >>> 27; h *= 0x81DADEF4BC2DD44DL; h ^= h >>> 33;
        return (h >>> 40) / (float) (1L << 24);
    }

    @Override
    public boolean shouldRenderOffScreen() {
        return true;
    }
}
