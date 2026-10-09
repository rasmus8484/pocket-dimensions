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
import net.minecraft.resources.Identifier;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.material.FogType;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Draws the Vaulted Ward over the realm, in a frame pass of its own just before the terrain (added by
 * LevelRendererMixin), so the world is drawn over it: first the ward field (WardField, a grid of soft quads with an
 * opacity per corner), then the Core's beam up to the crown, then every rune of VaultFrame (a glow sprite behind each
 * rune sprite). Every quad is pulled toward the camera by one factor (CameraPull), which leaves its picture unchanged
 * while keeping it inside the fog-free zone, so the ward looks the same at every render distance. Colour, brightness
 * and cracks come from WardLook. Not drawn when vanilla would not draw the sky (blindness, darkness, lava, powder snow).
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
    private static final int CROWN_Y = 384, FIELD_CELLS = 64;

    private static final List<VaultFrame.Rune> runes = new ArrayList<>();
    private static float[] fieldAlpha = new float[0];

    private WardSigilRenderer() {}

    public static void addPass(FrameGraphBuilder builder, LevelTargetBundle targets, LevelRenderState state, GpuBufferSlice fog) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || !mc.level.dimension().equals(PocketDimensionsMod.REALM_DIM)) return;
        WardView.State ward = WardView.current();
        if (ward == null || skyHidden(mc.gameRenderer.getMainCamera())) return;
        float stars = state.skyRenderState.starBrightness;
        FramePass pass = builder.addPass("pocketdimensions_ward_sigil");
        targets.main = pass.readsAndWrites(targets.main);
        pass.executes(() -> {
            RenderSystem.setShaderFog(fog);
            draw(ward, stars);
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
        beam(f, s);

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
        int al = Math.min(255, (int) (alpha * 255));
        vertex(b, (rx - ux - vx) * k, ry * k, (rz - uz - vz) * k, rgb, al, 0, 1);
        vertex(b, (rx + ux - vx) * k, ry * k, (rz + uz - vz) * k, rgb, al, 1, 1);
        vertex(b, (rx + ux + vx) * k, ry * k, (rz + uz + vz) * k, rgb, al, 1, 0);
        vertex(b, (rx - ux + vx) * k, ry * k, (rz - uz + vz) * k, rgb, al, 0, 0);
    }

    private static void vertex(BufferBuilder b, double x, double y, double z, int rgb, int alpha, float u, float v) {
        b.addVertex((float) x, (float) y, (float) z).setColor(rgb >> 16 & 0xFF, rgb >> 8 & 0xFF, rgb & 0xFF, alpha).setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(0xF000F0).setNormal(0, -1, 0);
    }

    /**
     * The ward field over the square inside the band: a grid of quads on the dome with the field's opacity at each corner,
     * so the graphics card blends it smoothly between them. Each quad is pulled by the factor for its centre; neighbours
     * share corners on the same rays from the camera, so they meet on screen.
     */
    private static void field(Frame f, Vault.Layout l, double seconds) {
        double square = l.half() - 15, cell = square / FIELD_CELLS;
        int n = FIELD_CELLS, side = 2 * n + 1;
        if (fieldAlpha.length != side * side) fieldAlpha = new float[side * side];
        for (int i = -n; i <= n; i++) for (int j = -n; j <= n; j++)
            fieldAlpha[(i + n) * side + (j + n)] = (float) (WardField.alpha(l.half(), i * cell, j * cell, seconds) * f.bright());
        int rgb = tint(f.rgb(), 0.6, 100);
        RenderType type = RenderTypes.eyes(FIELD);
        BufferBuilder b = Tesselator.getInstance().begin(type.mode(), type.format());
        for (int i = -n; i < n; i++) for (int j = -n; j < n; j++) {
            float a00 = fieldAlpha[(i + n) * side + (j + n)], a10 = fieldAlpha[(i + 1 + n) * side + (j + n)];
            float a11 = fieldAlpha[(i + 1 + n) * side + (j + 1 + n)], a01 = fieldAlpha[(i + n) * side + (j + 1 + n)];
            if (a00 + a10 + a11 + a01 < 0.016f) continue;
            double x0 = i * cell, x1 = x0 + cell, z0 = j * cell, z1 = z0 + cell;
            double mx = l.cx() + x0 + cell / 2 - f.cam().x, mz = l.cz() + z0 + cell / 2 - f.cam().z;
            double my = l.dome(Math.hypot(x0 + cell / 2, z0 + cell / 2)) + 5 - f.cam().y;
            double k = CameraPull.factor(Math.sqrt(mx * mx + my * my + mz * mz), f.reach());
            fieldVertex(b, f, l, x0, z0, k, rgb, a00);
            fieldVertex(b, f, l, x1, z0, k, rgb, a10);
            fieldVertex(b, f, l, x1, z1, k, rgb, a11);
            fieldVertex(b, f, l, x0, z1, k, rgb, a01);
        }
        drawIfAny(b, type);
    }

    private static void fieldVertex(BufferBuilder b, Frame f, Vault.Layout l, double x, double z, double k, int rgb, float alpha) {
        double y = l.dome(Math.hypot(x, z)) + 5;
        vertex(b, (l.cx() + x - f.cam().x) * k, (y - f.cam().y) * k, (l.cz() + z - f.cam().z) * k, rgb,
                Math.min(255, (int) (alpha * 255)), 0.5f, 0.5f);
    }

    /**
     * The Core's beam above where the vanilla beam shows, up to the crown: two crossed thin quads per 16-block segment,
     * each drawn with both windings so it shows from every side, the line texture's soft band across its width. Only
     * where the Core's own renderer can't draw it (pulled segments, or above its 256-block reach).
     */
    private static void beam(Frame f, WardSyncS2C s) {
        if (s.siege() == WardLook.LOST) return;
        RenderType type = RenderTypes.eyes(LINE);
        BufferBuilder b = Tesselator.getInstance().begin(type.mode(), type.format());
        double x = s.coreX() + 0.5, z = s.coreZ() + 0.5, bottom = s.coreY() + 3;
        int al = (int) (f.bright() * 200);
        for (double y0 = bottom; y0 < CROWN_Y; y0 += 16) {
            double y1 = Math.min(CROWN_Y, y0 + 16), my = (y0 + y1) / 2;
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
