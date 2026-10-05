package com.pocketdimensions.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.CameraRenderState;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

/**
 * The realm's black hole, shared by the World Anchor and the World Core: a blocky starry rift sphere, a photon ring that
 * always faces the camera (its shimmer fakes a spin) and an accretion disk spinning about the vertical axis.
 */
public final class BlackHoleRenderer {

    private static final float PX = 1f / 16f;
    private static final float[] DEEP = {40 / 255f, 110 / 255f, 230 / 255f};
    /** Disk spin in radians per second; the ring shimmer completes one lap in the same time. */
    public static final float SPIN = 0.6f;

    /**
     * How over-bright ring colours become bytes.
     * GLOW  - clamped: the cool blue glow of the approved concept (default).
     * EMBER - wrapped like an unclamped byte cast: channels pushed past 255 roll over to near zero,
     *         turning the rings a flickering red-yellow, like a real black hole's accretion glow.
     *         Found by accident in the first build and kept on purpose for the siege look.
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

    private static final List<int[]> SPHERE_FACES = blockySphereFaces(3.2);
    private static final List<int[]> RING = ringPixels();
    private static final List<int[]> DISK = diskPixels();

    private BlackHoleRenderer() {}

    /** Draws the black hole centred at (x, y, z) in block units relative to the pose; t = seconds. */
    public static void submit(PoseStack pose, SubmitNodeCollector out, CameraRenderState camera,
                              float x, float y, float z, RingPalette pal, float t) {
        float[] glow = pal.glow;

        // Rift sphere, turned to face the camera like the ring so their pixel edges line up
        pose.pushPose();
        pose.translate(x, y, z);
        pose.mulPose(camera.orientation);
        out.submitCustomGeometry(pose, RenderTypes.endGateway(), (p, vc) -> {
            Matrix4f m = p.pose();
            for (int[] f : SPHERE_FACES) {
                for (int i = 0; i < 4; i++) vc.addVertex(m, f[i * 3] * PX, f[i * 3 + 1] * PX, f[i * 3 + 2] * PX);
            }
        });
        // Photon ring: camera-facing, 0.6 px toward the viewer; the shimmer travels one lap per disk revolution
        pose.translate(0f, 0f, 0.6f * PX);
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
        pose.translate(x, y, z);
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

    /** 0..1 hash, identical to the generator's and the concept's hash3. */
    static float hash(int x, int y, int z) {
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
}
