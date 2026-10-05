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

    /** Rune colours: the anchor's own cyan, and the breacher's pink and gold. */
    public static final int CYAN = 0x8CEBFF, PINK = 0xFF5ADC, GOLD = 0xFFCD5F, RED = 0xFF5028;

    RuneParticle(ClientLevel level, double x, double y, double z, double xd, double yd, double zd, TextureAtlasSprite sprite, int rgb) {
        super(level, x, y, z, sprite);
        this.xd = xd; this.yd = yd; this.zd = zd;
        this.hasPhysics = false;
        this.gravity = 0f;
        this.friction = 1f;
        this.lifetime = 60 + this.random.nextInt(31);
        this.quadSize = 0.11f;
        this.rCol = (rgb >> 16 & 0xFF) / 255f; this.gCol = (rgb >> 8 & 0xFF) / 255f; this.bCol = (rgb & 0xFF) / 255f;
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
        private final int rgb;
        public Provider(SpriteSet sprites, int rgb) { this.sprites = sprites; this.rgb = rgb; }

        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z,
                                       double xd, double yd, double zd, RandomSource random) {
            return new RuneParticle(level, x, y, z, xd, yd, zd, sprites.get(random), rgb);
        }
    }
}
