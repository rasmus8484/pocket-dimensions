package com.pocketdimensions.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.pocketdimensions.PocketDimensionsMod;
import com.pocketdimensions.block.PocketAnchorBlock;
import com.pocketdimensions.blockentity.PocketAnchorBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

/**
 * The Tumbling Cube (see design/world-anchor-concept.html, round 21): a head-sized cube hovering in the block, its sides
 * windows onto end portal light, turning on three axes at once so it reads as a random tumble, inside three bands of
 * runes whose axes swing round too, so together they sweep out a sphere. Coordinates are block pixels.
 */
public class PocketAnchorRenderer implements BlockEntityRenderer<PocketAnchorBlockEntity, PocketAnchorRenderState> {

    private static final float PX = 1f / 16f;
    private static final float CY = 10.5f;                 // cube centre height
    private static final float BAND_R = 10f;               // rune band radius
    private static final int BAND_GLYPHS = 13;
    private static final float GLYPH = 8 * 0.42f * PX;     // the 8x8 rune sprite at 0.42 px per texel
    private static final int[] RUNE = {140, 235, 255};
    /** The unlit rune and its glow sit this far off the depth-claiming cutout, toward the side they are seen from
     *  (each winding is only visible from its own side), so they never z-fight with it. */
    private static final float LIFT = 0.1f * PX;
    private static final Identifier[] RUNE_TEX = new Identifier[6], GLOW_TEX = new Identifier[6];
    static {
        for (int i = 0; i < 6; i++) {
            RUNE_TEX[i] = Identifier.fromNamespaceAndPath(PocketDimensionsMod.MODID, "textures/particle/rune_" + i + ".png");
            GLOW_TEX[i] = Identifier.fromNamespaceAndPath(PocketDimensionsMod.MODID, "textures/particle/rune_glow_" + i + ".png");
        }
    }

    public PocketAnchorRenderer(BlockEntityRendererProvider.Context ctx) {}

    @Override
    public PocketAnchorRenderState createRenderState() { return new PocketAnchorRenderState(); }

    @Override
    public void extractRenderState(PocketAnchorBlockEntity be, PocketAnchorRenderState s, float partialTick, Vec3 cameraPos,
                                   ModelFeatureRenderer.CrumblingOverlay crumbling) {
        BlockEntityRenderState.extractBase(be, s, crumbling);
        Level level = be.getLevel();
        BlockPos pos = be.getBlockPos();
        float offset = BlackHoleRenderer.hash(pos.getX(), pos.getY(), pos.getZ()) * 1000f;
        s.occupied = be.getBlockState().getValue(PocketAnchorBlock.OCCUPIED);
        // Wrap before converting to float: past ~2^24 ticks a float can no longer hold the partial tick
        s.time = level == null ? 0 : (Math.floorMod(level.getGameTime(), 24000L * 20) + partialTick) / 20f + offset;
        s.phase = be.getPhase(partialTick) + offset;
        s.cube = Minecraft.getInstance().getBlockRenderer().getBlockModel(
                be.getBlockState().setValue(PocketAnchorBlock.CUBE, true).setValue(PocketAnchorBlock.OCCUPIED, false));
    }

    /*
     * Draw order. Translucent runes write no depth, and a model sent through submitBlock lands in a fixed buffer that is
     * only drawn at the end of the frame, so the cube painted over every rune in front of it. Instead the cube, its
     * portal and the runes all write depth (so their order no longer matters), and the soft glow is submitted in a
     * later order, drawn after them and tested against their depth.
     */
    @Override
    public void submit(PocketAnchorRenderState s, PoseStack pose, SubmitNodeCollector out, CameraRenderState camera) {
        float t = s.time, p = s.phase;

        // The cube: three slow turns about different axes added together, hovering and bobbing
        pose.pushPose();
        pose.translate(0.5f, (CY + (float) Math.sin(t * 0.9f) * 0.6f) * PX, 0.5f);
        pose.mulPose(new Quaternionf().rotateXYZ(
                p * 0.19f + (float) Math.sin(t * 0.13f) * 1.1f,
                p * 0.27f + (float) Math.sin(t * 0.07f + 1f) * 0.8f,
                (float) Math.sin(t * 0.11f + 2f) * 1.4f));
        out.submitCustomGeometry(pose, RenderTypes.endPortal(), (pp, vc) -> portal(vc, pp.pose(), 2.2f * PX, 2f * PX));
        pose.translate(-0.5f, -0.5f, -0.5f);                 // the cube model is centred on the block
        int light = s.lightCoords;
        out.submitCustomGeometry(pose, RenderTypes.entityCutout(TextureAtlas.LOCATION_BLOCKS), (pp, vc) ->
                ModelBlockRenderer.renderModel(pp, vc, s.cube, 1f, 1f, 1f, light, OverlayTexture.NO_OVERLAY));   // keeps the runes' emission
        pose.popPose();

        // Three rune bands: each turns about its own axis while that axis swings round and nods
        float bright = s.occupied ? 1f : 0.75f;
        for (int i = 0; i < 3; i++) {
            pose.pushPose();
            pose.translate(0.5f, CY * PX, 0.5f);
            pose.mulPose(Axis.YP.rotation(i * 2f * (float) Math.PI / 3f + p * 0.09f));
            pose.mulPose(Axis.XP.rotation(1.0f + 0.3f * (float) Math.sin(t * 0.17f + i * 2.1f)));
            pose.mulPose(Axis.YP.rotation(p * (i % 2 == 1 ? -0.42f : 0.36f)));
            for (int tex = 0; tex < RUNE_TEX.length; tex++) {
                // the lit cutout only claims depth (so the cube cannot paint over it); the unlit copy on top is what you see
                out.submitCustomGeometry(pose, RenderTypes.entityCutoutNoCull(RUNE_TEX[tex]), runes(i, tex, bright, t, 255, false));
                out.order(1).submitCustomGeometry(pose, RenderTypes.eyes(RUNE_TEX[tex]), runes(i, tex, bright, t, 255, true));
                out.order(1).submitCustomGeometry(pose, RenderTypes.eyes(GLOW_TEX[tex]), runes(i, tex, bright, t, 140, true));
            }
            pose.popPose();
        }
    }

    /** The quads of one band that use one rune texture (rune or glow sprite), at alpha (scaled by the flicker). */
    private static SubmitNodeCollector.CustomGeometryRenderer runes(int band, int texture, float bright, float t, int alpha, boolean bothSides) {
        return (pp, vc) -> {
            for (int j = 0; j < BAND_GLYPHS; j++) {
                if ((j * 5 + band * 2) % RUNE_TEX.length != texture) continue;
                float a = j / (float) BAND_GLYPHS * 2f * (float) Math.PI;
                float k = bright * (0.75f + 0.25f * (float) Math.sin(t * 1.2f + j * 1.9f + band));
                Matrix4f m = new Matrix4f(pp.pose())
                        .translate((float) Math.cos(a) * BAND_R * PX, 0f, (float) Math.sin(a) * BAND_R * PX)
                        .rotateY((float) Math.PI / 2f - a);            // face outward from the cube
                glyph(vc, m, k, alpha, bothSides);
            }
        };
    }

    /**
     * A rune sprite quad in its local XY plane. Drawn three times: a depth-writing cutout (lit, so it would take the
     * game's face shading, but it is covered), the same rune unlit and full-bright on top, and the soft glow sprite
     * blended over and around it at about half strength.
     */
    private static void glyph(VertexConsumer vc, Matrix4f m, float k, int alpha, boolean bothSides) {
        int r = (int) (RUNE[0] * k), g = (int) (RUNE[1] * k), b = (int) (RUNE[2] * k), a = alpha == 255 ? 255 : (int) (alpha * k);
        float h = GLYPH / 2f;
        float[][] quad = {{-h, -h, 0, 1}, {h, -h, 1, 1}, {h, h, 1, 0}, {-h, h, 0, 0}};
        for (int side = 0; side < (bothSides ? 2 : 1); side++) {   // the unlit type culls back faces: give it both windings
            for (int n = 0; n < 4; n++) {
                float[] v = quad[side == 0 ? n : 3 - n];
                vc.addVertex(m, v[0], v[1], bothSides ? (side == 0 ? LIFT : -LIFT) : 0f).setColor(r, g, b, a).setUv(v[2], v[3])
                        .setOverlay(OverlayTexture.NO_OVERLAY).setLight(0xF000F0).setNormal(0f, 1f, 0f);
            }
        }
    }

    /** The end portal inside the cube: a box of half-width w and half-height h, faces wound outward. */
    private static void portal(VertexConsumer vc, Matrix4f m, float w, float h) {
        // south, north, east, west, down, up (same winding as the vanilla end portal cube)
        quad(vc, m, -w, -h, w, w, -h, w, w, h, w, -w, h, w);
        quad(vc, m, -w, h, -w, w, h, -w, w, -h, -w, -w, -h, -w);
        quad(vc, m, w, h, w, w, -h, w, w, -h, -w, w, h, -w);
        quad(vc, m, -w, -h, w, -w, h, w, -w, h, -w, -w, -h, -w);
        quad(vc, m, -w, -h, -w, w, -h, -w, w, -h, w, -w, -h, w);
        quad(vc, m, -w, h, w, w, h, w, w, h, -w, -w, h, -w);
    }

    private static void quad(VertexConsumer vc, Matrix4f m, float... p) {
        for (int i = 0; i < 12; i += 3) vc.addVertex(m, p[i], p[i + 1], p[i + 2]);
    }
}
