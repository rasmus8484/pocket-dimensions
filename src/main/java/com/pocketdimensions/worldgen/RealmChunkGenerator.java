package com.pocketdimensions.worldgen;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.ints.IntArraySet;
import it.unimi.dsi.fastutil.ints.IntSet;
import it.unimi.dsi.fastutil.objects.ObjectArraySet;
import net.minecraft.CrashReport;
import net.minecraft.ReportedException;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeGenerationSettings;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.FeatureSorter;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.RandomSupport;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.XoroshiroRandomSource;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import org.jspecify.annotations.Nullable;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * NoiseBasedChunkGenerator with the server config's [realm] rules (RealmWorldRules): only the structures they allow get
 * a start (none by default, so /locate finds nothing), and features they block (dungeons by default) are skipped when
 * biomes decorate a chunk. With everything allowed it is plain vanilla generation.
 */
public class RealmChunkGenerator extends NoiseBasedChunkGenerator {

    public static final MapCodec<RealmChunkGenerator> CODEC = RecordCodecBuilder.mapCodec(
            instance -> instance.group(
                    BiomeSource.CODEC.fieldOf("biome_source").forGetter(g -> g.biomeSource),
                    NoiseGeneratorSettings.CODEC.fieldOf("settings").forGetter(g -> g.generatorSettings())
            ).apply(instance, instance.stable(RealmChunkGenerator::new))
    );

    /** ChunkGenerator keeps the sorted per-step feature lists and the biome settings lookup private; found by type. */
    private static Field featuresPerStepField, settingsGetterField;

    public RealmChunkGenerator(BiomeSource biomeSource, Holder<NoiseGeneratorSettings> settings) {
        super(biomeSource, settings);
    }

    @Override
    protected MapCodec<? extends RealmChunkGenerator> codec() {
        return CODEC;
    }

    // -------------------------------------------------------------------------
    // Structures
    // -------------------------------------------------------------------------

    /** Vanilla's starts, minus the structures the rules don't allow (with none allowed, nothing is even tried). */
    @Override
    public void createStructures(RegistryAccess registryAccess, ChunkGeneratorStructureState structureState,
                                 StructureManager structureManager, ChunkAccess chunk,
                                 StructureTemplateManager templateManager, ResourceKey<Level> dimension) {
        if (RealmWorldRules.noStructures()) return;
        super.createStructures(registryAccess, structureState, structureManager, chunk, templateManager, dimension);
        Registry<Structure> registry = registryAccess.lookupOrThrow(Registries.STRUCTURE);
        Map<Structure, StructureStart> starts = new HashMap<>(chunk.getAllStarts());
        if (starts.keySet().removeIf(s -> !RealmWorldRules.structureAllowed(registry, s))) chunk.setAllStarts(starts);
    }

    @Override
    public void createReferences(WorldGenLevel level, StructureManager structureManager, ChunkAccess chunk) {
        if (!RealmWorldRules.noStructures()) super.createReferences(level, structureManager, chunk);
    }

    /** /locate and explorer maps only look for structures the rules allow. */
    @Override
    public @Nullable Pair<BlockPos, Holder<Structure>> findNearestMapStructure(ServerLevel level, HolderSet<Structure> structures,
                                                                               BlockPos pos, int radius, boolean skipKnownStructures) {
        if (RealmWorldRules.noStructures()) return null;
        Registry<Structure> registry = level.registryAccess().lookupOrThrow(Registries.STRUCTURE);
        List<Holder<Structure>> allowed = structures.stream()
                .filter(h -> RealmWorldRules.structureAllowed(registry, h.value())).toList();
        if (allowed.isEmpty()) return null;
        return super.findNearestMapStructure(level, HolderSet.direct(allowed), pos, radius, skipKnownStructures);
    }

    // -------------------------------------------------------------------------
    // Features
    // -------------------------------------------------------------------------

    /**
     * ChunkGenerator.applyBiomeDecoration (1.21.11) with one change: a placed feature the rules don't allow is skipped.
     * Seeds and order are vanilla's, so every feature that is placed lands exactly where vanilla would put it.
     */
    @Override
    public void applyBiomeDecoration(WorldGenLevel level, ChunkAccess chunk, StructureManager structureManager) {
        if (RealmWorldRules.allFeatures() || !reflectionReady()) {
            super.applyBiomeDecoration(level, chunk, structureManager);
            return;
        }
        List<FeatureSorter.StepFeatureData> steps = featuresPerStep();
        Function<Holder<Biome>, BiomeGenerationSettings> settingsGetter = settingsGetter();
        ChunkPos chunkpos = chunk.getPos();
        SectionPos sectionpos = SectionPos.of(chunkpos, level.getMinSectionY());
        BlockPos blockpos = sectionpos.origin();
        Registry<Structure> registry = level.registryAccess().lookupOrThrow(Registries.STRUCTURE);
        Map<Integer, List<Structure>> map = registry.stream().collect(Collectors.groupingBy(s -> s.step().ordinal()));
        WorldgenRandom worldgenrandom = new WorldgenRandom(new XoroshiroRandomSource(RandomSupport.generateUniqueSeed()));
        long i = worldgenrandom.setDecorationSeed(level.getSeed(), blockpos.getX(), blockpos.getZ());
        Set<Holder<Biome>> set = new ObjectArraySet<>();
        ChunkPos.rangeClosed(sectionpos.chunk(), 1).forEach(p -> {
            ChunkAccess c = level.getChunk(p.x, p.z);
            for (var section : c.getSections()) section.getBiomes().getAll(set::add);
        });
        set.retainAll(this.biomeSource.possibleBiomes());
        int j = steps.size();

        try {
            Registry<PlacedFeature> features = level.registryAccess().lookupOrThrow(Registries.PLACED_FEATURE);
            int stepCount = Math.max(GenerationStep.Decoration.values().length, j);

            for (int k = 0; k < stepCount; k++) {
                int l = 0;
                if (structureManager.shouldGenerateStructures()) {
                    for (Structure structure : map.getOrDefault(k, Collections.emptyList())) {
                        worldgenrandom.setFeatureSeed(i, l, k);
                        Supplier<String> name = () -> registry.getResourceKey(structure).map(Object::toString).orElseGet(structure::toString);
                        try {
                            level.setCurrentlyGenerating(name);
                            structureManager.startsForStructure(sectionpos, structure)
                                    .forEach(s -> s.placeInChunk(level, structureManager, this, worldgenrandom, writableArea(chunk), chunkpos));
                        } catch (Exception e) {
                            CrashReport report = CrashReport.forThrowable(e, "Feature placement");
                            report.addCategory("Feature").setDetail("Description", name::get);
                            throw new ReportedException(report);
                        }
                        l++;
                    }
                }

                if (k < j) {
                    IntSet intset = new IntArraySet();
                    for (Holder<Biome> holder : set) {
                        List<HolderSet<PlacedFeature>> perStep = settingsGetter.apply(holder).features();
                        if (k < perStep.size()) {
                            FeatureSorter.StepFeatureData data = steps.get(k);
                            perStep.get(k).stream().map(Holder::value).forEach(f -> intset.add(data.indexMapping().applyAsInt(f)));
                        }
                    }
                    int[] order = intset.toIntArray();
                    Arrays.sort(order);
                    FeatureSorter.StepFeatureData data = steps.get(k);

                    for (int index : order) {
                        PlacedFeature feature = data.features().get(index);
                        if (!RealmWorldRules.featureAllowed(features, feature)) continue;      // the one change
                        Supplier<String> name = () -> features.getResourceKey(feature).map(Object::toString).orElseGet(feature::toString);
                        worldgenrandom.setFeatureSeed(i, index, k);
                        try {
                            level.setCurrentlyGenerating(name);
                            feature.placeWithBiomeCheck(level, this, worldgenrandom, blockpos);
                        } catch (Exception e) {
                            CrashReport report = CrashReport.forThrowable(e, "Feature placement");
                            report.addCategory("Feature").setDetail("Description", name::get);
                            throw new ReportedException(report);
                        }
                    }
                }
            }
            level.setCurrentlyGenerating(null);
        } catch (Exception e) {
            CrashReport report = CrashReport.forThrowable(e, "Biome decoration");
            report.addCategory("Generation").setDetail("CenterX", chunkpos.x).setDetail("CenterZ", chunkpos.z)
                    .setDetail("Decoration Seed", i);
            throw new ReportedException(report);
        }
    }

    private static BoundingBox writableArea(ChunkAccess chunk) {
        ChunkPos pos = chunk.getPos();
        LevelHeightAccessor h = chunk.getHeightAccessorForGeneration();
        return new BoundingBox(pos.getMinBlockX(), h.getMinY() + 1, pos.getMinBlockZ(), pos.getMinBlockX() + 15, h.getMaxY(), pos.getMinBlockZ() + 15);
    }

    private static boolean reflectionReady() {
        if (featuresPerStepField != null && settingsGetterField != null) return true;
        for (Field f : ChunkGenerator.class.getDeclaredFields()) {
            if (f.getType() == Function.class) { f.setAccessible(true); settingsGetterField = f; }
            else if (f.getType().getSimpleName().equals("ClearableLazy")) { f.setAccessible(true); featuresPerStepField = f; }
        }
        return featuresPerStepField != null && settingsGetterField != null;
    }

    @SuppressWarnings("unchecked")
    private List<FeatureSorter.StepFeatureData> featuresPerStep() {
        try {
            return ((Supplier<List<FeatureSorter.StepFeatureData>>) featuresPerStepField.get(this)).get();
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }

    @SuppressWarnings("unchecked")
    private Function<Holder<Biome>, BiomeGenerationSettings> settingsGetter() {
        try {
            return (Function<Holder<Biome>, BiomeGenerationSettings>) settingsGetterField.get(this);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }
}
