package com.pocketdimensions.init;

import com.pocketdimensions.PocketDimensionsMod;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModParticles {
    public static final DeferredRegister<ParticleType<?>> PARTICLE_TYPES =
            DeferredRegister.create(ForgeRegistries.PARTICLE_TYPES, PocketDimensionsMod.MODID);

    /** Glowing rune glyph that drifts off a linked World Anchor. */
    public static final RegistryObject<SimpleParticleType> RUNE =
            PARTICLE_TYPES.register("rune", () -> new SimpleParticleType(false));

    /** Rune glyphs tinted by a World Breacher's influence. */
    public static final RegistryObject<SimpleParticleType> RUNE_PINK =
            PARTICLE_TYPES.register("rune_pink", () -> new SimpleParticleType(false));
    public static final RegistryObject<SimpleParticleType> RUNE_GOLD =
            PARTICLE_TYPES.register("rune_gold", () -> new SimpleParticleType(false));

    /** Rune glyphs heated red by an Anchor Breaker. */
    public static final RegistryObject<SimpleParticleType> RUNE_RED =
            PARTICLE_TYPES.register("rune_red", () -> new SimpleParticleType(false));

    /** Pink mote drained from a World Breacher's mandibles into the anchor's black hole. */
    public static final RegistryObject<SimpleParticleType> DRAIN =
            PARTICLE_TYPES.register("drain", () -> new SimpleParticleType(false));

    /** Red mote thrown from the black hole through a window to an Anchor Breaker's clamp foot. */
    public static final RegistryObject<SimpleParticleType> UNMAKE =
            PARTICLE_TYPES.register("unmake", () -> new SimpleParticleType(false));

    /** Red mote siphoned straight up from the black hole into an Anchor Breaker's funnel. */
    public static final RegistryObject<SimpleParticleType> SIPHON =
            PARTICLE_TYPES.register("siphon", () -> new SimpleParticleType(false));
}
