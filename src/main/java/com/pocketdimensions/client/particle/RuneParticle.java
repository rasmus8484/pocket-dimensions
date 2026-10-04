package com.pocketdimensions.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.RandomSource;

/** A glowing rune glyph that drifts slowly away from the World Anchor and fades out. */
public class RuneParticle extends SingleQuadParticle {

    RuneParticle(ClientLevel level, double x, double y, double z, double xd, double yd, double zd, TextureAtlasSprite sprite) {
        super(level, x, y, z, sprite);
        this.xd = xd; this.yd = yd; this.zd = zd;
        this.hasPhysics = false;
        this.gravity = 0f;
        this.friction = 1f;
        this.lifetime = 60 + this.random.nextInt(31);
        this.quadSize = 0.11f;
        this.rCol = 140 / 255f; this.gCol = 235 / 255f; this.bCol = 1f;
        this.setAlpha(0f);
    }

    @Override
    public void tick() {
        super.tick();
        float f = (float) this.age / this.lifetime;
        this.setAlpha(Math.min(1f, this.age / 6f) * (1f - f) * 0.85f);
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
            return new RuneParticle(level, x, y, z, xd, yd, zd, sprites.get(random));
        }
    }
}
