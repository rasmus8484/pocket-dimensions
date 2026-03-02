package com.pocketdimensions.manager;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.pocketdimensions.PocketDimensionsMod;
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
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Server-side SavedData tracking all pocket room allocations, occupants, and entry locations.
 * Stored in overworld data storage under "pocket_room_manager".
 */
public class PocketRoomManager extends SavedData {

    // -------------------------------------------------------------------------
    // Constants
    // -------------------------------------------------------------------------

    private static final String DATA_KEY = "pocket_room_manager";
    private static final int PLOT_BASE_Y  = 64;
    private static final int PLOT_STRIDE  = 48;
    private static final int PLOTS_PER_ROW = 10000;
    private static final int SHELL_SIZE   = 20;
    private static final int AIR_SIZE     = 16;
    private static final int SHELL_OFFSET_XZ = 14;
    private static final int SHELL_OFFSET_Y  = 0;
    private static final int SPAWN_OFFSET_XZ = 24;
    private static final int SPAWN_OFFSET_Y  = 2; // relative to PLOT_BASE_Y + SHELL_OFFSET_Y + 2

    // -------------------------------------------------------------------------
    // Inner data classes
    // -------------------------------------------------------------------------

    public static class RoomData {
        public final int plotIndex;
        public final UUID ownerUUID;
        public boolean generated = false;
        public @Nullable String anchorDimKey = null;
        public @Nullable BlockPos anchorPos = null;

        public RoomData(int plotIndex, UUID ownerUUID) {
            this.plotIndex = plotIndex;
            this.ownerUUID = ownerUUID;
        }
    }

    public static class EntryLocation {
        public final ResourceKey<Level> dimension;
        public final double x, y, z;
        public final float yaw, pitch;

        public EntryLocation(ResourceKey<Level> dimension, double x, double y, double z, float yaw, float pitch) {
            this.dimension = dimension;
            this.x = x; this.y = y; this.z = z;
            this.yaw = yaw; this.pitch = pitch;
        }
    }

    // -------------------------------------------------------------------------
    // Codec helpers - intermediate records for serialization
    // -------------------------------------------------------------------------

    private record RoomEntry(UUID pocketId, int plotIndex, UUID ownerUUID,
                             boolean generated,
                             Optional<String> anchorDimKey,
                             Optional<Long> anchorPosLong) {

        static final Codec<RoomEntry> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                UUIDUtil.CODEC.fieldOf("pocketId").forGetter(RoomEntry::pocketId),
                Codec.INT.fieldOf("plotIndex").forGetter(RoomEntry::plotIndex),
                UUIDUtil.CODEC.fieldOf("ownerUUID").forGetter(RoomEntry::ownerUUID),
                Codec.BOOL.optionalFieldOf("generated", false).forGetter(RoomEntry::generated),
                Codec.STRING.optionalFieldOf("anchorDimKey").forGetter(RoomEntry::anchorDimKey),
                Codec.LONG.optionalFieldOf("anchorPosLong").forGetter(RoomEntry::anchorPosLong)
        ).apply(instance, RoomEntry::new));

        static RoomEntry from(UUID pocketId, RoomData data) {
            return new RoomEntry(
                    pocketId, data.plotIndex, data.ownerUUID, data.generated,
                    Optional.ofNullable(data.anchorDimKey),
                    Optional.ofNullable(data.anchorPos).map(BlockPos::asLong)
            );
        }

        RoomData toData() {
            RoomData d = new RoomData(plotIndex, ownerUUID);
            d.generated = generated;
            d.anchorDimKey = anchorDimKey.orElse(null);
            d.anchorPos = anchorPosLong.map(BlockPos::of).orElse(null);
            return d;
        }
    }

    private record OccupantEntry(UUID pocketId, List<UUID> players) {
        static final Codec<OccupantEntry> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                UUIDUtil.CODEC.fieldOf("pocketId").forGetter(OccupantEntry::pocketId),
                UUIDUtil.CODEC.listOf().fieldOf("players").forGetter(OccupantEntry::players)
        ).apply(instance, OccupantEntry::new));
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

    private static final Codec<PocketRoomManager> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.optionalFieldOf("nextPlotIndex", 0).forGetter(m -> m.nextPlotIndex),
            Codec.INT.listOf().optionalFieldOf("freePlotIndices", List.of()).forGetter(m ->
                    new ArrayList<>(m.freePlotIndices)),
            RoomEntry.CODEC.listOf().optionalFieldOf("rooms", List.of()).forGetter(m ->
                    m.rooms.entrySet().stream()
                            .map(e -> RoomEntry.from(e.getKey(), e.getValue()))
                            .collect(Collectors.toList())),
            OccupantEntry.CODEC.listOf().optionalFieldOf("occupants", List.of()).forGetter(m ->
                    m.occupants.entrySet().stream()
                            .map(e -> new OccupantEntry(e.getKey(), new ArrayList<>(e.getValue())))
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
    ).apply(instance, PocketRoomManager::fromCodecData));

    private static PocketRoomManager fromCodecData(int nextPlotIndex,
                                                    List<Integer> freePlotIndexList,
                                                    List<RoomEntry> roomEntries,
                                                    List<OccupantEntry> occupantEntries,
                                                    List<EntryLocEntry> entryLocEntries) {
        PocketRoomManager mgr = new PocketRoomManager();
        mgr.nextPlotIndex = nextPlotIndex;
        mgr.freePlotIndices.addAll(freePlotIndexList);
        for (RoomEntry e : roomEntries) mgr.rooms.put(e.pocketId(), e.toData());
        for (OccupantEntry e : occupantEntries) {
            if (!e.players().isEmpty())
                mgr.occupants.put(e.pocketId(), new HashSet<>(e.players()));
        }
        for (EntryLocEntry e : entryLocEntries) {
            Identifier rl = Identifier.tryParse(e.dimKey());
            if (rl == null) continue;
            ResourceKey<Level> dim = ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION, rl);
            mgr.entryLocations.put(e.playerUUID(),
                    new EntryLocation(dim, e.x(), e.y(), e.z(), e.yaw(), e.pitch()));
        }
        return mgr;
    }

    public static final SavedDataType<PocketRoomManager> TYPE = new SavedDataType<>(
            DATA_KEY,
            PocketRoomManager::new,
            CODEC,
            DataFixTypes.LEVEL
    );

    // -------------------------------------------------------------------------
    // Fields
    // -------------------------------------------------------------------------

    private int nextPlotIndex = 0;
    private final TreeSet<Integer>         freePlotIndices = new TreeSet<>();
    private final Map<UUID, RoomData>      rooms           = new HashMap<>();
    private final Map<UUID, Set<UUID>>     occupants       = new HashMap<>();
    private final Map<UUID, EntryLocation> entryLocations  = new HashMap<>();

    // -------------------------------------------------------------------------
    // Access
    // -------------------------------------------------------------------------

    public static PocketRoomManager get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(TYPE);
    }

    // -------------------------------------------------------------------------
    // Allocation
    // -------------------------------------------------------------------------

    public UUID allocateRoom(UUID ownerUUID) {
        UUID pocketId = UUID.randomUUID();
        int plotIndex = freePlotIndices.isEmpty() ? nextPlotIndex++ : freePlotIndices.pollFirst();
        rooms.put(pocketId, new RoomData(plotIndex, ownerUUID));
        setDirty();
        return pocketId;
    }

    // -------------------------------------------------------------------------
    // Plot geometry
    // -------------------------------------------------------------------------

    private BlockPos plotOrigin(int plotIndex) {
        int px = plotIndex % PLOTS_PER_ROW;
        int pz = plotIndex / PLOTS_PER_ROW;
        return new BlockPos(px * PLOT_STRIDE, PLOT_BASE_Y, pz * PLOT_STRIDE);
    }

    public BlockPos getSpawnPos(UUID pocketId) {
        RoomData data = rooms.get(pocketId);
        if (data == null) return new BlockPos(0, PLOT_BASE_Y + SHELL_OFFSET_Y + SPAWN_OFFSET_Y, 0);
        BlockPos origin = plotOrigin(data.plotIndex);
        return new BlockPos(
                origin.getX() + SPAWN_OFFSET_XZ,
                origin.getY() + SHELL_OFFSET_Y + SPAWN_OFFSET_Y,
                origin.getZ() + SPAWN_OFFSET_XZ
        );
    }

    // -------------------------------------------------------------------------
    // Room generation
    // -------------------------------------------------------------------------

    public void ensureGenerated(UUID pocketId, ServerLevel pocketLevel) {
        RoomData data = rooms.get(pocketId);
        if (data == null || data.generated) return;

        BlockPos origin = plotOrigin(data.plotIndex);
        int ox = origin.getX() + SHELL_OFFSET_XZ;
        int oy = origin.getY() + SHELL_OFFSET_Y;
        int oz = origin.getZ() + SHELL_OFFSET_XZ;

        // Force-load chunks covering the plot
        int baseChunkX = ox >> 4;
        int baseChunkZ = oz >> 4;
        for (int cx = baseChunkX - 1; cx <= baseChunkX + 3; cx++) {
            for (int cz = baseChunkZ - 1; cz <= baseChunkZ + 3; cz++) {
                pocketLevel.getChunk(cx, cz);
            }
        }

        var boundary = ModBlocks.BOUNDARY_BLOCK.get().defaultBlockState();
        var air      = net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();
        var mutable  = new BlockPos.MutableBlockPos();

        // Fill outer 20x20x20 with BoundaryBlock
        for (int x = 0; x < SHELL_SIZE; x++) {
            for (int y = 0; y < SHELL_SIZE; y++) {
                for (int z = 0; z < SHELL_SIZE; z++) {
                    pocketLevel.setBlock(mutable.set(ox + x, oy + y, oz + z), boundary, 3);
                }
            }
        }

        // Carve 16x16x16 air interior (2 blocks in from each face)
        for (int x = 0; x < AIR_SIZE; x++) {
            for (int y = 0; y < AIR_SIZE; y++) {
                for (int z = 0; z < AIR_SIZE; z++) {
                    pocketLevel.setBlock(mutable.set(ox + 2 + x, oy + 2 + y, oz + 2 + z), air, 3);
                }
            }
        }

        data.generated = true;
        setDirty();
    }

    // -------------------------------------------------------------------------
    // Anchor tracking
    // -------------------------------------------------------------------------

    public void setAnchorLocation(UUID pocketId, ResourceKey<Level> dim, BlockPos pos) {
        RoomData data = rooms.get(pocketId);
        if (data == null) return;
        data.anchorDimKey = dim.identifier().toString();
        data.anchorPos = pos.immutable();
        setDirty();
    }

    public void clearAnchorLocation(UUID pocketId) {
        RoomData data = rooms.get(pocketId);
        if (data == null) return;
        data.anchorDimKey = null;
        data.anchorPos = null;
        setDirty();
    }

    public Optional<Map.Entry<ResourceKey<Level>, BlockPos>> getAnchorLocation(UUID pocketId) {
        RoomData data = rooms.get(pocketId);
        if (data == null || data.anchorDimKey == null || data.anchorPos == null) return Optional.empty();
        Identifier rl = Identifier.tryParse(data.anchorDimKey);
        if (rl == null) return Optional.empty();
        ResourceKey<Level> key = ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION, rl);
        return Optional.of(Map.entry(key, data.anchorPos));
    }

    // -------------------------------------------------------------------------
    // Occupant tracking
    // -------------------------------------------------------------------------

    public void addOccupant(UUID pocketId, UUID playerUUID) {
        occupants.computeIfAbsent(pocketId, k -> new HashSet<>()).add(playerUUID);
        setDirty();
    }

    public void removeOccupant(UUID pocketId, UUID playerUUID) {
        Set<UUID> set = occupants.get(pocketId);
        if (set != null) {
            set.remove(playerUUID);
            if (set.isEmpty()) occupants.remove(pocketId);
        }
        setDirty();
    }

    public Set<UUID> getOccupants(UUID pocketId) {
        return occupants.getOrDefault(pocketId, Collections.emptySet());
    }

    @Nullable
    public UUID findRoomForOccupant(UUID playerUUID) {
        for (Map.Entry<UUID, Set<UUID>> entry : occupants.entrySet()) {
            if (entry.getValue().contains(playerUUID)) return entry.getKey();
        }
        return null;
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

    public void clearEntryLocation(UUID playerUUID) {
        entryLocations.remove(playerUUID);
        setDirty();
    }

    // -------------------------------------------------------------------------
    // Room destruction
    // -------------------------------------------------------------------------

    public void destroyRoom(UUID pocketId, MinecraftServer server) {
        RoomData data = rooms.get(pocketId);

        Set<UUID> toEject = new HashSet<>(getOccupants(pocketId));
        for (UUID playerUUID : toEject) {
            ServerPlayer player = server.getPlayerList().getPlayer(playerUUID);
            if (player != null) teleportToEntryOrSpawn(player, server);
            removeOccupant(pocketId, playerUUID);
        }
        clearAnchorLocation(pocketId);

        if (data != null) {
            if (data.generated) {
                ServerLevel pocketLevel = server.getLevel(PocketDimensionsMod.POCKET_DIM);
                if (pocketLevel != null) clearRoomBlocks(data.plotIndex, pocketLevel);
            }
            freePlotIndices.add(data.plotIndex);
        }

        rooms.remove(pocketId);
        setDirty();
    }

    private void clearRoomBlocks(int plotIndex, ServerLevel pocketLevel) {
        BlockPos origin = plotOrigin(plotIndex);
        int ox = origin.getX() + SHELL_OFFSET_XZ;
        int oy = origin.getY() + SHELL_OFFSET_Y;
        int oz = origin.getZ() + SHELL_OFFSET_XZ;

        // Force-load chunks covering the shell
        int baseChunkX = ox >> 4;
        int baseChunkZ = oz >> 4;
        for (int cx = baseChunkX - 1; cx <= baseChunkX + 3; cx++) {
            for (int cz = baseChunkZ - 1; cz <= baseChunkZ + 3; cz++) {
                pocketLevel.getChunk(cx, cz);
            }
        }

        var air     = net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();
        var mutable = new BlockPos.MutableBlockPos();

        for (int x = 0; x < SHELL_SIZE; x++) {
            for (int y = 0; y < SHELL_SIZE; y++) {
                for (int z = 0; z < SHELL_SIZE; z++) {
                    pocketLevel.setBlock(mutable.set(ox + x, oy + y, oz + z), air, 3);
                }
            }
        }
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

    public boolean roomExists(UUID pocketId) {
        return rooms.containsKey(pocketId);
    }
}
