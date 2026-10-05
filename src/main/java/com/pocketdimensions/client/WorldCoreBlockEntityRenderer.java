package com.pocketdimensions.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.pocketdimensions.block.WorldCoreBlock;
import com.pocketdimensions.blockentity.WorldCoreBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BeaconRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The Geode Heart's living parts (see design/world-anchor-concept.html, round 18): the realm's black hole in the hollow,
 * the beacon rising from it up the shaft, crystal shards (circling the hole at a third of the disk speed, along the
 * shaft walls at a fifth, a few leaked at the crown and under the boulder bobbing out of step) and the aurora crystals
 * (five faceted crystals driven down through the crown, a hanging layer with inverted peaks under the boulder) whose
 * colour drifts through the northern lights. Colours follow the siege state; once the anchor is lost only the dull
 * crown crystals remain. Coordinates are pixels relative to the lower half.
 */
public class WorldCoreBlockEntityRenderer implements BlockEntityRenderer<WorldCoreBlockEntity, GeodeRenderState> {

    private static final float PX = 1f / 16f;
    private static final float CY = 17.5f;                 // black hole centre height
    private static final int LOST = WorldCoreBlockEntity.STATE_ANCHOR_LOST;
    private static final int[] BEAM = {0xFF4488FF, 0xFFFF44FF, 0xFFFF4444};

    /** Crystal colours per living state: deep, bright (matching the model's crystal lining). */
    private static final float[][][] SHARD = {
        {{60, 130, 255}, {150, 240, 255}, {200, 250, 255}},
        {{200, 50, 180}, {255, 140, 235}, {255, 190, 245}},
        {{255, 80, 40}, {255, 170, 60}, {255, 170, 60}},
    };
    private static final float[][] AURORA = {{60, 255, 170}, {60, 200, 255}, {170, 90, 255}, {255, 110, 200}};

    /** Voxel {x, y, z, faceMask, cluster, shade*1000}; shade and cluster only used by crystals. */
    private static final List<int[]> RING = faces(ringShards());
    private static final List<int[]> SHAFT = faces(shaftShards());
    private static final List<Leak> LEAKS = leaks();
    private static final List<int[]> CRYSTALS = faces(crystals(0, true));
    private static final List<int[]> CRYSTALS_FALLEN = faces(crystals(4, false));

    private record Leak(int x, int y, int z, float phase, float speed, float amp) {}

    public WorldCoreBlockEntityRenderer(BlockEntityRendererProvider.Context ctx) {}

    @Override
    public GeodeRenderState createRenderState() { return new GeodeRenderState(); }

    @Override
    public void extractRenderState(WorldCoreBlockEntity be, GeodeRenderState s, float partialTick, Vec3 cameraPos,
                                   ModelFeatureRenderer.CrumblingOverlay crumbling) {
        BlockEntityRenderState.extractBase(be, s, crumbling);
        Level level = be.getLevel();
        s.siege = be.getBlockState().getValue(WorldCoreBlock.SIEGE);
        // Wrap before converting to float: past ~2^24 ticks a float can no longer hold the partial tick
        s.time = level == null ? 0 : (Math.floorMod(level.getGameTime(), 24000L * 20) + partialTick) / 20f;
        s.beamTime = level == null ? 0 : Math.floorMod(level.getGameTime(), 24000L * 20) + partialTick;
        s.beamHeight = 0;
        if (level != null && s.siege != LOST) {
            BlockPos top = be.getBlockPos().above(2);
            int h = 0;
            for (int i = 0; i < 256; i++) { if (!level.getBlockState(top.above(i)).isAir()) break; h = i + 1; }
            s.beamHeight = h + 1;   // + the 14.5 px from the black hole to the top of the upper half
        }
    }

    @Override
    public void submit(GeodeRenderState s, PoseStack pose, SubmitNodeCollector out, CameraRenderState camera) {
        float t = s.time;
        if (s.siege == LOST) {
            // inert: the black hole has collapsed, the beam is out; the crown crystals stand dull and still
            drawCrystals(pose, out, CRYSTALS_FALLEN, -1, t);
            return;
        }
        BlackHoleRenderer.submit(pose, out, camera, 0.5f, CY * PX, 0.5f,
                s.siege == 0 ? BlackHoleRenderer.RingPalette.GLOW : BlackHoleRenderer.RingPalette.EMBER, t);

        if (s.beamHeight > 0) {
            pose.pushPose();
            pose.translate(0f, CY * PX, 0f);
            BeaconRenderer.submitBeaconBeam(pose, out, BeaconRenderer.BEAM_LOCATION, 1f, s.beamTime, 0, s.beamHeight,
                    BEAM[s.siege], 0.07f, 0.16f);   // thin enough to rise up the shaft without touching its walls
            pose.popPose();
        }

        float[][] c = SHARD[s.siege];
        float spin = BlackHoleRenderer.SPIN;
        drawShards(pose, out, RING, t * spin / 3, c);
        drawShards(pose, out, SHAFT, t * spin / 5, c);
        for (Leak l : LEAKS) {
            pose.pushPose();
            pose.translate(0f, (float) Math.sin(t * l.speed + l.phase) * l.amp * PX, 0f);
            drawShards(pose, out, List.of(new int[]{l.x, l.y, l.z, 63, 0, 1000}), t * spin / 5, c);
            pose.popPose();
        }
        drawCrystals(pose, out, CRYSTALS, s.siege, t);
    }

    /** Glowing shards turning about the core's vertical axis by angle a (radians). */
    private static void drawShards(PoseStack pose, SubmitNodeCollector out, List<int[]> vox, float a, float[][] c) {
        pose.pushPose();
        pose.translate(0.5f, 0f, 0.5f);
        pose.mulPose(com.mojang.math.Axis.YP.rotation(a));   // same direction as the disk
        out.submitCustomGeometry(pose, RenderTypes.lightning(), (p, vc) -> {
            for (int[] v : vox) {
                float[] col = BlackHoleRenderer.hash(v[0], v[1], v[2]) > 0.5f ? c[2]
                        : new float[]{(c[0][0] + c[1][0]) / 2, (c[0][1] + c[1][1]) / 2, (c[0][2] + c[1][2]) / 2};
                cube(vc, p.pose(), v[0] - 8, v[1], v[2] - 8, v[3], col[0] / 255f, col[1] / 255f, col[2] / 255f, 1f);
            }
        });
        pose.popPose();
    }

    /** Translucent aurora crystals; siege < 0 = inert (dull, still). */
    private static void drawCrystals(PoseStack pose, SubmitNodeCollector out, List<int[]> vox, int siege, float t) {
        out.submitCustomGeometry(pose, RenderTypes.debugQuads(), (p, vc) -> {
            for (int[] v : vox) {
                float sh = v[5] / 1000f, r, g, b, a;
                if (siege < 0) {
                    float h = BlackHoleRenderer.hash(v[0], v[1], v[2]);
                    r = (90 + 30 * h) * sh; g = (100 + 32 * h) * sh; b = (120 + 36 * h) * sh; a = 0.85f;
                } else {
                    float[] col = aurora(t * 0.07f + v[4] * 0.37f + v[1] * 0.03f);
                    if (siege > 0) {   // under siege the aurora is swallowed by the state colour
                        float[] hi = SHARD[siege][1];
                        for (int i = 0; i < 3; i++) col[i] += (hi[i] - col[i]) * 0.6f;
                    }
                    r = col[0] * sh; g = col[1] * sh; b = col[2] * sh; a = 0.78f;
                }
                cube(vc, p.pose(), v[0], v[1], v[2], v[3], Math.min(1f, r / 255f), Math.min(1f, g / 255f), Math.min(1f, b / 255f), a);
            }
        });
    }

    private static float[] aurora(float s) {
        float f = (s % 1 + 1) % 1 * AURORA.length;
        int i = (int) Math.floor(f);
        float k = f - i;
        float[] a = AURORA[i % AURORA.length], b = AURORA[(i + 1) % AURORA.length];
        return new float[]{a[0] + (b[0] - a[0]) * k, a[1] + (b[1] - a[1]) * k, a[2] + (b[2] - a[2]) * k};
    }

    /** The exposed faces (mask bits: -z, +z, -x, +x, +y, -y) of one voxel. */
    private static void cube(VertexConsumer vc, Matrix4f m, int x, int y, int z, int mask, float r, float g, float b, float a) {
        float x0 = x * PX, x1 = (x + 1) * PX, y0 = y * PX, y1 = (y + 1) * PX, z0 = z * PX, z1 = (z + 1) * PX;
        float[][] q = {
            {x0, y0, z0, x0, y1, z0, x1, y1, z0, x1, y0, z0}, {x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1},
            {x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0}, {x1, y0, z0, x1, y1, z0, x1, y1, z1, x1, y0, z1},
            {x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0}, {x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1}};
        for (int f = 0; f < 6; f++) {
            if ((mask & (1 << f)) == 0) continue;
            for (int i = 0; i < 4; i++) vc.addVertex(m, q[f][i * 3], q[f][i * 3 + 1], q[f][i * 3 + 2]).setColor(r, g, b, a);
        }
    }

    // ---- static voxel tables (ported from the concept, same hash) ----------------------------

    private static float h3(int x, int y, int z) { return BlackHoleRenderer.hash(x, y, z); }

    private static Map<String, int[]> ringShards() {
        Map<String, int[]> m = new LinkedHashMap<>();
        for (int y = 6; y < 30; y++) for (int z = 0; z < 16; z++) for (int x = 0; x < 16; x++) {
            double dy = y + 0.5 - CY, rr = Math.hypot(x + 0.5 - 8, z + 0.5 - 8), r = Math.hypot(rr, dy);
            if (r >= 5.0 && r < 6.0 && Math.abs(dy) > 1.6 && h3(x, y, z) > 0.8) m.put(x + "," + y + "," + z, new int[]{x, y, z, 0, 0, 1000});
        }
        return m;
    }

    private static Map<String, int[]> shaftShards() {
        Map<String, int[]> m = new LinkedHashMap<>();
        for (int y = 4; y < 30; y++) for (int z = 0; z < 16; z++) for (int x = 0; x < 16; x++) {
            double dy = y + 0.5 - CY, rr = Math.hypot(x + 0.5 - 8, z + 0.5 - 8);
            if (Math.abs(dy) > 4.5 && rr > 1.2 && rr < 2.2 && h3(x, y, z) > 0.9) m.put(x + "," + y + "," + z, new int[]{x, y, z, 0, 0, 1000});
        }
        return m;
    }

    /** Five leaked shards fanned around the crown, four in the gap under the boulder (always clear of the ground). */
    private static List<Leak> leaks() {
        List<Leak> out = new ArrayList<>();
        double[][] groups = {{5, 30, 3, 0.8, 0.4}, {4, 2, 2, 0.5, 1.2}};
        for (double[] g : groups) {
            int n = (int) g[0], y0 = (int) g[1];
            for (int i = 0; i < n; i++) {
                double a = g[4] + i * 2 * Math.PI / n + (h3(i, y0, 3) - 0.5) * 0.6, r = 1.8 + h3(i, y0, 5) * 1.4;
                int x = (int) Math.floor(8 + Math.cos(a) * r), z = (int) Math.floor(8 + Math.sin(a) * r);
                int y = y0 + (int) Math.floor(h3(i, y0, 7) * g[2]);
                out.add(new Leak(x, y, z, h3(x, y, z) * 6.283f, 0.35f + h3(z, x, y) * 0.3f, (float) g[3] + h3(y, z, x) * 0.5f));
            }
        }
        return out;
    }

    private static double geodeSurfaceR(double y, double a) {
        double dy = y + 0.5 - CY, ds = dy < 0 ? dy * 0.42 : dy * 0.62, r = 0;
        while (r < 8) {
            double cx = Math.cos(a) * r, cz = Math.sin(a) * r;
            if (0.25 * Math.max(Math.max(Math.abs(cx), Math.abs(cz)), Math.abs(ds)) + 0.75 * Math.sqrt(cx * cx + cz * cz + ds * ds) > 7.4) break;
            r += 0.25;
        }
        return r;
    }

    /** Five faceted aurora crystals driven down through the crown, plus (while alive) the hanging layer under the boulder. */
    private static Map<String, int[]> crystals(int drop, boolean beard) {
        Map<String, int[]> m = new LinkedHashMap<>();
        int[][] full3 = {{0, 0, 1000}, {-1, 0, 820}, {1, 0, 950}, {0, -1, 880}, {0, 1, 1080}};
        int[][] full2 = {{0, 0, 1000}, {1, 0, 840}, {0, 1, 1060}, {1, 1, 920}};
        for (int i = 0; i < 5; i++) {
            double a = i * 2 * Math.PI / 5 + 0.5, r = 3.0 + h3(i, 1, 2) * 0.9, len = 7 + h3(i, 2, 3) * 2.5;
            double lean = (h3(i, 5, 5) - 0.5) * 0.25;
            double[] base = {8 + Math.cos(a) * r, 35 - h3(i, 4, 4) * 2.5, 8 + Math.sin(a) * r};
            double[] d = {Math.cos(a) * lean, -1, Math.sin(a) * lean};
            double dl = Math.sqrt(d[0] * d[0] + d[1] * d[1] + d[2] * d[2]);
            int thick = i < 2 ? 3 : 2;
            for (double t = 0; t <= len; t += 0.35) {
                int qx = (int) Math.floor(base[0] + d[0] / dl * t), qy = (int) Math.floor(base[1] + d[1] / dl * t - drop), qz = (int) Math.floor(base[2] + d[2] / dl * t);
                int tip = t < 1.2 ? 0 : t < 2.4 ? 1 : 2;   // faceted cross-section tapering to a point at the exposed tip
                int[][] full = thick == 3 ? full3 : full2;
                int[][] sec = tip == 0 ? new int[][]{{0, 0, 1100}} : tip == 1 ? java.util.Arrays.copyOf(full, thick == 3 ? 3 : 2) : full;
                for (int[] c : sec) {
                    int y = qy;
                    if (y < 0 || y > 35) continue;
                    m.put((qx + c[0]) + "," + y + "," + (qz + c[1]), new int[]{qx + c[0], y, qz + c[1], 0, i, c[2]});
                }
            }
        }
        if (beard) {   // a thin, broken sheet over the underside dropping into jagged inverted peaks
            double[][] peaks = {{5.2, 6.0, 4.6, 2.6}, {10.8, 5.6, 3.6, 2.2}, {9.4, 10.6, 4.8, 2.8}, {4.8, 10.6, 3.0, 2.0}, {7.6, 8.4, 4.4, 1.8}, {11.2, 9.2, 2.6, 1.6}};
            for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) {
                double cx = x + 0.5 - 8, cz = z + 0.5 - 8, a = Math.atan2(cz, cx);
                if (Math.hypot(cx, cz) > geodeSurfaceR(5, a) - 1.6) continue;
                double depth = 0; int cl = 20;
                for (int k = 0; k < peaks.length; k++) {
                    double f = 1 - Math.hypot(x + 0.5 - peaks[k][0], z + 0.5 - peaks[k][1]) / peaks[k][3];
                    double dd = f > 0 ? peaks[k][2] * Math.pow(f, 0.8) : 0;
                    if (dd > depth) { depth = dd; cl = 20 + k; }
                }
                if (depth < 0.6 && h3(x, 3, z) > 0.45) continue;
                int bottom = (int) Math.max(1, Math.round(5 - Math.max(0.6, depth)));
                for (int y = bottom; y <= 5; y++) {
                    int sh = y == bottom ? 1120 : (int) (920 + h3(x, y, z) * 120);
                    m.put(x + "," + y + "," + z, new int[]{x, y, z, 0, cl, sh});
                }
            }
        }
        return m;
    }

    /** Fills each voxel's exposed-face mask (faces touching another voxel of the same set are hidden). */
    private static List<int[]> faces(Map<String, int[]> m) {
        int[][] dirs = {{0, 0, -1}, {0, 0, 1}, {-1, 0, 0}, {1, 0, 0}, {0, 1, 0}, {0, -1, 0}};
        List<int[]> out = new ArrayList<>();
        for (int[] v : m.values()) {
            int mask = 0;
            for (int f = 0; f < 6; f++) if (!m.containsKey((v[0] + dirs[f][0]) + "," + (v[1] + dirs[f][1]) + "," + (v[2] + dirs[f][2]))) mask |= 1 << f;
            v[3] = mask;
            out.add(v);
        }
        return out;
    }

    @Override
    public boolean shouldRenderOffScreen() { return true; }

    @Override
    public int getViewDistance() { return Minecraft.getInstance().options.getEffectiveRenderDistance() * 16; }

    @Override
    public boolean shouldRender(WorldCoreBlockEntity be, Vec3 cameraPos) {
        return Vec3.atCenterOf(be.getBlockPos()).multiply(1.0, 0.0, 1.0)
                .closerThan(cameraPos.multiply(1.0, 0.0, 1.0), this.getViewDistance());
    }
}
