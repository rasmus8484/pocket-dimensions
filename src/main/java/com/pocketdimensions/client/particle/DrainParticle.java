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
 * A pink mote drained from a World Breacher's mandible into the anchor's black hole.
 * Spawned at a hook; (xd, yd, zd) is the offset to the target (the core). It follows a quadratic
 * curve whose control point sits over the window on one of the two faces next to the hook.
 */
public class DrainParticle extends SingleQuadParticle {

    private final double sx, sy, sz, cx, cy, cz, tx, ty, tz;

    DrainParticle(ClientLevel level, double x, double y, double z, double dx, double dy, double dz, TextureAtlasSprite sprite) {
        super(level, x, y, z, sprite);
        this.sx = x; this.sy = y; this.sz = z;
        this.tx = x + dx; this.ty = y + dy; this.tz = z + dz;
        boolean alongX = this.random.nextBoolean();
        this.cx = alongX ? x : tx;
        this.cy = ty + 5 / 16.0;
        this.cz = alongX ? tz : z;
        this.hasPhysics = false;
        this.lifetime = 60 + this.random.nextInt(21);
        this.quadSize = 0.05f;
        this.rCol = 1f; this.gCol = 112 / 255f; this.bCol = 224 / 255f;
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
        public Provider(SpriteSet sprites) { this.sprites = sprites; }

        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z,
                                       double dx, double dy, double dz, RandomSource random) {
            return new DrainParticle(level, x, y, z, dx, dy, dz, sprites.get(random));
        }
    }
}
