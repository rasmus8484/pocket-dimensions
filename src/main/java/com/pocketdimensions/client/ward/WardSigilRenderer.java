package com.pocketdimensions.client.ward;

import com.mojang.blaze3d.framegraph.FrameGraphBuilder;
import com.mojang.blaze3d.framegraph.FramePass;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.Tesselator;
import com.pocketdimensions.PocketDimensionsMod;
import com.pocketdimensions.network.WardSyncS2C;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelTargetBundle;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.LevelRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;

/**
 * Draws the ward's sigil over the realm (SigilLayout: seal and hexagram, turning rings, spiral arms with pulses, band), in a frame pass of its own just before the terrain (added by
 * LevelRendererMixin), so the world is drawn over it. Every piece is pulled toward the camera by one factor (CameraPull),
 * which leaves its picture unchanged while keeping it inside the fog-free zone, so the sigil looks the same at every
 * render distance. Colour, brightness, cracks and pulses come from WardLook.
 */
public final class WardSigilRenderer {

    private static final Identifier[] RUNE = new Identifier[6], GLOW = new Identifier[6];
    static {
        for (int i = 0; i < 6; i++) {
            RUNE[i] = Identifier.fromNamespaceAndPath(PocketDimensionsMod.MODID, "textures/particle/rune_" + i + ".png");
            GLOW[i] = Identifier.fromNamespaceAndPath(PocketDimensionsMod.MODID, "textures/particle/rune_glow_" + i + ".png");
        }
    }
    private static final Identifier LINE = Identifier.fromNamespaceAndPath(PocketDimensionsMod.MODID, "textures/misc/ward_line.png");

    private WardSigilRenderer() {}

    public static void addPass(FrameGraphBuilder builder, LevelTargetBundle targets, LevelRenderState state) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || !mc.level.dimension().equals(PocketDimensionsMod.REALM_DIM)) return;
        WardView.State ward = WardView.current();
        if (ward == null) return;
        float stars = state.skyRenderState.starBrightness;
        FramePass pass = builder.addPass("pocketdimensions_ward_sigil");
        targets.main = pass.readsAndWrites(targets.main);
        pass.executes(() -> draw(ward, stars));
    }

    /** One frame's drawing context: the camera, the reach, the time and the ward's look. */
    private record Frame(Vec3 cam, double reach, double ticks, long tick, int rgb, float bright, WardSyncS2C sync, boolean turns) {}

    private static void draw(WardView.State ward, float stars) {
        Minecraft mc = Minecraft.getInstance();
        WardSyncS2C s = ward.sync();
        double ticks = mc.level.getGameTime() + mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        Frame f = new Frame(mc.gameRenderer.getMainCamera().position(),
                CameraPull.reach(mc.options.getEffectiveRenderDistance() * 16f), ticks, (long) ticks,
                WardLook.colour(s.siege()), WardLook.brightness(s.warded(), stars), s, WardLook.turns(s.siege()));
        SigilLayout.Sigil sigil = ward.sigil();

        RenderType line = RenderTypes.eyes(LINE);
        BufferBuilder buf = Tesselator.getInstance().begin(line.mode(), line.format());
        for (int i = 0; i < sigil.lines().size(); i++) lineQuad(buf, f, sigil, sigil.lines().get(i), i);
        beam(buf, f);
        drawIfAny(buf, line);

        for (int pass = 0; pass < 2; pass++) {
            for (int g = 0; g < 6; g++) {
                RenderType type = RenderTypes.eyes(pass == 0 ? GLOW[g] : RUNE[g]);
                BufferBuilder b = Tesselator.getInstance().begin(type.mode(), type.format());
                for (int i = 0; i < sigil.runes().size(); i++) {
                    SigilLayout.Rune r = sigil.runes().get(i);
                    if (r.glyph() == g) runeQuad(b, f, sigil, r, i + 100000, pass == 0);
                }
                drawIfAny(b, type);
            }
        }
    }

    private static void drawIfAny(BufferBuilder b, RenderType type) {
        MeshData mesh = b.build();
        if (mesh != null) try (mesh) { type.draw(mesh); }
    }

    /** (x, z) turned about the sigil's centre by `a` radians. */
    private static double[] turned(SigilLayout.Sigil s, double x, double z, double a) {
        if (a == 0) return new double[]{x, z};
        double dx = x - s.cx(), dz = z - s.cz(), c = Math.cos(a), n = Math.sin(a);
        return new double[]{s.cx() + dx * c - dz * n, s.cz() + dx * n + dz * c};
    }

    private static double turnOf(Frame f, SigilLayout.Kind kind, int ring) {
        if (!f.turns()) return 0;
        return switch (kind) {
            case RING -> SigilLayout.turn(ring, f.ticks());
            case ARM -> SigilLayout.turnArms(f.ticks());
            case STAR -> SigilLayout.turnSeal(f.ticks());
            default -> 0;
        };
    }

    private static void runeQuad(BufferBuilder b, Frame f, SigilLayout.Sigil s, SigilLayout.Rune r, int index, boolean glow) {
        if (!WardLook.shown(index, f.sync().siege(), f.sync().breachOpen(), f.tick())) return;
        double turn = turnOf(f, r.kind(), r.ring());
        double[] c = turned(s, r.x(), r.z(), turn);
        double half = r.size() / 2 * (glow ? 1.7 : 1.0), a = r.angle() + turn;
        float alpha = f.bright() * (glow ? 0.45f : 0.9f);
        quad(b, f, c[0], c[1], half, half, a, alpha, 1);
    }

    private static void lineQuad(BufferBuilder b, Frame f, SigilLayout.Sigil s, SigilLayout.Line l, int index) {
        if (!WardLook.shown(index, f.sync().siege(), f.sync().breachOpen(), f.tick())) return;
        double turn = turnOf(f, l.kind(), l.ring());
        double[] p = turned(s, l.x1(), l.z1(), turn), q = turned(s, l.x2(), l.z2(), turn);
        double mx = (p[0] + q[0]) / 2, mz = (p[1] + q[1]) / 2;
        double len = Math.hypot(q[0] - p[0], q[1] - p[1]), a = Math.atan2(q[1] - p[1], q[0] - p[0]);
        float boost = l.kind() == SigilLayout.Kind.ARM || l.kind() == SigilLayout.Kind.CONDUIT ? WardLook.pulse(l.along(), f.ticks()) : 0;
        quad(b, f, mx, mz, len / 2, l.width() * 2, a, f.bright() * (float) l.fade() * (0.6f + 0.4f * boost), 1 + boost);
    }

    /**
     * A flat quad at the sigil's plane centred on (x, z), halfU along angle `a` and halfV across it, pulled toward the
     * camera as a whole (one factor for the whole quad keeps its picture exact, texture included).
     */
    private static void quad(BufferBuilder b, Frame f, double x, double z, double halfU, double halfV, double a, float alpha, float glow) {
        double rx = x - f.cam().x, ry = SigilLayout.PLANE_Y - f.cam().y, rz = z - f.cam().z;
        double k = CameraPull.factor(Math.sqrt(rx * rx + ry * ry + rz * rz), f.reach());
        double ux = Math.cos(a) * halfU, uz = Math.sin(a) * halfU, vx = -Math.sin(a) * halfV, vz = Math.cos(a) * halfV;
        int r = Math.min(255, (int) ((f.rgb() >> 16 & 0xFF) * glow)), g = Math.min(255, (int) ((f.rgb() >> 8 & 0xFF) * glow)),
                bl = Math.min(255, (int) ((f.rgb() & 0xFF) * glow)), al = Math.min(255, (int) (alpha * 255));
        double[][] corners = {{-1, -1, 0, 1}, {1, -1, 1, 1}, {1, 1, 1, 0}, {-1, 1, 0, 0}};
        for (double[] c : corners) {
            float px = (float) ((rx + ux * c[0] + vx * c[1]) * k), py = (float) (ry * k), pz = (float) ((rz + uz * c[0] + vz * c[1]) * k);
            b.addVertex(px, py, pz).setColor(r, g, bl, al).setUv((float) c[2], (float) c[3])
                    .setOverlay(OverlayTexture.NO_OVERLAY).setLight(0xF000F0).setNormal(0, -1, 0);
        }
    }

    /**
     * The Core's beam above where the vanilla beam shows: two crossed thin quads per 16-block segment, from the Core up
     * to the sigil, drawn only where the Core's own renderer can't (pulled segments, or above its 256-block reach).
     */
    private static void beam(BufferBuilder b, Frame f) {
        WardSyncS2C s = f.sync();
        if (s.siege() == WardLook.LOST) return;
        double x = s.coreX() + 0.5, z = s.coreZ() + 0.5, top = SigilLayout.PLANE_Y, bottom = s.coreY() + 3;
        int r = f.rgb() >> 16 & 0xFF, g = f.rgb() >> 8 & 0xFF, bl = f.rgb() & 0xFF, al = (int) (f.bright() * 200);
        for (double y0 = bottom; y0 < top; y0 += 16) {
            double y1 = Math.min(top, y0 + 16), my = (y0 + y1) / 2;
            double rx = x - f.cam().x, ry = my - f.cam().y, rz = z - f.cam().z;
            double k = CameraPull.factor(Math.sqrt(rx * rx + ry * ry + rz * rz), f.reach());
            if (k >= 1 && y1 <= s.coreY() + 256) continue;             // the Core's own beam is drawn there
            for (int side = 0; side < 2; side++) {
                double ox = side == 0 ? 0.3 : 0, oz = side == 0 ? 0 : 0.3;
                double[][] v = {{-ox, y0, -oz, 0, 1}, {ox, y0, oz, 1, 1}, {ox, y1, oz, 1, 0}, {-ox, y1, -oz, 0, 0}};
                for (double[] c : v) {
                    float px = (float) ((rx + c[0]) * k), py = (float) ((c[1] - f.cam().y) * k), pz = (float) ((rz + c[2]) * k);
                    b.addVertex(px, py, pz).setColor(r, g, bl, al).setUv((float) c[3], (float) c[4])
                            .setOverlay(OverlayTexture.NO_OVERLAY).setLight(0xF000F0).setNormal(0, 0, 1);
                }
            }
        }
    }
}
