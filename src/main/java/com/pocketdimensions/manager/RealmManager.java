package com.pocketdimensions.manager;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.pocketdimensions.PocketDimensionsConfig;
import com.pocketdimensions.block.WorldCoreBlock;
import com.pocketdimensions.blockentity.WorldAnchorBlockEntity;
import com.pocketdimensions.blockentity.WorldCoreBlockEntity;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import com.pocketdimensions.init.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Server-side SavedData tracking all realm allocations, anchor bindings, and entry locations.
 * Stored in overworld data storage under "realm_manager".
 *
 * Plot geometry (chunk-aligned):
 *   radiusChunks()  r   - config, default 2
 *   sideChunks()    2r-1 - chunks per plot side (default 3 -> 48 blocks)
 *   paddingChunks() p   - gap chunks between plots (default 1)
 *   cellChunks()    sideChunks+paddingChunks - stride between plot origins in chunks
 *   Plot origin (chunks): ((plotIndex % PLOTS_PER_ROW) * cellChunks(), (plotIndex / PLOTS_PER_ROW) * cellChunks())
 *   Realm area (blocks):  originChunk*16 to (originChunk+sideChunks)*16
 *   WorldCore:   closest dry-land surface within searchRadius chunks of plot center
 *   Spawn pos:   WorldCore + (1, 0, 0)
 *   Plots with no dry land are added to invalidPlots and skipped on future allocation.
 */
public class RealmManager extends SavedData {

    // -------------------------------------------------------------------------
    // Constants
    // -------------------------------------------------------------------------

    private static final String DATA_KEY   = "realm_manager";
    private static final int PLOTS_PER_ROW = 10000;
    private static final int REALM_BASE_Y  = 64;

    // Read from config - not cached, so hot-reloads work (though changing mid-game is discouraged)
    private static int radiusChunks()    { return PocketDimensionsConfig.REALM_RADIUS_CHUNKS.get(); }
    private static int paddingChunks()   { return PocketDimensionsConfig.REALM_PADDING_CHUNKS.get(); }
    private static int sideChunks()      { return 2 * radiusChunks() - 1; }
    private static int cellChunks()      { return sideChunks() + paddingChunks(); }
    private static int maxSearchChunks() { return PocketDimensionsConfig.MAX_SPAWN_SEARCH_CHUNKS.get(); }

    // -------------------------------------------------------------------------
    // Inner data classes
    // -------------------------------------------------------------------------

    public static class RealmData {
        public final int  plotIndex;
        public final UUID ownerUUID;
        public boolean    generated   = false;
        public @Nullable String   anchorDimKey  = null;
        public @Nullable BlockPos anchorPos     = null;
        public @Nullable BlockPos worldCorePos  = null;
        public long createdGameTime = 0;
        public final List<UUID> allowedPlayers = new ArrayList<>();
        /** Allowed players the owner crowned: they can use the core's Access and Manage tabs. */
        public final Set<UUID> managers = new LinkedHashSet<>();
        /** The realm's name ("" = unnamed, shown as "Realm of <owner>"). */
        public String name = "";

        public RealmData(int plotIndex, UUID ownerUUID) {
            this.plotIndex = plotIndex;
            this.ownerUUID = ownerUUID;
        }
    }

    public static class EntryLocation {
        public final ResourceKey<Level> dimension;
        public final double x, y, z;
        public final float yaw, pitch;

        public EntryLocation(ResourceKey<Level> dimension,
                             double x, double y, double z, float yaw, float pitch) {
            this.dimension = dimension;
            this.x = x; this.y = y; this.z = z;
            this.yaw = yaw; this.pitch = pitch;
        }
    }

    public static class PlayerRealmInfo {
        public final UUID realmOwner;
        public final int minX, maxX, minZ, maxZ;
        public final int spawnX, spawnY, spawnZ;

        public PlayerRealmInfo(UUID realmOwner, int minX, int maxX, int minZ, int maxZ,
                               int spawnX, int spawnY, int spawnZ) {
            this.realmOwner = realmOwner;
            this.minX = minX; this.maxX = maxX;
            this.minZ = minZ; this.maxZ = maxZ;
            this.spawnX = spawnX; this.spawnY = spawnY; this.spawnZ = spawnZ;
        }

        public boolean contains(double x, double z) {
            return x >= minX && x < maxX && z >= minZ && z < maxZ;
        }
    }

    // -------------------------------------------------------------------------
    // Codec helpers
    // -------------------------------------------------------------------------

    private record RealmEntry(UUID ownerUUID, int plotIndex, boolean generated,
                              Optional<String> anchorDimKey,
                              Optional<Long> anchorPosLong,
                              Optional<Long> worldCorePosLong,
                              long createdGameTime,
                              List<UUID> allowedPlayers,
                              List<UUID> managers,
                              String name) {

        static final Codec<RealmEntry> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                UUIDUtil.CODEC.fieldOf("ownerUUID").forGetter(RealmEntry::ownerUUID),
                Codec.INT.fieldOf("plotIndex").forGetter(RealmEntry::plotIndex),
                Codec.BOOL.optionalFieldOf("generated", false).forGetter(RealmEntry::generated),
                Codec.STRING.optionalFieldOf("anchorDimKey").forGetter(RealmEntry::anchorDimKey),
                Codec.LONG.optionalFieldOf("anchorPosLong").forGetter(RealmEntry::anchorPosLong),
                Codec.LONG.optionalFieldOf("worldCorePosLong").forGetter(RealmEntry::worldCorePosLong),
                Codec.LONG.optionalFieldOf("createdGameTime", 0L).forGetter(RealmEntry::createdGameTime),
                UUIDUtil.CODEC.listOf().optionalFieldOf("allowedPlayers", List.of()).forGetter(RealmEntry::allowedPlayers),
                UUIDUtil.CODEC.listOf().optionalFieldOf("managers", List.of()).forGetter(RealmEntry::managers),
                Codec.STRING.optionalFieldOf("name", "").forGetter(RealmEntry::name)
        ).apply(instance, RealmEntry::new));

        static RealmEntry from(UUID ownerUUID, RealmData data) {
            return new RealmEntry(
                    ownerUUID, data.plotIndex, data.generated,
                    Optional.ofNullable(data.anchorDimKey),
                    Optional.ofNullable(data.anchorPos).map(BlockPos::asLong),
                    Optional.ofNullable(data.worldCorePos).map(BlockPos::asLong),
                    data.createdGameTime,
                    List.copyOf(data.allowedPlayers),
                    List.copyOf(data.managers),
                    data.name);
        }

        RealmData toData() {
            RealmData d = new RealmData(plotIndex, ownerUUID);
            d.generated       = generated;
            d.anchorDimKey    = anchorDimKey.orElse(null);
            d.anchorPos       = anchorPosLong.map(BlockPos::of).orElse(null);
            d.worldCorePos    = worldCorePosLong.map(BlockPos::of).orElse(null);
            d.createdGameTime = createdGameTime;
            d.allowedPlayers.addAll(allowedPlayers);
            d.managers.addAll(managers);
            d.name = name;
            return d;
        }
    }

    private record EntryLocEntry(UUID playerUUID, String dimKey,
                                 double x, double y, double z, float yaw, float pitch) {
        static final Codec<EntryLocEntry> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                UUIDUtil.CODEC.fieldOf("playerUUID").forGetter(EntryLocEntry::playerUUID),
                Codec.STRING.fieldOf("dim").forGetter(EntryLocEntry::dimKey),
                Codec.DOUBLE.fieldOf("x").forGetter(EntryLocEntry::x),
                Codec.DOUBLE.fieldOf("y").forGetter(EntryLocEntry::y),
                Codec.DOUBLE.fieldOf("z").forGetter(EntryLocEntry::z),
                Codec.FLOAT.fieldOf("yaw").forGetter(EntryLocEntry::yaw),
                Codec.FLOAT.fieldOf("pitch").forGetter(EntryLocEntry::pitch)
        ).apply(instance, EntryLocEntry::new));
    }

    private record PlayerRealmInfoEntry(UUID playerUUID, UUID realmOwner,
                                        int minX, int maxX, int minZ, int maxZ,
                                        int spawnX, int spawnY, int spawnZ) {
        static final Codec<PlayerRealmInfoEntry> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                UUIDUtil.CODEC.fieldOf("playerUUID").forGetter(PlayerRealmInfoEntry::playerUUID),
                UUIDUtil.CODEC.fieldOf("realmOwner").forGetter(PlayerRealmInfoEntry::realmOwner),
                Codec.INT.fieldOf("minX").forGetter(PlayerRealmInfoEntry::minX),
                Codec.INT.fieldOf("maxX").forGetter(PlayerRealmInfoEntry::maxX),
                Codec.INT.fieldOf("minZ").forGetter(PlayerRealmInfoEntry::minZ),
                Codec.INT.fieldOf("maxZ").forGetter(PlayerRealmInfoEntry::maxZ),
                Codec.INT.fieldOf("spawnX").forGetter(PlayerRealmInfoEntry::spawnX),
                Codec.INT.fieldOf("spawnY").forGetter(PlayerRealmInfoEntry::spawnY),
                Codec.INT.fieldOf("spawnZ").forGetter(PlayerRealmInfoEntry::spawnZ)
        ).apply(instance, PlayerRealmInfoEntry::new));
    }

    // -------------------------------------------------------------------------
    // Main codec and SavedDataType
    // -------------------------------------------------------------------------

    private static final Codec<RealmManager> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.optionalFieldOf("nextPlotIndex", 0).forGetter(m -> m.nextPlotIndex),
            Codec.INT.listOf().optionalFieldOf("freePlotIndices", List.of()).forGetter(m ->
                    new ArrayList<>(m.freePlotIndices)),
            RealmEntry.CODEC.listOf().optionalFieldOf("realms", List.of()).forGetter(m ->
                    m.realms.entrySet().stream()
                            .map(e -> RealmEntry.from(e.getKey(), e.getValue()))
                            .collect(Collectors.toList())),
            EntryLocEntry.CODEC.listOf().optionalFieldOf("entryLocations", List.of()).forGetter(m ->
                    m.entryLocations.entrySet().stream()
                            .map(e -> {
                                EntryLocation loc = e.getValue();
                                return new EntryLocEntry(e.getKey(),
                                        loc.dimension.identifier().toString(),
                                        loc.x, loc.y, loc.z, loc.yaw, loc.pitch);
                            })
                            .collect(Collectors.toList())),
            PlayerRealmInfoEntry.CODEC.listOf().optionalFieldOf("playerRealmInfos", List.of()).forGetter(m ->
                    m.playerRealmInfos.entrySet().stream()
                            .map(e -> {
                                PlayerRealmInfo pri = e.getValue();
                                return new PlayerRealmInfoEntry(e.getKey(), pri.realmOwner,
                                        pri.minX, pri.maxX, pri.minZ, pri.maxZ,
                                        pri.spawnX, pri.spawnY, pri.spawnZ);
                            })
                            .collect(Collectors.toList())),
            Codec.INT.listOf().optionalFieldOf("invalidPlots", List.of()).forGetter(m ->
                    new ArrayList<>(m.invalidPlots))
    ).apply(instance, RealmManager::fromCodecData));

    private static RealmManager fromCodecData(int nextPlotIndex,
                                              List<Integer> freePlotIndexList,
                                              List<RealmEntry> realmEntries,
                                              List<EntryLocEntry> entryLocEntries,
                                              List<PlayerRealmInfoEntry> playerRealmInfoEntries,
                                              List<Integer> invalidPlotList) {
        RealmManager mgr = new RealmManager();
        mgr.nextPlotIndex = nextPlotIndex;
        mgr.freePlotIndices.addAll(freePlotIndexList);
        mgr.invalidPlots.addAll(invalidPlotList);
        for (RealmEntry e : realmEntries) mgr.realms.put(e.ownerUUID(), e.toData());
        for (EntryLocEntry e : entryLocEntries) {
            Identifier rl = Identifier.tryParse(e.dimKey());
            if (rl == null) continue;
            ResourceKey<Level> dim = ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION, rl);
            mgr.entryLocations.put(e.playerUUID(),
                    new EntryLocation(dim, e.x(), e.y(), e.z(), e.yaw(), e.pitch()));
        }
        for (PlayerRealmInfoEntry e : playerRealmInfoEntries) {
            mgr.playerRealmInfos.put(e.playerUUID(), new PlayerRealmInfo(
                    e.realmOwner(), e.minX(), e.maxX(), e.minZ(), e.maxZ(),
                    e.spawnX(), e.spawnY(), e.spawnZ()));
        }
        return mgr;
    }

    public static final SavedDataType<RealmManager> TYPE = new SavedDataType<>(
            DATA_KEY,
            RealmManager::new,
            CODEC,
            DataFixTypes.LEVEL
    );

    // -------------------------------------------------------------------------
    // Fields
    // -------------------------------------------------------------------------

    private int nextPlotIndex = 0;
    private final TreeSet<Integer>              freePlotIndices  = new TreeSet<>();
    private final Set<Integer>                  invalidPlots     = new HashSet<>();
    private final Map<UUID, RealmData>          realms           = new HashMap<>();
    private final Map<UUID, EntryLocation>      entryLocations   = new HashMap<>();
    private final Map<UUID, PlayerRealmInfo>    playerRealmInfos = new HashMap<>();

    // -------------------------------------------------------------------------
    // Access
    // -------------------------------------------------------------------------

    public static RealmManager get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(TYPE);
    }

    // -------------------------------------------------------------------------
    // Allocation
    // -------------------------------------------------------------------------

    public void allocateRealm(UUID ownerUUID) {
        int plotIndex = freePlotIndices.isEmpty() ? nextPlotIndex++ : freePlotIndices.pollFirst();
        RealmData data = new RealmData(plotIndex, ownerUUID);
        realms.put(ownerUUID, data);
        setDirty();
    }

    /** Set the created timestamp on a realm (call after allocation, when server is available). */
    public void setCreatedGameTime(UUID ownerUUID, long gameTime) {
        RealmData data = realms.get(ownerUUID);
        if (data != null) {
            data.createdGameTime = gameTime;
            setDirty();
        }
    }

    public long getCreatedGameTime(UUID ownerUUID) {
        RealmData data = realms.get(ownerUUID);
        return data != null ? data.createdGameTime : 0;
    }

    // -------------------------------------------------------------------------
    // Plot geometry
    // -------------------------------------------------------------------------

    /** Returns [originChunkX, originChunkZ] for the given plot index. */
    private int[] plotOriginChunkXZ(int plotIndex) {
        int cell = cellChunks();
        return new int[]{ (plotIndex % PLOTS_PER_ROW) * cell, (plotIndex / PLOTS_PER_ROW) * cell };
    }

    /** Returns [minBlockX, minBlockZ, maxBlockX, maxBlockZ] (exclusive max). */
    public int[] getRealmBounds(UUID ownerUUID) {
        RealmData data = realms.get(ownerUUID);
        if (data == null) return new int[]{0, 0, 0, 0};
        int[] origChunk = plotOriginChunkXZ(data.plotIndex);
        int minBlockX = origChunk[0] * 16;
        int minBlockZ = origChunk[1] * 16;
        int side = sideChunks() * 16;
        return new int[]{ minBlockX, minBlockZ, minBlockX + side, minBlockZ + side };
    }

    /** Whether the realm's owner or anyone on its access list is inside it right now (see RealmRules#defenderInside). */
    public boolean defenderInside(MinecraftServer server, UUID ownerUUID) {
        ServerLevel realm = server.getLevel(com.pocketdimensions.PocketDimensionsMod.REALM_DIM);
        if (realm == null) return false;
        List<UUID> inside = new java.util.ArrayList<>();
        for (ServerPlayer p : realm.players()) if (isWithinRealm(ownerUUID, p.getX(), p.getZ())) inside.add(p.getUUID());
        return RealmRules.defenderInside(ownerUUID, getAllowedPlayers(ownerUUID), inside);
    }

    /** The owner of the realm whose plot contains (x, z) in the realm dimension, or null between plots. */
    @Nullable
    public UUID realmOwnerAt(double x, double z) {
        for (UUID owner : realms.keySet()) if (isWithinRealm(owner, x, z)) return owner;
        return null;
    }

    /** Records the player as inside this owner's realm: its bounds hold them in, and a later arrival check finds them. */
    public void recordInRealm(UUID playerUUID, UUID ownerUUID) {
        int[] b = getRealmBounds(ownerUUID);                       // [minX, minZ, maxX, maxZ]
        setPlayerRealmInfo(playerUUID, ownerUUID, b[0], b[2], b[1], b[3], getSpawnPos(ownerUUID));
    }

    public boolean isWithinRealm(UUID ownerUUID, double x, double z) {
        int[] b = getRealmBounds(ownerUUID);
        return x >= b[0] && x < b[2] && z >= b[1] && z < b[3];
    }

    public BlockPos getSpawnPos(UUID ownerUUID) {
        RealmData data = realms.get(ownerUUID);
        if (data == null) return new BlockPos(1, REALM_BASE_Y, 0);
        if (data.worldCorePos != null) {
            return data.worldCorePos.east(); // worldCorePos + (1, 0, 0)
        }
        // Fallback: chunk-based center + 1
        int[] origChunk = plotOriginChunkXZ(data.plotIndex);
        int centerX = (origChunk[0] + sideChunks() / 2) * 16 + 8;
        int centerZ = (origChunk[1] + sideChunks() / 2) * 16 + 8;
        return new BlockPos(centerX + 1, REALM_BASE_Y, centerZ);
    }

    /**
     * Where to put someone entering the realm: next to the World Core (one block east of it) if that spot is free,
     * otherwise the nearest spot around the core with room for feet and head and ground underneath, so a slope or
     * something built there can't trap anyone. Never on the core itself, never outside the realm. If nothing near the
     * core is free, the usual spot is broken open (the blocks drop as items).
     */
    public BlockPos findSafeSpawn(UUID ownerUUID, ServerLevel level) {
        BlockPos spawn = getSpawnPos(ownerUUID);
        RealmData data = realms.get(ownerUUID);
        if (data == null || data.worldCorePos == null) return spawn;
        BlockPos core = data.worldCorePos;
        int[] b = getRealmBounds(ownerUUID);   // [minX, minZ, maxX, maxZ), max exclusive
        BlockPos min = new BlockPos(Math.max(b[0], core.getX() - 6), core.getY() - 4, Math.max(b[1], core.getZ() - 6));
        BlockPos max = new BlockPos(Math.min(b[2] - 1, core.getX() + 6), core.getY() + 6, Math.min(b[3] - 1, core.getZ() + 6));
        BlockPos found = SafeSpot.nearest(level, spawn, min, max, p -> p.getX() == core.getX() && p.getZ() == core.getZ());
        return found != null ? found : SafeSpot.breakOpen(level, spawn);
    }

    // -------------------------------------------------------------------------
    // Realm generation
    // -------------------------------------------------------------------------

    public void ensureGenerated(UUID ownerUUID, ServerLevel realmLevel) {
        RealmData data = realms.get(ownerUUID);
        if (data == null || data.generated) return;

        for (int attempt = 0; attempt < 100; attempt++) {
            // Skip any plot previously marked as having no dry land
            if (invalidPlots.contains(data.plotIndex)) {
                int nextIndex = freePlotIndices.isEmpty() ? nextPlotIndex++ : freePlotIndices.pollFirst();
                data = new RealmData(nextIndex, ownerUUID);
                realms.put(ownerUUID, data);
                setDirty();
            }

            int[] origChunk = plotOriginChunkXZ(data.plotIndex);
            int centerChunkX = origChunk[0] + sideChunks() / 2;
            int centerChunkZ = origChunk[1] + sideChunks() / 2;
            int searchRadius = Math.max(1, Math.min(radiusChunks() / 3, maxSearchChunks()));

            // Force-load all chunks in the search area
            for (int dx = -searchRadius; dx <= searchRadius; dx++) {
                for (int dz = -searchRadius; dz <= searchRadius; dz++) {
                    realmLevel.getChunk(centerChunkX + dx, centerChunkZ + dz);
                }
            }

            BlockPos landPos = findLandNearCenter(realmLevel, centerChunkX, centerChunkZ, searchRadius);

            if (landPos != null) {
                placeCore(realmLevel, landPos, ownerUUID);
                clearColumnAbove(realmLevel, landPos);
                data.worldCorePos = landPos;
                data.generated = true;
                setDirty();
                return;
            }

            // No dry land found - mark this plot invalid and try the next one
            invalidPlots.add(data.plotIndex);
            int nextIndex = freePlotIndices.isEmpty() ? nextPlotIndex++ : freePlotIndices.pollFirst();
            data = new RealmData(nextIndex, ownerUUID);
            realms.put(ownerUUID, data);
            setDirty();
        }

        // Extreme fallback (100 consecutive water-only plots): force-place at center surface
        int[] origChunk = plotOriginChunkXZ(data.plotIndex);
        int centerX = (origChunk[0] + sideChunks() / 2) * 16 + 8;
        int centerZ = (origChunk[1] + sideChunks() / 2) * 16 + 8;
        realmLevel.getChunk(centerX >> 4, centerZ >> 4);
        int surfaceY = realmLevel.getHeight(Heightmap.Types.WORLD_SURFACE, centerX, centerZ);
        BlockPos corePos = new BlockPos(centerX, surfaceY, centerZ);
        placeCore(realmLevel, corePos, ownerUUID);
        clearColumnAbove(realmLevel, corePos);
        data.worldCorePos = corePos;
        data.generated = true;
        setDirty();
    }

    /** A World Core at corePos for ownerUUID's realm: two blocks tall, its block entity on the lower half. */
    private void placeCore(ServerLevel level, BlockPos corePos, UUID ownerUUID) {
        level.setBlock(corePos, ModBlocks.WORLD_CORE.get().defaultBlockState(), 3);
        if (level.getBlockEntity(corePos) instanceof WorldCoreBlockEntity wc) wc.setOwnerUUID(ownerUUID);
        level.setBlock(corePos.above(), ModBlocks.WORLD_CORE.get().defaultBlockState()
                .setValue(WorldCoreBlock.HALF, DoubleBlockHalf.UPPER), 3);
    }

    /** Clears all blocks in the column directly above corePos up to the world surface. */

    private void clearColumnAbove(ServerLevel level, BlockPos corePos) {
        int topY = level.getHeight(Heightmap.Types.WORLD_SURFACE, corePos.getX(), corePos.getZ());
        for (int y = corePos.getY() + 1; y < topY; y++) {
            level.setBlock(new BlockPos(corePos.getX(), y, corePos.getZ()), Blocks.AIR.defaultBlockState(), 3);
        }
    }

    /**
     * Scans chunks within searchRadius of center, sampling 4x4 points per chunk.
     * Returns the closest BlockPos where the surface block is solid and fluid-free,
     * or null if no such position exists in the search area.
     * The returned Y is the surface height (first air above solid), suitable for WorldCore placement.
     */
    @Nullable
    private BlockPos findLandNearCenter(ServerLevel level, int centerChunkX, int centerChunkZ, int searchRadius) {
        int centerBlockX = centerChunkX * 16 + 8;
        int centerBlockZ = centerChunkZ * 16 + 8;
        BlockPos best = null;
        double bestDistSq = Double.MAX_VALUE;
        int[] offsets = {2, 6, 10, 14};

        for (int cx = centerChunkX - searchRadius; cx <= centerChunkX + searchRadius; cx++) {
            for (int cz = centerChunkZ - searchRadius; cz <= centerChunkZ + searchRadius; cz++) {
                int baseX = cx * 16;
                int baseZ = cz * 16;
                for (int ox : offsets) {
                    for (int oz : offsets) {
                        int x = baseX + ox;
                        int z = baseZ + oz;
                        int y = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
                        if (y > level.getMinY()) {
                            BlockPos surfaceBlock = new BlockPos(x, y - 1, z);
                            BlockState surfaceState = level.getBlockState(surfaceBlock);
                            if (surfaceState.getFluidState().isEmpty() && surfaceState.isSolid()) {
                                // If the surface is leaves or logs, scan downward for the forest floor
                                int placementY = y;
                                if (surfaceState.is(BlockTags.LEAVES) || surfaceState.is(BlockTags.LOGS)
                                        || surfaceState.is(BlockTags.REPLACEABLE)) {
                                    for (int scanY = y - 2; scanY >= level.getMinY(); scanY--) {
                                        BlockState scanState = level.getBlockState(new BlockPos(x, scanY, z));
                                        if (!scanState.is(BlockTags.LEAVES) && !scanState.is(BlockTags.LOGS)
                                                && !scanState.is(BlockTags.REPLACEABLE)) {
                                            placementY = scanY + 1;
                                            break;
                                        }
                                    }
                                }
                                double distSq = (x - centerBlockX) * (x - centerBlockX)
                                        + (z - centerBlockZ) * (z - centerBlockZ);
                                if (distSq < bestDistSq) {
                                    bestDistSq = distSq;
                                    best = new BlockPos(x, placementY, z);
                                }
                            }
                        }
                    }
                }
            }
        }

        return best;
    }

    // -------------------------------------------------------------------------
    // Anchor tracking
    // -------------------------------------------------------------------------

    public void setAnchorLocation(UUID ownerUUID, ResourceKey<Level> dim, BlockPos pos) {
        RealmData data = realms.get(ownerUUID);
        if (data == null) return;
        data.anchorDimKey = dim.identifier().toString();
        data.anchorPos = pos.immutable();
        setDirty();
    }

    public void clearAnchorLocation(UUID ownerUUID) {
        RealmData data = realms.get(ownerUUID);
        if (data == null) return;
        data.anchorDimKey = null;
        data.anchorPos = null;
        setDirty();
    }

    public Optional<Map.Entry<ResourceKey<Level>, BlockPos>> getAnchorLocation(UUID ownerUUID) {
        RealmData data = realms.get(ownerUUID);
        if (data == null || data.anchorDimKey == null || data.anchorPos == null) return Optional.empty();
        Identifier rl = Identifier.tryParse(data.anchorDimKey);
        if (rl == null) return Optional.empty();
        ResourceKey<Level> key = ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION, rl);
        return Optional.of(Map.entry(key, data.anchorPos));
    }

    // -------------------------------------------------------------------------
    // Entry location tracking
    // -------------------------------------------------------------------------

    public void setEntryLocation(UUID playerUUID, ResourceKey<Level> dimension,
                                 double x, double y, double z, float yaw, float pitch) {
        entryLocations.put(playerUUID, new EntryLocation(dimension, x, y, z, yaw, pitch));
        setDirty();
    }

    @Nullable
    public EntryLocation getEntryLocation(UUID playerUUID) {
        return entryLocations.get(playerUUID);
    }

    /**
     * Whether this player may be in this owner's realm right now (see {@link RealmRules#mayEnter}): the owner, the access
     * list, or anyone while a fuelled, completed World Breacher stands on the realm's anchor.
     */
    public boolean mayEnter(MinecraftServer server, UUID ownerUUID, UUID playerUUID) {
        return RealmRules.mayEnter(ownerUUID.equals(playerUUID), isAllowed(ownerUUID, playerUUID), breachOpen(server, ownerUUID));
    }

    private boolean breachOpen(MinecraftServer server, UUID ownerUUID) {
        var anchor = getAnchorLocation(ownerUUID);
        if (anchor.isEmpty()) return false;
        ServerLevel level = server.getLevel(anchor.get().getKey());
        if (level == null) return false;
        BlockPos siegePos = anchor.get().getValue().above(2);           // a siege block sits on the anchor's upper half
        return level.getBlockEntity(siegePos) instanceof com.pocketdimensions.blockentity.WorldBreacherBlockEntity b
                && b.isBreachComplete();
    }

    /** Sends the player back where they entered from (or to world spawn), and forgets which realm they were in. */
    public void teleportToEntryOrSpawn(ServerPlayer player, MinecraftServer server) {
        clearPlayerRealmInfo(player.getUUID());
        EntryLocation entry = entryLocations.get(player.getUUID());
        if (entry != null) {
            ServerLevel target = server.getLevel(entry.dimension);
            if (target != null) {
                player.teleport(new TeleportTransition(target,
                        new Vec3(entry.x, entry.y, entry.z), Vec3.ZERO,
                        entry.yaw, entry.pitch, TeleportTransition.DO_NOTHING));
                return;
            }
        }
        BlockPos spawn = server.getRespawnData().pos();
        player.teleport(new TeleportTransition(server.overworld(),
                new Vec3(spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5), Vec3.ZERO,
                0f, 0f, TeleportTransition.DO_NOTHING));
    }

    // -------------------------------------------------------------------------
    // Player realm info tracking (persisted)
    // -------------------------------------------------------------------------

    public void setPlayerRealmInfo(UUID playerUUID, UUID realmOwner,
                                   int minX, int maxX, int minZ, int maxZ, BlockPos spawn) {
        playerRealmInfos.put(playerUUID, new PlayerRealmInfo(realmOwner, minX, maxX, minZ, maxZ,
                spawn.getX(), spawn.getY(), spawn.getZ()));
        setDirty();
    }

    @Nullable
    public PlayerRealmInfo getPlayerRealmInfo(UUID playerUUID) {
        return playerRealmInfos.get(playerUUID);
    }

    public void clearPlayerRealmInfo(UUID playerUUID) {
        playerRealmInfos.remove(playerUUID);
        setDirty();
    }

    // -------------------------------------------------------------------------
    // Queries
    // -------------------------------------------------------------------------

    public boolean realmExistsFor(UUID ownerUUID) {
        return realms.containsKey(ownerUUID);
    }

    /**
     * Transfers realm ownership from oldOwner to newOwner.
     * Moves the RealmData entry (plot index, positions) to the new UUID key.
     * The caller is responsible for updating WorldAnchorBlockEntity and WorldCoreBlockEntity.
     */
    public void transferOwnership(UUID oldOwner, UUID newOwner) {
        RealmData old = realms.remove(oldOwner);
        if (old == null) return;
        RealmData fresh = new RealmData(old.plotIndex, newOwner);
        fresh.generated       = old.generated;
        fresh.anchorDimKey    = old.anchorDimKey;
        fresh.anchorPos       = old.anchorPos;
        fresh.worldCorePos    = old.worldCorePos;
        fresh.createdGameTime = old.createdGameTime;
        fresh.allowedPlayers.addAll(old.allowedPlayers);
        fresh.managers.addAll(old.managers);
        RealmRules.handOver(fresh.allowedPlayers, fresh.managers, oldOwner, newOwner);
        fresh.name            = old.name;
        realms.put(newOwner, fresh);
        setDirty();
    }

    // -------------------------------------------------------------------------
    // Allowlist
    // -------------------------------------------------------------------------

    public boolean addAllowedPlayer(UUID ownerUUID, UUID playerUUID) {
        RealmData data = realms.get(ownerUUID);
        if (data == null) return false;
        if (playerUUID.equals(ownerUUID)) return false;
        if (data.allowedPlayers.contains(playerUUID)) return false;
        int max = PocketDimensionsConfig.MAX_ALLOWED_PLAYERS.get();
        if (max > 0 && data.allowedPlayers.size() >= max) return false;
        data.allowedPlayers.add(playerUUID);
        setDirty();
        return true;
    }

    public boolean removeAllowedPlayer(UUID ownerUUID, UUID playerUUID) {
        RealmData data = realms.get(ownerUUID);
        if (data == null) return false;
        boolean removed = data.allowedPlayers.remove(playerUUID);
        data.managers.remove(playerUUID);            // leaving the list takes the crown with it
        if (removed) setDirty();
        return removed;
    }

    public boolean isAllowed(UUID ownerUUID, UUID playerUUID) {
        RealmData data = realms.get(ownerUUID);
        if (data == null) return false;
        return data.allowedPlayers.contains(playerUUID);
    }

    public List<UUID> getAllowedPlayers(UUID ownerUUID) {
        RealmData data = realms.get(ownerUUID);
        if (data == null) return List.of();
        return List.copyOf(data.allowedPlayers);
    }

    // -------------------------------------------------------------------------
    // Managers, roles and the realm's name
    // -------------------------------------------------------------------------

    /** What player can do at ownerUUID's World Core. */
    public RealmRules.Role roleOf(UUID ownerUUID, UUID player) {
        if (player.equals(ownerUUID)) return RealmRules.Role.OWNER;
        RealmData data = realms.get(ownerUUID);
        return data != null && data.managers.contains(player) ? RealmRules.Role.MANAGER : RealmRules.Role.VISITOR;
    }

    public boolean isManager(UUID ownerUUID, UUID player) {
        RealmData data = realms.get(ownerUUID);
        return data != null && data.managers.contains(player);
    }

    /** Crown an allowed player or take the crown away. Returns whether they are a manager now. */
    public boolean toggleManager(UUID ownerUUID, UUID player) {
        RealmData data = realms.get(ownerUUID);
        if (data == null || !data.allowedPlayers.contains(player)) return false;
        boolean now = !data.managers.remove(player);
        if (now) data.managers.add(player);
        setDirty();
        return now;
    }

    public String getName(UUID ownerUUID) {
        RealmData data = realms.get(ownerUUID);
        return data != null ? data.name : "";
    }

    /** Sets the realm's name; pass it through RealmRules.cleanName first ("" = unnamed). */
    public void setName(UUID ownerUUID, String name) {
        RealmData data = realms.get(ownerUUID);
        if (data == null) return;
        data.name = name;
        setDirty();
    }

    // -------------------------------------------------------------------------
    // Relocation
    // -------------------------------------------------------------------------

    /**
     * Grows ownerUUID's realm again somewhere new and gives up the old one for good. Everyone in the realm is sent back
     * to where they entered from, the old World Core is removed, the old plot is retired (never handed out again), and a
     * new plot is generated with a new core. The access list, managers and name are kept; the realm's age starts again.
     */
    public boolean relocate(UUID ownerUUID, ServerLevel realmLevel) {
        RealmData old = realms.get(ownerUUID);
        if (old == null) return false;
        MinecraftServer server = realmLevel.getServer();

        evacuate(ownerUUID, realmLevel);
        removeCore(old, realmLevel);

        invalidPlots.add(old.plotIndex);
        int index = freePlotIndices.isEmpty() ? nextPlotIndex++ : freePlotIndices.pollFirst();
        RealmData fresh = new RealmData(index, ownerUUID);
        fresh.anchorDimKey    = old.anchorDimKey;
        fresh.anchorPos       = old.anchorPos;
        fresh.createdGameTime = server.overworld().getGameTime();
        fresh.allowedPlayers.addAll(old.allowedPlayers);
        fresh.managers.addAll(old.managers);
        fresh.name            = old.name;
        realms.put(ownerUUID, fresh);
        setDirty();
        ensureGenerated(ownerUUID, realmLevel);
        return true;
    }

    /**
     * /pd disown: ownerUUID loses their realm. Everyone in it is sent back to where they entered from, its World Core
     * is removed, its anchor is unlinked (it stays, and takes a new World Seed), and the plot is retired for good. The
     * player may grow a new realm with a new seed.
     */
    public boolean disown(UUID ownerUUID, ServerLevel realmLevel) {
        RealmData old = realms.get(ownerUUID);
        if (old == null) return false;
        MinecraftServer server = realmLevel.getServer();

        evacuate(ownerUUID, realmLevel);
        removeCore(old, realmLevel);
        getAnchorLocation(ownerUUID).ifPresent(loc -> {
            ServerLevel anchorLevel = server.getLevel(loc.getKey());
            if (anchorLevel != null && anchorLevel.getBlockEntity(loc.getValue()) instanceof WorldAnchorBlockEntity a
                    && ownerUUID.equals(a.getOwnerUUID()))
                a.unlink();
        });

        invalidPlots.add(old.plotIndex);
        realms.remove(ownerUUID);
        setDirty();
        return true;
    }

    public enum CoreRegen { NO_REALM, STILL_STANDS, REBUILT }

    /**
     * /pd regenCore: puts ownerUUID's World Core back where it stood (for one broken by accident, in creative). A realm
     * that was never generated is generated now.
     */
    public CoreRegen regenerateCore(UUID ownerUUID, ServerLevel realmLevel) {
        RealmData data = realms.get(ownerUUID);
        if (data == null) return CoreRegen.NO_REALM;
        if (!data.generated || data.worldCorePos == null) {
            ensureGenerated(ownerUUID, realmLevel);
            return CoreRegen.REBUILT;
        }
        if (realmLevel.getBlockEntity(data.worldCorePos) instanceof WorldCoreBlockEntity wc
                && realmLevel.getBlockState(data.worldCorePos.above()).is(ModBlocks.WORLD_CORE.get())) {
            if (!ownerUUID.equals(wc.getOwnerUUID())) wc.setOwnerUUID(ownerUUID);
            return CoreRegen.STILL_STANDS;
        }
        placeCore(realmLevel, data.worldCorePos, ownerUUID);
        return CoreRegen.REBUILT;
    }

    /** Sends everyone in ownerUUID's realm back to where they entered from, now or when they next log in. */
    private void evacuate(UUID ownerUUID, ServerLevel realmLevel) {
        for (ServerPlayer p : new ArrayList<>(realmLevel.players())) {
            PlayerRealmInfo info = playerRealmInfos.get(p.getUUID());
            boolean here = (info != null && info.realmOwner.equals(ownerUUID)) || isWithinRealm(ownerUUID, p.getX(), p.getZ());
            if (here) com.pocketdimensions.event.RealmEventHandler.queueRealmExit(p.getUUID());
        }
        // anyone logged out inside the realm is sent home when they come back
        playerRealmInfos.values().removeIf(i -> i.realmOwner.equals(ownerUUID));
    }

    private void removeCore(RealmData data, ServerLevel realmLevel) {
        if (data.worldCorePos == null) return;
        realmLevel.setBlock(data.worldCorePos.above(), Blocks.AIR.defaultBlockState(), 2);
        realmLevel.setBlock(data.worldCorePos, Blocks.AIR.defaultBlockState(), 2);
    }

    @Nullable
    public BlockPos getWorldCorePos(UUID ownerUUID) {
        RealmData data = realms.get(ownerUUID);
        return data != null ? data.worldCorePos : null;
    }
}
