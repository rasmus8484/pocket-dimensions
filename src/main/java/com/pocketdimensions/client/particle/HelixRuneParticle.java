package com.pocketdimensions.client.particle;

import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.renderer.state.QuadParticleRenderState;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import org.joml.Quaternionf;

/**
 * A rune climbing the World Core's beacon in a slow helix. It is not camera-facing: the glyph lies flat on an
 * invisible cylinder around the beam, facing outward, so the runes read like script wrapped around it.
 * Spawned on the beam axis at the crown; xd = starting angle (strand), zd = colour (0xRRGGBB).
 */
public class HelixRuneParticle extends SingleQuadParticle {

    private static final double RADIUS = 4.5 / 16, RISE = 2.2 / 16 / 20;   // 4.5 px from the axis, 2.2 px per second
    private static final double TURNS = 1.2;                                // turns per climb
    private static final int LIFE = 360;                                    // 18 seconds

    private final double ax, ay, az, a0;
    private double ang, oAng;

    HelixRuneParticle(ClientLevel level, double x, double y, double z, double a0, int rgb, TextureAtlasSprite sprite) {
        super(level, x, y, z, sprite);
        this.ax = x; this.ay = y; this.az = z; this.a0 = a0;
        this.hasPhysics = false;
        this.lifetime = LIFE;
        this.quadSize = 0.13f;
        this.rCol = (rgb >> 16 & 0xFF) / 255f; this.gCol = (rgb >> 8 & 0xFF) / 255f; this.bCol = (rgb & 0xFF) / 255f;
        this.setAlpha(0f);
        place();
        this.oAng = this.ang;
        this.xo = this.x; this.yo = this.y; this.zo = this.z;
    }

    private void place() {
        double f = (double) this.age / this.lifetime;
        this.ang = a0 + f * TURNS * 2 * Math.PI;
        this.setPos(ax + Math.cos(ang) * RADIUS, ay + this.age * RISE, az + Math.sin(ang) * RADIUS);
    }

    @Override
    public void tick() {
        this.xo = this.x; this.yo = this.y; this.zo = this.z;
        this.oAng = this.ang;
        if (this.age++ >= this.lifetime) { this.remove(); return; }
        place();
        float f = (float) this.age / this.lifetime;
        this.setAlpha(Math.min(1f, f * 6) * Math.min(1f, (1 - f) * 3) * 0.85f);
    }

    /** Face outward from the beam axis instead of facing the camera. */
    @Override
    public SingleQuadParticle.FacingCameraMode getFacingCameraMode() {
        return (q, camera, partialTick) -> q.rotationY((float) (Math.PI / 2 - Mth.lerp(partialTick, oAng, ang)));
    }

    /** Particle quads are back-face culled: draw the glyph from both sides. */
    @Override
    public void extract(QuadParticleRenderState state, Camera camera, float partialTick) {
        Quaternionf q = new Quaternionf();
        getFacingCameraMode().setRotation(q, camera, partialTick);
        extractRotatedQuad(state, camera, q, partialTick);
        extractRotatedQuad(state, camera, new Quaternionf(q).rotateY((float) Math.PI), partialTick);
    }

    @Override
    public int getLightColor(float partialTick) { return 0xF000F0; }   // full bright

    @Override
    protected SingleQuadParticle.Layer getLayer() { return SingleQuadParticle.Layer.TRANSLUCENT; }

    public static class Provider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;
        public Provider(SpriteSet sprites) { this.sprites = sprites; }

        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z,
                                       double xd, double yd, double zd, RandomSource random) {
            return new HelixRuneParticle(level, x, y, z, xd, (int) zd, sprites.get(random));
        }
    }
}
