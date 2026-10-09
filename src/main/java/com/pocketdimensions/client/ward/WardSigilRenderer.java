package com.pocketdimensions.client.ward;

import com.mojang.blaze3d.framegraph.FrameGraphBuilder;
import com.mojang.blaze3d.framegraph.FramePass;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.Tesselator;
import com.pocketdimensions.PocketDimensionsMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelTargetBundle;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * SPIKE: draws one test rune quad, 48 blocks across, hanging at y 340 over a fixed point near the player, pulled toward
 * the camera so it stays inside the fog-free zone. Only checks the hook, the draw order and the pull.
 */
public final class WardSigilRenderer {

    private static final Identifier RUNE = Identifier.fromNamespaceAndPath(PocketDimensionsMod.MODID, "textures/particle/rune_0.png");

    private WardSigilRenderer() {}

    public static void addPass(FrameGraphBuilder builder, LevelTargetBundle targets) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || !mc.level.dimension().equals(PocketDimensionsMod.REALM_DIM)) return;
        FramePass pass = builder.addPass("pocketdimensions_ward_sigil");
        targets.main = pass.readsAndWrites(targets.main);
        pass.executes(WardSigilRenderer::draw);
    }

    private static void draw() {
        Minecraft mc = Minecraft.getInstance();
        Vec3 cam = mc.gameRenderer.getMainCamera().position();
        double cx = Math.floor(cam.x / 256) * 256 + 128, cz = Math.floor(cam.z / 256) * 256 + 128, cy = 340;
        double half = 24;
        double dx = cx - cam.x, dy = cy - cam.y, dz = cz - cam.z;
        double d = Math.sqrt(dx * dx + dy * dy + dz * dz);
        float rd = mc.options.getEffectiveRenderDistance() * 16f;
        float fogStart = rd - Mth.clamp(rd / 10f, 4f, 64f);
        double k = Math.min(1, Math.min(96, 0.9 * fogStart) / d);
        RenderType type = RenderTypes.eyes(RUNE);
        BufferBuilder buf = Tesselator.getInstance().begin(type.mode(), type.format());
        float[][] corners = {{-1, -1, 0, 1}, {1, -1, 1, 1}, {1, 1, 1, 0}, {-1, 1, 0, 0}};
        for (float[] c : corners) {
            float x = (float) ((dx + c[0] * half) * k), y = (float) (dy * k), z = (float) ((dz + c[1] * half) * k);
            buf.addVertex(x, y, z).setColor(140, 235, 255, 230).setUv(c[2], c[3])
                    .setOverlay(OverlayTexture.NO_OVERLAY).setLight(0xF000F0).setNormal(0, -1, 0);
        }
        try (MeshData mesh = buf.buildOrThrow()) { type.draw(mesh); }
    }
}
