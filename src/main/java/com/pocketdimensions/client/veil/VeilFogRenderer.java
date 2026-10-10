package com.pocketdimensions.client.veil;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.DestFactor;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.platform.SourceFactor;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.GpuQuery;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.textures.TextureFormat;
import com.pocketdimensions.PocketDimensionsClientConfig;
import com.pocketdimensions.PocketDimensionsMod;
import com.pocketdimensions.client.ward.WardSigilRenderer;
import com.pocketdimensions.client.ward.WardView;
import com.pocketdimensions.network.WardSyncS2C;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MappableRingBuffer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.joml.Matrix4f;
import org.jspecify.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.Locale;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.OptionalLong;

/**
 * The Veil fog: falling glowing smoke along the realm's four edges, one full-screen pass drawn at the very end of the
 * world render (LevelRendererMixin, RETURN of renderLevel). That is after any shader mod's final pass (Iris and its
 * Oculus ports overwrite the screen inside renderLevel), so the fog is never wiped; the hand is drawn later by
 * GameRenderer and never fogged. Reads the main depth texture (also Iris's scene depth) and the noise atlas, and blends
 * light and transmittance onto the frame. /pd test veilTime times it on the GPU (VeilFogTimer).
 */
public final class VeilFogRenderer {

    private static final Logger LOG = LogManager.getLogger();
    private static final int UBO_SIZE = 16 * 4 + 3 * 16;   // mat4 + 3 vec4, std140

    private static final RenderPipeline PIPELINE = RenderPipeline.builder(RenderPipelines.POST_PROCESSING_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath(PocketDimensionsMod.MODID, "pipeline/veil_fog"))
            .withVertexShader("core/screenquad")
            .withFragmentShader(Identifier.fromNamespaceAndPath(PocketDimensionsMod.MODID, "core/veil_fog"))
            .withSampler("DepthSampler")
            .withSampler("NoiseSampler")
            .withUniform("VeilFog", UniformType.UNIFORM_BUFFER)
            .withUniform("Fog", UniformType.UNIFORM_BUFFER)
            .withBlend(new BlendFunction(SourceFactor.ONE, DestFactor.SRC_ALPHA, SourceFactor.ZERO, DestFactor.ONE))
            .build();

    private static @Nullable GpuTexture noise;
    private static @Nullable GpuTextureView noiseView;
    private static @Nullable MappableRingBuffer ubo;
    private static boolean ready, failed;
    private static final VeilFogTimer timer = new VeilFogTimer();
    private static final ArrayDeque<GpuQuery> pending = new ArrayDeque<>();

    private VeilFogRenderer() {}

    /** Reports /pd test veilTime in chat once its window closes. */
    public static void register() {
        TickEvent.ClientTickEvent.Post.BUS.addListener(e -> {
            VeilFogTimer.Report report = timer.poll(System.nanoTime());
            Minecraft mc = Minecraft.getInstance();
            if (report != null && mc.player != null) mc.player.displayClientMessage(Component.literal(report.describe()), false);
        });
    }

    /** /pd test veilTime: measure the next `seconds` of frames. */
    public static void measure(float seconds) {
        timer.start(seconds, System.nanoTime());
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null)
            mc.player.displayClientMessage(Component.literal(String.format(Locale.ROOT,
                    "Measuring the Veil fog for %.1f s. Look at the Veil as you normally would.", seconds)), false);
    }

    /** Called at the end of LevelRenderer.renderLevel with its camera, matrices and fog. */
    public static void render(Camera camera, Matrix4f viewRotation, Matrix4f projection, GpuBufferSlice fog, float partialTick) {
        collectTimings();
        Minecraft mc = Minecraft.getInstance();
        if (failed || mc.level == null || !mc.level.dimension().equals(PocketDimensionsMod.REALM_DIM)) return;
        if (!PocketDimensionsClientConfig.VEIL_FOG.get()) return;
        WardView.State ward = WardView.current();
        if (ward == null || WardSigilRenderer.skyHidden(camera)) return;
        try {
            if (!ready) setUp();
            if (failed) return;
            draw(mc, camera, ward.sync(), viewRotation, projection, fog, partialTick);
        } catch (RuntimeException e) {
            failed = true;
            LOG.warn("The Veil fog could not be drawn and is off until the game restarts; the Veil blocks still show.", e);
        }
    }

    private static void setUp() {
        ready = true;
        GpuDevice device = RenderSystem.getDevice();
        if (!device.precompilePipeline(PIPELINE).isValid()) {
            failed = true;
            LOG.warn("The Veil fog shader did not compile on this graphics card; the fog is off, the Veil blocks still show.");
            return;
        }
        int[] pixels = VeilNoise.atlas();
        try (NativeImage image = new NativeImage(VeilNoise.ATLAS_W, VeilNoise.ATLAS_H, false)) {
            for (int y = 0; y < VeilNoise.ATLAS_H; y++)
                for (int x = 0; x < VeilNoise.ATLAS_W; x++) image.setPixel(x, y, pixels[y * VeilNoise.ATLAS_W + x]);
            noise = device.createTexture("Veil fog noise", GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING,
                    TextureFormat.RGBA8, VeilNoise.ATLAS_W, VeilNoise.ATLAS_H, 1, 1);
            device.createCommandEncoder().writeToTexture(noise, image);
        }
        noiseView = device.createTextureView(noise);
        ubo = new MappableRingBuffer(() -> "Veil fog", GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_MAP_WRITE, UBO_SIZE);
    }

    private static void draw(Minecraft mc, Camera camera, WardSyncS2C plot, Matrix4f viewRotation, Matrix4f projection,
                             GpuBufferSlice fog, float partialTick) {
        RenderTarget main = mc.getMainRenderTarget();
        if (main.getDepthTextureView() == null) return;
        Vec3 pos = camera.position();
        float[] cam = VeilFogUniforms.camera(pos.x, pos.y, pos.z, plot.minX(), plot.minZ());
        Matrix4f invViewProj = new Matrix4f(projection).mul(viewRotation).invert();
        int minY = mc.level.getMinY(), height = mc.level.getHeight();

        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
        try (GpuBuffer.MappedView view = encoder.mapBuffer(ubo.currentBuffer(), false, true)) {
            Std140Builder.intoBuffer(view.data())
                    .putMat4f(invViewProj)
                    .putVec4(cam[0], cam[1], cam[2], 0)
                    .putVec4(plot.maxX() - plot.minX(), plot.maxZ() - plot.minZ(),
                            VeilFogUniforms.time(mc.level.getGameTime(), partialTick), VeilFogUniforms.night(mc.level.getSkyDarken()))
                    .putVec4((float) VeilFogMath.fadeStart(minY, height), (float) VeilFogMath.fadeEnd(minY, height),
                            PocketDimensionsClientConfig.VEIL_FOG_STEPS.get(), 0);
        }
        GpuQuery query = timer.measuring() && pending.size() < 8 ? encoder.timerQueryBegin() : null;
        try (RenderPass pass = encoder.createRenderPass(() -> "Veil fog", main.getColorTextureView(), OptionalInt.empty(),
                null, OptionalDouble.empty())) {
            pass.setPipeline(PIPELINE);
            pass.setUniform("VeilFog", ubo.currentBuffer());
            pass.setUniform("Fog", fog);
            pass.bindTexture("DepthSampler", main.getDepthTextureView(), RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST));
            pass.bindTexture("NoiseSampler", noiseView, RenderSystem.getSamplerCache().getRepeat(FilterMode.LINEAR));
            pass.draw(0, 3);
        }
        if (query != null) {
            encoder.timerQueryEnd(query);
            pending.add(query);
        }
        ubo.rotate();
    }

    /** GPU timings arrive a frame or two late: hand over the ones that are ready. */
    private static void collectTimings() {
        while (!pending.isEmpty()) {
            OptionalLong value = pending.peek().getValue();
            if (value.isEmpty()) return;
            timer.frame(value.getAsLong());
            pending.poll().close();
        }
    }
}
