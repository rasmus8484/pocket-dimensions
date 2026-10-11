package com.pocketdimensions.mixin;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.framegraph.FrameGraphBuilder;
import com.mojang.blaze3d.resource.GraphicsResourceAllocator;
import com.pocketdimensions.client.veil.VeilFogRenderer;
import com.pocketdimensions.client.ward.WardSigilRenderer;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LevelTargetBundle;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.state.LevelRenderState;
import net.minecraft.util.profiling.ProfilerFiller;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Two hooks into the world render: the ward's sigil pass just before the main pass, and the Veil fog at the very end.
 * The ward's sigil is drawn in a frame pass of its own, added just before the main (terrain) pass: after the sky, so
 * everything in the world is drawn over it. Forge's frame pass API adds passes after all of vanilla's, too late.
 */
@Mixin(LevelRenderer.class)
public abstract class LevelRendererMixin {

    @Shadow @Final private LevelTargetBundle targets;

    @Inject(method = "addMainPass", at = @At("HEAD"))
    private void pocketdimensions$wardSigilPass(FrameGraphBuilder builder, Frustum frustum, Matrix4f view, GpuBufferSlice fog,
                                                boolean outline, LevelRenderState state, DeltaTracker delta,
                                                ProfilerFiller profiler, CallbackInfo ci) {
        WardSigilRenderer.addPass(builder, targets, state, fog);
    }

    /**
     * The Veil fog is drawn last, after everything in the world and after any shader mod's final pass (Iris runs it
     * inside renderLevel), so nothing paints over it.
     */
    @Inject(method = "renderLevel", at = @At("RETURN"))
    private void pocketdimensions$veilFog(GraphicsResourceAllocator allocator, DeltaTracker delta, boolean blockOutline,
                                          Camera camera, Matrix4f viewRotation, Matrix4f projection, Matrix4f cullingProjection,
                                          GpuBufferSlice fog, Vector4f fogColor, boolean renderSky, CallbackInfo ci) {
        VeilFogRenderer.render(camera, viewRotation, projection, fog, delta.getGameTimeDeltaPartialTick(false));
    }
}
