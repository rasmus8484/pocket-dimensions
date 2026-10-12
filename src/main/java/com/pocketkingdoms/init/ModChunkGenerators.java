package com.pocketkingdoms.init;

import com.mojang.serialization.MapCodec;
import com.pocketkingdoms.PocketKingdomsMod;
import com.pocketkingdoms.worldgen.RealmChunkGenerator;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

public class ModChunkGenerators {

    public static final DeferredRegister<MapCodec<? extends ChunkGenerator>> CHUNK_GENERATORS =
            DeferredRegister.create(Registries.CHUNK_GENERATOR, PocketKingdomsMod.MODID);

    public static final RegistryObject<MapCodec<? extends ChunkGenerator>> REALM =
            CHUNK_GENERATORS.register("seeded_noise_no_structures", () -> RealmChunkGenerator.CODEC);
}
