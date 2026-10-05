package com.pocketdimensions.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.RandomSource;

/**
 * A mote travelling between an anchor's black hole and a siege block along a quadratic curve.
 * Spawned at the start; (xd, yd, zd) is the offset to the target.
 * DRAIN (World Breacher, pink): hook to core, control point over a window next to the hook.
 * OUT (Anchor Breaker, red): core to clamp foot, leaving sideways through a window at core height.
 * STRAIGHT (Anchor Breaker siphon, red): a straight line up into the funnel.
 */
public class DrainParticle extends SingleQuadParticle {

    public enum Path { DRAIN, OUT, STRAIGHT }
    public static final int PINK = 0xFF70E0, RED = 0xFF4A2A;

    private final double sx, sy, sz, cx, cy, cz, tx, ty, tz;

    DrainParticle(ClientLevel level, double x, double y, double z, double dx, double dy, double dz,
                  TextureAtlasSprite sprite, int rgb, Path path) {
        super(level, x, y, z, sprite);
        this.sx = x; this.sy = y; this.sz = z;
        this.tx = x + dx; this.ty = y + dy; this.tz = z + dz;
        boolean alongX = this.random.nextBoolean();
        switch (path) {
            case DRAIN -> { cx = alongX ? x : tx; cy = ty + 5 / 16.0; cz = alongX ? tz : z; }
            case OUT -> { cx = alongX ? x : tx; cy = y; cz = alongX ? tz : z; }
            default -> { cx = (x + tx) / 2; cy = (y + ty) / 2; cz = (z + tz) / 2; }
        }
        this.hasPhysics = false;
        this.lifetime = (path == Path.STRAIGHT ? 30 : 60) + this.random.nextInt(21);
        this.quadSize = 0.05f;
        this.rCol = (rgb >> 16 & 0xFF) / 255f; this.gCol = (rgb >> 8 & 0xFF) / 255f; this.bCol = (rgb & 0xFF) / 255f;
        this.setAlpha(0f);
    }

    @Override
    public void tick() {
        this.xo = this.x; this.yo = this.y; this.zo = this.z;
        if (this.age++ >= this.lifetime) { this.remove(); return; }
        double t = (double) this.age / this.lifetime, u = 1 - t;
        this.setPos(u * u * sx + 2 * u * t * cx + t * t * tx,
                    u * u * sy + 2 * u * t * cy + t * t * ty,
                    u * u * sz + 2 * u * t * cz + t * t * tz);
        this.setAlpha((float) (Math.min(1, t * 6) * Math.min(1, (1 - t) * 5) * 0.95));
    }

    @Override
    public int getLightColor(float partialTick) { return 0xF000F0; }   // full bright

    @Override
    protected SingleQuadParticle.Layer getLayer() { return SingleQuadParticle.Layer.TRANSLUCENT; }

    public static class Provider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;
        private final int rgb;
        private final Path path;
        public Provider(SpriteSet sprites, int rgb, Path path) { this.sprites = sprites; this.rgb = rgb; this.path = path; }

        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z,
                                       double dx, double dy, double dz, RandomSource random) {
            return new DrainParticle(level, x, y, z, dx, dy, dz, sprites.get(random), rgb, path);
        }
    }
}
