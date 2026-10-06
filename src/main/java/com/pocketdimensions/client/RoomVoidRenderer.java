package com.pocketdimensions.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.pocketdimensions.blockentity.RoomVoidBlockEntity;
import com.pocketdimensions.manager.RoomShell;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.CameraRenderState;
import org.joml.Matrix4f;

/**
 * The pocket room's void: the end-portal effect laid over each of the room's six faces, inside the netherite and gold
 * ring, from the one block entity in the room's floor corner. One renderer per room instead of one per wall block;
 * the effect is screen-space, so six big panels look the same as hundreds of small ones.
 */
public class RoomVoidRenderer implements BlockEntityRenderer<RoomVoidBlockEntity, BlockEntityRenderState> {

    /** Panels sit this far in front of the wall blocks so they never fight them for depth. */
    private static final float LIFT = 0.002f;

    public RoomVoidRenderer(BlockEntityRendererProvider.Context ctx) {}

    @Override
    public BlockEntityRenderState createRenderState() { return new BlockEntityRenderState(); }

    @Override
    public void submit(BlockEntityRenderState s, PoseStack pose, SubmitNodeCollector out, CameraRenderState camera) {
        out.submitCustomGeometry(pose, RenderTypes.endPortal(), (pp, vc) -> {
            Matrix4f m = pp.pose();
            float[][] p = RoomShell.PANELS;
            for (int i = 0; i < p.length; i++) panel(vc, m, i, p[i]);
        });
    }

    /** One panel, in the heart block's space, wound to face into the room (same windings as the vanilla end portal). */
    private static void panel(VertexConsumer vc, Matrix4f m, int face, float[] q) {
        float hx = RoomShell.HEART_X, hy = RoomShell.HEART_Y, hz = RoomShell.HEART_Z;
        float x0 = q[0] - hx, y0 = q[1] - hy, z0 = q[2] - hz, x1 = q[3] - hx, y1 = q[4] - hy, z1 = q[5] - hz;
        switch (face) {
            case 0 -> { float y = y0 + LIFT; quad(vc, m, x0, y, z1, x1, y, z1, x1, y, z0, x0, y, z0); }          // floor, facing up
            case 1 -> { float y = y0 - LIFT; quad(vc, m, x0, y, z0, x1, y, z0, x1, y, z1, x0, y, z1); }          // ceiling, facing down
            case 2 -> { float x = x0 + LIFT; quad(vc, m, x, y1, z1, x, y0, z1, x, y0, z0, x, y1, z0); }          // west wall, facing east
            case 3 -> { float x = x0 - LIFT; quad(vc, m, x, y0, z1, x, y1, z1, x, y1, z0, x, y0, z0); }          // east wall, facing west
            case 4 -> { float z = z0 + LIFT; quad(vc, m, x0, y0, z, x1, y0, z, x1, y1, z, x0, y1, z); }          // north wall, facing south
            default -> { float z = z0 - LIFT; quad(vc, m, x0, y1, z, x1, y1, z, x1, y0, z, x0, y0, z); }         // south wall, facing north
        }
    }

    private static void quad(VertexConsumer vc, Matrix4f m, float... p) {
        for (int i = 0; i < 12; i += 3) vc.addVertex(m, p[i], p[i + 1], p[i + 2]);
    }

    /** Drawn whenever the room is in view, even when the heart's own corner is off screen. */
    @Override
    public boolean shouldRenderOffScreen() { return true; }

    @Override
    public int getViewDistance() { return 96; }
}
