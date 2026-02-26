package com.pocketdimensions.manager;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.pocketdimensions.blockentity.WorldCoreBlockEntity;
import com.pocketdimensions.init.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
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
 * Plot geometry (constants, easy to scale):
 *   REALM_STRIDE     = 64    distance between plot origins
 *   REALM_SIZE       = 48    playable XZ area (3x3 chunks)
 *   REALM_OFFSET_XZ  = 8     buffer from plot edge to realm edge
 *   Plot origin: ((plotIndex % PLOTS_PER_ROW) * 64, 0, (plotIndex / PLOTS_PER_ROW) * 64)
 *   Realm area:  plotOrigin + (8, 0, 8) to plotOrigin + (56, *, 56)
 *   WorldCore:   plotOrigin + (32, surfaceY, 32)   (center of the 48x48 area)
 *   Spawn pos:   WorldCore + (1, 0, 0)
 */
public class RealmManager extends SavedData {

    // -------------------------------------------------------------------------
    // Constants
    // -------------------------------------------------------------------------

    private static final String DATA_KEY      = "realm_manager";
    private static final int REALM_STRIDE     = 64;
    private static final int REALM_SIZE       = 48;
    private static final int REALM_OFFSET_XZ  = 8;
    private static final int PLOTS_PER_ROW    = 10000;
    private static final int REALM_BASE_Y     = 64;

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

    // -------------------------------------------------------------------------
    // Codec helpers
    // -------------------------------------------------------------------------

    private record RealmEntry(UUID ownerUUID, int plotIndex, boolean generated,
                              Optional<String> anchorDimKey,
                              Optional<Long> anchorPosLong,
                              Optional<Long> worldCorePosLong) {

        static final Codec<RealmEntry> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                UUIDUtil.CODEC.fieldOf("ownerUUID").forGetter(RealmEntry::ownerUUID),
                Codec.INT.fieldOf("plotIndex").forGetter(RealmEntry::plotIndex),
                Codec.BOOL.optionalFieldOf("generated", false).forGetter(RealmEntry::generated),
                Codec.STRING.optionalFieldOf("anchorDimKey").forGetter(RealmEntry::anchorDimKey),
                Codec.LONG.optionalFieldOf("anchorPosLong").forGetter(RealmEntry::anchorPosLong),
                Codec.LONG.optionalFieldOf("worldCorePosLong").forGetter(RealmEntry::worldCorePosLong)
        ).apply(instance, RealmEntry::new));

        static RealmEntry from(UUID ownerUUID, RealmData data) {
            return new RealmEntry(
                    ownerUUID, data.plotIndex, data.generated,
                    Optional.ofNullable(data.anchorDimKey),
                    Optional.ofNullable(data.anchorPos).map(BlockPos::asLong),
                    Optional.ofNullable(data.worldCorePos).map(BlockPos::asLong));
        }

        RealmData toData() {
            RealmData d = new RealmData(plotIndex, ownerUUID);
            d.generated    = generated;
            d.anchorDimKey = anchorDimKey.orElse(null);
            d.anchorPos    = anchorPosLong.map(BlockPos::of).orElse(null);
            d.worldCorePos = worldCorePosLong.map(BlockPos::of).orElse(null);
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
                            .collect(Collectors.toList()))
    ).apply(instance, RealmManager::fromCodecData));

    private static RealmManager fromCodecData(int nextPlotIndex,
                                              List<Integer> freePlotIndexList,
                                              List<RealmEntry> realmEntries,
                                              List<EntryLocEntry> entryLocEntries) {
        RealmManager mgr = new RealmManager();
        mgr.nextPlotIndex = nextPlotIndex;
        mgr.freePlotIndices.addAll(freePlotIndexList);
        for (RealmEntry e : realmEntries) mgr.realms.put(e.ownerUUID(), e.toData());
        for (EntryLocEntry e : entryLocEntries) {
            Identifier rl = Identifier.tryParse(e.dimKey());
            if (rl == null) continue;
            ResourceKey<Level> dim = ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION, rl);
            mgr.entryLocations.put(e.playerUUID(),
                    new EntryLocation(dim, e.x(), e.y(), e.z(), e.yaw(), e.pitch()));
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
    private final TreeSet<Integer>         freePlotIndices = new TreeSet<>();
    private final Map<UUID, RealmData>     realms          = new HashMap<>();
    private final Map<UUID, EntryLocation> entryLocations  = new HashMap<>();
    /** Transient — NOT persisted. playerUUID → realmOwnerUUID */
    private final Map<UUID, UUID>          playerRealms    = new HashMap<>();

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
        realms.put(ownerUUID, new RealmData(plotIndex, ownerUUID));
        setDirty();
    }

    // -------------------------------------------------------------------------
    // Plot geometry
    // -------------------------------------------------------------------------

    private int[] plotOriginXZ(int plotIndex) {
        int px = plotIndex % PLOTS_PER_ROW;
        int pz = plotIndex / PLOTS_PER_ROW;
        return new int[]{ px * REALM_STRIDE, pz * REALM_STRIDE };
    }

    public int[] getRealmBounds(UUID ownerUUID) {
        RealmData data = realms.get(ownerUUID);
        if (data == null) return new int[]{0, 0, 0, 0};
        int[] orig = plotOriginXZ(data.plotIndex);
        int minX = orig[0] + REALM_OFFSET_XZ;
        int minZ = orig[1] + REALM_OFFSET_XZ;
        return new int[]{ minX, minZ, minX + REALM_SIZE, minZ + REALM_SIZE };
    }

    public boolean isWithinRealm(UUID ownerUUID, double x, double z) {
        int[] b = getRealmBounds(ownerUUID);
        return x >= b[0] && x < b[2] && z >= b[1] && z < b[3];
    }

    @Nullable
    public UUID findRealmAtPosition(double x, double z) {
        for (UUID ownerUUID : realms.keySet()) {
            if (isWithinRealm(ownerUUID, x, z)) return ownerUUID;
        }
        return null;
    }

    public BlockPos getSpawnPos(UUID ownerUUID) {
        RealmData data = realms.get(ownerUUID);
        if (data == null) return new BlockPos(1, REALM_BASE_Y, 0);
        if (data.worldCorePos != null) {
            return data.worldCorePos.east(); // worldCorePos + (1, 0, 0)
        }
        // Fallback: computed center + 1
        int[] orig = plotOriginXZ(data.plotIndex);
        int centerX = orig[0] + REALM_OFFSET_XZ + REALM_SIZE / 2;
        int centerZ = orig[1] + REALM_OFFSET_XZ + REALM_SIZE / 2;
        return new BlockPos(centerX + 1, REALM_BASE_Y, centerZ);
    }

    // -------------------------------------------------------------------------
    // Realm generation
    // -------------------------------------------------------------------------

    public void ensureGenerated(UUID ownerUUID, ServerLevel realmLevel) {
        RealmData data = realms.get(ownerUUID);
        if (data == null || data.generated) return;

        int[] orig = plotOriginXZ(data.plotIndex);
        int centerX = orig[0] + REALM_OFFSET_XZ + REALM_SIZE / 2;
        int centerZ = orig[1] + REALM_OFFSET_XZ + REALM_SIZE / 2;

        // Force-load the 3×3 chunks around the realm center
        int cx = centerX >> 4;
        int cz = centerZ >> 4;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                realmLevel.getChunk(cx + dx, cz + dz);
            }
        }

        // Find surface Y and place WorldCore on top of terrain
        int surfaceY = realmLevel.getHeight(Heightmap.Types.WORLD_SURFACE, centerX, centerZ);
        BlockPos corePos = new BlockPos(centerX, surfaceY, centerZ);
        realmLevel.setBlock(corePos, ModBlocks.WORLD_CORE.get().defaultBlockState(), 3);

        if (realmLevel.getBlockEntity(corePos) instanceof WorldCoreBlockEntity wc) {
            wc.setOwnerUUID(ownerUUID);
        }

        data.worldCorePos = corePos;
        data.generated = true;
        setDirty();
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

    public void teleportToEntryOrSpawn(ServerPlayer player, MinecraftServer server) {
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
    // Player→realm transient tracking (not persisted)
    // -------------------------------------------------------------------------

    public void setPlayerRealm(UUID playerUUID, UUID realmOwnerUUID) {
        playerRealms.put(playerUUID, realmOwnerUUID);
    }

    @Nullable
    public UUID getPlayerRealm(UUID playerUUID) {
        return playerRealms.get(playerUUID);
    }

    public void clearPlayerRealm(UUID playerUUID) {
        playerRealms.remove(playerUUID);
    }

    // -------------------------------------------------------------------------
    // Queries
    // -------------------------------------------------------------------------

    public boolean realmExistsFor(UUID ownerUUID) {
        return realms.containsKey(ownerUUID);
    }
}
