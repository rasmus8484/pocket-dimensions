package com.pocketdimensions.client.ward;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.framegraph.FrameGraphBuilder;
import com.mojang.blaze3d.framegraph.FramePass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.Tesselator;
import com.pocketdimensions.PocketDimensionsMod;
import com.pocketdimensions.network.WardSyncS2C;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelTargetBundle;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.LevelRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.material.FogType;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * Draws the Vaulted Ward over the realm, in a frame pass of its own just before the terrain (added by
 * LevelRendererMixin), so the world is drawn over it: first the ward field (WardField on a FieldGrid, soft quads with
 * an opacity per corner), then the Core's beam up to the crown, then every rune of VaultFrame (a glow sprite behind each
 * rune sprite). Every quad is pulled toward the camera by one factor (CameraPull), which leaves its picture unchanged
 * while keeping it inside the fog-free zone, so the ward looks the same at every render distance. Colour, brightness
 * and cracks come from WardLook. Not drawn when vanilla would not draw the sky (blindness, darkness, lava, powder snow).
 * /pd test wardTime measures it (WardTimer).
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
    private static final Identifier FIELD = Identifier.fromNamespaceAndPath(PocketDimensionsMod.MODID, "textures/misc/ward_field.png");
    private static final List<VaultFrame.Rune> runes = new ArrayList<>();
    private static FieldGrid grid;
    private static Vault.Layout gridFor;
    private static float[] fieldAlpha = new float[0];
    private static final WardTimer timer = new WardTimer();
    private static int vertices;

    private WardSigilRenderer() {}

    /** Reports /pd test wardTime in chat once its window closes. */
    public static void register() {
        TickEvent.ClientTickEvent.Post.BUS.addListener(e -> {
            WardTimer.Report report = timer.poll(System.nanoTime());
            Minecraft mc = Minecraft.getInstance();
            if (report != null && mc.player != null) mc.player.displayClientMessage(Component.literal(report.describe()), false);
        });
    }

    /** /pd test wardTime: measure the next `seconds` of frames. */
    public static void measure(float seconds) {
        timer.start(seconds, System.nanoTime());
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null)
            mc.player.displayClientMessage(Component.literal(String.format(java.util.Locale.ROOT,
                    "Measuring the ward sigil for %.1f s. Look around as you normally would.", seconds)), false);
    }

    public static void addPass(FrameGraphBuilder builder, LevelTargetBundle targets, LevelRenderState state, GpuBufferSlice fog) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || !mc.level.dimension().equals(PocketDimensionsMod.REALM_DIM)) return;
        WardView.State ward = WardView.current();
        if (ward == null || skyHidden(mc.gameRenderer.getMainCamera())) return;
        float stars = state.skyRenderState.starBrightness;
        FramePass pass = builder.addPass("pocketdimensions_ward_sigil");
        targets.main = pass.readsAndWrites(targets.main);
        pass.executes(() -> {
            long start = System.nanoTime();
            vertices = 0;
            RenderSystem.setShaderFog(fog);
            draw(ward, stars);
            timer.frame(System.nanoTime() - start, vertices);
        });
    }

    /** Vanilla leaves the sky out in lava and powder snow and under blindness or darkness; so does the ward. */
    private static boolean skyHidden(Camera camera) {
        FogType fluid = camera.getFluidInCamera();
        if (fluid == FogType.LAVA || fluid == FogType.POWDER_SNOW) return true;
        return camera.entity() instanceof LivingEntity e && (e.hasEffect(MobEffects.BLINDNESS) || e.hasEffect(MobEffects.DARKNESS));
    }

    /** One frame's drawing context: the camera, the reach, the colour and brightness. */
    private record Frame(Vec3 cam, double reach, int rgb, float bright) {}

    private static void draw(WardView.State ward, float stars) {
        Minecraft mc = Minecraft.getInstance();
        WardSyncS2C s = ward.sync();
        Vault.Layout l = ward.layout();
        double ticks = mc.level.getGameTime() + mc.getDeltaTracker().getGameTimeDeltaPartialTick(false), seconds = ticks / 20;
        long tick = (long) ticks;
        boolean turning = WardLook.turns(s.siege());
        Frame f = new Frame(mc.gameRenderer.getMainCamera().position(), CameraPull.reach(mc.options.getEffectiveRenderDistance() * 16f),
                WardLook.colour(s.siege()), WardLook.brightness(s.warded(), stars));

        if (turning) field(f, l, seconds);
        beam(f, s, l);

        runes.clear();
        VaultFrame.runes(l, seconds, turning, id -> !WardLook.shown(id, s.siege(), s.breachOpen(), tick), runes);
        for (int layer = 0; layer < 2; layer++) {
            boolean glow = layer == 0;
            for (int g = 0; g < 6; g++) {
                RenderType type = RenderTypes.eyes(glow ? GLOW[g] : RUNE[g]);
                BufferBuilder b = Tesselator.getInstance().begin(type.mode(), type.format());
                for (VaultFrame.Rune r : runes) {
                    if (r.glyph() != g) continue;
                    double half = r.size() / 2 * (glow ? 1.7 : 1.0);
                    float alpha = f.bright() * (float) r.alpha() * (glow ? 0.45f : 0.9f);
                    flat(b, f, r.x(), r.y(), r.z(), half, r.angle(), f.rgb(), alpha);
                }
                drawIfAny(b, type);
            }
        }
    }

    private static void drawIfAny(BufferBuilder b, RenderType type) {
        MeshData mesh = b.build();
        if (mesh != null) try (mesh) { type.draw(mesh); }
    }

    /** A flat square rune at height y centred on (x, z), turned by `angle`, pulled toward the camera as a whole. */
    private static void flat(BufferBuilder b, Frame f, double x, double y, double z, double half, double angle, int rgb, float alpha) {
        double rx = x - f.cam().x, ry = y - f.cam().y, rz = z - f.cam().z;
        double k = CameraPull.factor(Math.sqrt(rx * rx + ry * ry + rz * rz), f.reach());
        double ux = Math.cos(angle) * half, uz = Math.sin(angle) * half, vx = -uz, vz = ux;
        int al = opacity(alpha);
        vertex(b, (rx - ux - vx) * k, ry * k, (rz - uz - vz) * k, rgb, al, 0, 1);
        vertex(b, (rx + ux - vx) * k, ry * k, (rz + uz - vz) * k, rgb, al, 1, 1);
        vertex(b, (rx + ux + vx) * k, ry * k, (rz + uz + vz) * k, rgb, al, 1, 0);
        vertex(b, (rx - ux + vx) * k, ry * k, (rz - uz + vz) * k, rgb, al, 0, 0);
    }

    private static void vertex(BufferBuilder b, double x, double y, double z, int rgb, int alpha, float u, float v) {
        vertices++;
        b.addVertex((float) x, (float) y, (float) z).setColor(rgb >> 16 & 0xFF, rgb >> 8 & 0xFF, rgb & 0xFF, alpha).setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(0xF000F0).setNormal(0, -1, 0);
    }

    /**
     * The ward field over the square inside the band: the quads of its polar grid with the field's opacity at each
     * corner, so the graphics card blends it smoothly between them. Each quad is pulled by the factor for its centre;
     * neighbours share corners on the same rays from the camera, so they meet on screen.
     */
    private static void field(Frame f, Vault.Layout l, double seconds) {
        if (gridFor != l) { grid = FieldGrid.build(l); gridFor = l; }
        FieldGrid g = grid;
        if (fieldAlpha.length != g.points()) fieldAlpha = new float[g.points()];
        g.alphas(l.half(), seconds, f.bright(), fieldAlpha);
        int rgb = tint(f.rgb(), 0.6, 100);
        RenderType type = RenderTypes.eyes(FIELD);
        BufferBuilder b = Tesselator.getInstance().begin(type.mode(), type.format());
        int[] q = new int[4];
        for (int ring = 0; ring < FieldGrid.RINGS; ring++) for (int sector = 0; sector < FieldGrid.SECTORS; sector++) {
            if (!g.drawn(fieldAlpha, ring, sector)) continue;
            q[0] = g.index(ring, sector); q[1] = g.index(ring + 1, sector);
            q[2] = g.index(ring + 1, sector + 1); q[3] = g.index(ring, sector + 1);
            double mx = 0, my = 0, mz = 0;
            for (int p : q) { mx += g.x(p); my += g.y(p); mz += g.z(p); }
            mx = l.cx() + mx / 4 - f.cam().x; my = my / 4 - f.cam().y; mz = l.cz() + mz / 4 - f.cam().z;
            double k = CameraPull.factor(Math.sqrt(mx * mx + my * my + mz * mz), f.reach());
            for (int p : q)
                vertex(b, (l.cx() + g.x(p) - f.cam().x) * k, (g.y(p) - f.cam().y) * k, (l.cz() + g.z(p) - f.cam().z) * k,
                        rgb, opacity(fieldAlpha[p]), 0.5f, 0.5f);
        }
        drawIfAny(b, type);
    }

    /** 0..1 opacity to the vertex's 0..255, rounded (so the field's faintest edges keep their last step). */
    private static int opacity(float alpha) {
        return Math.max(0, Math.min(255, Math.round(alpha * 255)));
    }

    /**
     * The Core's beam above where the vanilla beam shows, up to the crown (or, for an off-centre Core, to the dome
     * where the conduit takes over): two crossed thin quads per 16-block segment,
     * each drawn with both windings so it shows from every side, the line texture's soft band across its width. Only
     * where the Core's own renderer can't draw it (pulled segments, or above its 256-block reach).
     */
    private static void beam(Frame f, WardSyncS2C s, Vault.Layout l) {
        if (s.siege() == WardLook.LOST) return;
        RenderType type = RenderTypes.eyes(LINE);
        BufferBuilder b = Tesselator.getInstance().begin(type.mode(), type.format());
        double x = s.coreX() + 0.5, z = s.coreZ() + 0.5, bottom = s.coreY() + 3, top = l.beamTop();
        int al = opacity(f.bright() * 200 / 255f);
        for (double y0 = bottom; y0 < top; y0 += 16) {
            double y1 = Math.min(top, y0 + 16), my = (y0 + y1) / 2;
            double rx = x - f.cam().x, ry = my - f.cam().y, rz = z - f.cam().z;
            double k = CameraPull.factor(Math.sqrt(rx * rx + ry * ry + rz * rz), f.reach());
            if (k >= 1 && y1 <= s.coreY() + 256) continue;             // the Core's own beam is drawn there
            for (int plane = 0; plane < 2; plane++) {
                double ox = plane == 0 ? 0.3 : 0, oz = plane == 0 ? 0 : 0.3;
                double[][] v = {{-ox, y0, -oz, 0, 0}, {ox, y0, oz, 0, 1}, {ox, y1, oz, 1, 1}, {-ox, y1, -oz, 1, 0}};
                for (int wind = 0; wind < 2; wind++)
                    for (int q = 0; q < 4; q++) {
                        double[] c = v[wind == 0 ? q : 3 - q];
                        vertex(b, (rx + c[0]) * k, (c[1] - f.cam().y) * k, (rz + c[2]) * k, f.rgb(), al, (float) c[3], (float) c[4]);
                    }
            }
        }
        drawIfAny(b, type);
    }

    private static int tint(int rgb, double keep, int add) {
        int r = (int) Math.min(255, (rgb >> 16 & 0xFF) * keep + add), g = (int) Math.min(255, (rgb >> 8 & 0xFF) * keep + add);
        int bl = (int) Math.min(255, (rgb & 0xFF) * keep + add);
        return r << 16 | g << 8 | bl;
    }
}
