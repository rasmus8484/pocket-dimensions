package com.pocketdimensions.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.pocketdimensions.PocketDimensionsMod;
import com.pocketdimensions.block.PocketAnchorBlock;
import com.pocketdimensions.blockentity.PocketAnchorBlockEntity;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
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
    private static final Identifier[] RUNE_TEX = new Identifier[6];
    static {
        for (int i = 0; i < 6; i++) RUNE_TEX[i] = Identifier.fromNamespaceAndPath(PocketDimensionsMod.MODID, "textures/particle/rune_" + i + ".png");
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
        s.cube = be.getBlockState().setValue(PocketAnchorBlock.CUBE, true).setValue(PocketAnchorBlock.OCCUPIED, false);
    }

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
        out.submitBlock(pose, s.cube, s.lightCoords, OverlayTexture.NO_OVERLAY, 0);
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
                final int band = i, texture = tex;
                out.submitCustomGeometry(pose, RenderTypes.entityTranslucentEmissive(RUNE_TEX[tex]), (pp, vc) -> {
                    for (int j = 0; j < BAND_GLYPHS; j++) {
                        if ((j * 5 + band * 2) % RUNE_TEX.length != texture) continue;
                        float a = j / (float) BAND_GLYPHS * 2f * (float) Math.PI;
                        float k = bright * (0.75f + 0.25f * (float) Math.sin(t * 1.2f + j * 1.9f + band));
                        Matrix4f m = new Matrix4f(pp.pose())
                                .translate((float) Math.cos(a) * BAND_R * PX, 0f, (float) Math.sin(a) * BAND_R * PX)
                                .rotateY((float) Math.PI / 2f - a);            // face outward from the cube
                        glyph(vc, pp, m, k);
                    }
                });
            }
            pose.popPose();
        }
    }

    /** A rune sprite quad in its local XY plane, drawn from both sides, full-bright. It writes depth (transparent texels are
     *  discarded), so a glyph in front of the cube stays visible whichever is drawn first. */
    private static void glyph(VertexConsumer vc, PoseStack.Pose pp, Matrix4f m, float k) {
        int r = (int) (RUNE[0] * k), g = (int) (RUNE[1] * k), b = (int) (RUNE[2] * k);
        float h = GLYPH / 2f;
        float[][] front = {{-h, -h, 0, 1}, {h, -h, 1, 1}, {h, h, 1, 0}, {-h, h, 0, 0}};
        for (int side = 0; side < 2; side++) {
            for (int n = 0; n < 4; n++) {
                float[] v = front[side == 0 ? n : 3 - n];
                vc.addVertex(m, v[0], v[1], 0f).setColor(r, g, b, 255).setUv(v[2], v[3])
                        .setOverlay(OverlayTexture.NO_OVERLAY).setLight(0xF000F0).setNormal(pp, 0f, 0f, side == 0 ? 1f : -1f);
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
