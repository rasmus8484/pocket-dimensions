package com.pocketdimensions.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.pocketdimensions.PocketDimensionsConfig;
import com.pocketdimensions.PocketDimensionsMod;
import com.pocketdimensions.block.WorldAnchorBlock;
import com.pocketdimensions.block.WorldCoreBlock;
import com.pocketdimensions.blockentity.PocketAnchorBlockEntity;
import com.pocketdimensions.blockentity.WorldAnchorBlockEntity;
import com.pocketdimensions.blockentity.WorldCoreBlockEntity;
import com.pocketdimensions.manager.RealmManager;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permission;
import net.minecraft.server.permissions.PermissionLevel;
import net.minecraft.server.players.NameAndId;
import net.minecraft.server.players.StoredUserEntry;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.UsernameCache;

import java.util.Collection;
import java.util.UUID;

/**
 * Registers all "/pd" sub-commands.
 *
 * Syntax:
 *   /pd owner <name>                               - look at a World Anchor or World Core (the realm) or a Pocket
 *                                                    Anchor (the room) and give it to a player, resolved by name
 *   /pd owner 550e8400-e29b-41d4-a716-446655440000 - resolve by raw UUID
 *   /pd allow <name> [man]                         - look at a World Anchor or World Core: put a player on the
 *                                                    realm's access list (with man: also make them a manager)
 *   /pd deny <name> [man]                          - take a player off the access list (with man: only take away
 *                                                    their manager status; they stay on the list)
 *   /pd test mineAnchor [seconds]                  - testing: mine the anchor of the room you're in, start to finish
 *                                                    (default: pocket_anchor_mine_seconds from the server config)
 *
 * The argument is auto-detected: if it parses as a UUID it is used directly;
 * otherwise it is treated as a player name and resolved via:
 *   1. Online players
 *   2. Op list  (covers previously-joined opped players stored in ops.json)
 *   3. Whitelist (covers previously-joined whitelisted players)
 *   4. Forge's username cache (anyone who has ever joined)
 *
 * All sub-commands require OP level 2 (GAMEMASTERS).
 */
public class PocketDimensionsCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            Commands.literal("pd")
                .requires(src -> src.permissions().hasPermission(
                        new Permission.HasCommandLevel(PermissionLevel.GAMEMASTERS)))
                .then(Commands.literal("owner")
                    .then(Commands.argument("player", StringArgumentType.word())
                        .executes(PocketDimensionsCommand::executeSetOwner)))
                .then(Commands.literal("allow")
                    .then(Commands.argument("player", StringArgumentType.word())
                        .executes(ctx -> executeAccess(ctx, true, false))
                        .then(Commands.literal("man")
                            .executes(ctx -> executeAccess(ctx, true, true)))))
                .then(Commands.literal("deny")
                    .then(Commands.argument("player", StringArgumentType.word())
                        .executes(ctx -> executeAccess(ctx, false, false))
                        .then(Commands.literal("man")
                            .executes(ctx -> executeAccess(ctx, false, true)))))
                .then(Commands.literal("test")
                    .then(Commands.literal("mineAnchor")
                        .executes(ctx -> executeTestMineAnchor(ctx,
                                com.pocketdimensions.PocketDimensionsServerConfig.POCKET_ANCHOR_MINE_SECONDS.get()))
                        .then(Commands.argument("seconds", DoubleArgumentType.doubleArg(0, 3600))
                            .executes(ctx -> executeTestMineAnchor(ctx, DoubleArgumentType.getDouble(ctx, "seconds"))))))
        );
    }

    // -------------------------------------------------------------------------
    // /pd test mineAnchor [seconds]
    // -------------------------------------------------------------------------

    /**
     * An invisible miner works the placed anchor of the room you're standing in, at the configured mining speed, so the
     * warnings and cracks can be seen from inside without a second player. At 100 % it breaks: the room is gone.
     * {@code seconds} is how long the whole mining takes (the server config's mining time unless given).
     */
    private static int executeTestMineAnchor(CommandContext<CommandSourceStack> ctx, double seconds)
            throws CommandSyntaxException {
        CommandSourceStack src = ctx.getSource();
        ServerPlayer player = src.getPlayerOrException();
        MinecraftServer server = src.getServer();
        var mgr = com.pocketdimensions.manager.PocketRoomManager.get(server);
        UUID room = player.level().dimension().equals(PocketDimensionsMod.POCKET_DIM)
                ? mgr.findRoomForOccupant(player.getUUID()) : null;
        if (room == null) {
            src.sendFailure(Component.literal("Stand inside a pocket room to test its anchor."));
            return 0;
        }
        var anchor = mgr.getAnchorLocation(room).orElse(null);
        ServerLevel level = anchor == null ? null : server.getLevel(anchor.getKey());
        if (level == null || !com.pocketdimensions.event.AnchorMiningHandler.startTestMiner(
                player.getUUID(), level, anchor.getValue(), seconds)) {
            src.sendFailure(Component.literal("This room's anchor isn't placed anywhere (it is being carried)."));
            return 0;
        }
        BlockPos pos = anchor.getValue();
        src.sendSuccess(() -> Component.literal(String.format(
                "Test: mining this room's anchor at %d %d %d in %s. It breaks in %.1f s and the room is lost.",
                pos.getX(), pos.getY(), pos.getZ(), anchor.getKey().identifier(), seconds)), false);
        return 1;
    }

    // -------------------------------------------------------------------------
    // /pd owner <name|uuid:UUID>
    // -------------------------------------------------------------------------

    private static int executeSetOwner(CommandContext<CommandSourceStack> ctx)
            throws CommandSyntaxException {
        CommandSourceStack src = ctx.getSource();
        MinecraftServer server = src.getServer();

        BlockEntity target = lookedAt(src);
        if (target == null) return 0;
        if (target instanceof PocketAnchorBlockEntity pocketAnchor)
            return setPocketOwner(ctx, server, pocketAnchor);

        UUID oldOwner = realmOwnerOf(src, target);
        if (oldOwner == null) return 0;

        // -- Resolve new owner UUID from the argument --------------------------
        NewOwner resolved = resolveNewOwner(ctx, server);
        if (resolved == null) return 0;
        UUID newOwner = resolved.id();

        if (oldOwner.equals(newOwner)) {
            src.sendFailure(Component.literal(resolved.name() + " already holds dominion over this realm."));
            return 0;
        }

        // -- Transfer ownership across all three data holders ------------------
        RealmManager mgr = RealmManager.get(server);
        mgr.transferOwnership(oldOwner, newOwner);
        if (target instanceof WorldAnchorBlockEntity anchor) anchor.setOwnerUUID(newOwner);
        if (target instanceof WorldCoreBlockEntity core) core.setOwnerUUID(newOwner);

        // The other half of the pair: the anchor out in the world, the core inside the realm
        mgr.getAnchorLocation(newOwner).ifPresent(loc -> {
            ServerLevel anchorLevel = server.getLevel(loc.getKey());
            if (anchorLevel != null && anchorLevel.getBlockEntity(loc.getValue()) instanceof WorldAnchorBlockEntity a)
                a.setOwnerUUID(newOwner);
        });
        BlockPos corePos = mgr.getWorldCorePos(newOwner);
        if (corePos != null) {
            ServerLevel realmLevel = server.getLevel(PocketDimensionsMod.REALM_DIM);
            if (realmLevel != null && realmLevel.getBlockEntity(corePos) instanceof WorldCoreBlockEntity wc) {
                wc.setOwnerUUID(newOwner);
            }
        }

        src.sendSuccess(() -> Component.literal(
                "Dominion over the realm has passed to " + resolved.name() + "."), true);
        return 1;
    }

    // -------------------------------------------------------------------------
    // /pd allow <name> [man]  /  /pd deny <name> [man]
    // -------------------------------------------------------------------------

    /**
     * The realm of the World Anchor or World Core you're looking at. allow puts the player on its access list, deny
     * takes them off (and their manager status with it). With man, allow also makes them a manager and deny only takes
     * the manager status away.
     */
    private static int executeAccess(CommandContext<CommandSourceStack> ctx, boolean allow, boolean asManager)
            throws CommandSyntaxException {
        CommandSourceStack src = ctx.getSource();
        MinecraftServer server = src.getServer();
        BlockEntity target = lookedAt(src);
        if (target == null) return 0;
        if (target instanceof PocketAnchorBlockEntity) {
            src.sendFailure(Component.literal("A pocket room keeps no wards: anyone may enter it."));
            return 0;
        }
        UUID owner = realmOwnerOf(src, target);
        if (owner == null) return 0;
        NewOwner who = resolveNewOwner(ctx, server);
        if (who == null) return 0;
        if (who.id().equals(owner)) {
            src.sendFailure(Component.literal("The realm's owner needs no leave to enter, and cannot be kept out."));
            return 0;
        }

        RealmManager mgr = RealmManager.get(server);
        boolean listed = mgr.isAllowed(owner, who.id());
        boolean manager = mgr.isManager(owner, who.id());
        String message;
        if (allow) {
            if (listed && (!asManager || manager)) {
                src.sendFailure(Component.literal(who.name() + (asManager
                        ? " already keeps the realm."
                        : " already walks freely through the realm's wards.")));
                return 0;
            }
            if (!listed && !mgr.addAllowedPlayer(owner, who.id())) {
                src.sendFailure(Component.literal("The realm's wards can hold no more names. The limit of "
                        + PocketDimensionsConfig.MAX_ALLOWED_PLAYERS.get() + " has been reached."));
                return 0;
            }
            if (asManager) mgr.toggleManager(owner, who.id());
            message = asManager ? who.name() + " now keeps the realm."
                          : who.name() + " has been granted passage through the realm's wards.";
        } else {
            if (asManager ? !manager : !listed) {
                src.sendFailure(Component.literal(who.name() + (asManager
                        ? " does not keep the realm."
                        : " is not held in the realm's wards.")));
                return 0;
            }
            if (asManager) mgr.toggleManager(owner, who.id());
            else mgr.removeAllowedPlayer(owner, who.id());
            message = asManager ? who.name() + " no longer keeps the realm."
                          : who.name() + " has been cast from the realm's wards.";
        }
        src.sendSuccess(() -> Component.literal(message), true);
        return 1;
    }

    // -------------------------------------------------------------------------
    // What the executor is looking at
    // -------------------------------------------------------------------------

    /**
     * The block entity of the block the executor looks at (within 5 blocks). The World Anchor and World Core are two
     * blocks tall with the block entity on the lower half, so either half counts. Tells the executor and returns null
     * when there is nothing there.
     */
    private static BlockEntity lookedAt(CommandSourceStack src) throws CommandSyntaxException {
        ServerPlayer executor = src.getPlayerOrException();
        ServerLevel level = src.getLevel();
        Vec3 eyePos = executor.getEyePosition();
        Vec3 lookEnd = eyePos.add(executor.getLookAngle().scale(5.0));
        BlockHitResult hit = level.clip(new ClipContext(
                eyePos, lookEnd,
                ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, executor));
        if (hit.getType() != HitResult.Type.BLOCK) {
            src.sendFailure(Component.literal("You are not looking at a block."));
            return null;
        }
        BlockPos pos = hit.getBlockPos();
        BlockState state = level.getBlockState(pos);
        if ((state.getBlock() instanceof WorldAnchorBlock && state.getValue(WorldAnchorBlock.HALF) == DoubleBlockHalf.UPPER)
                || (state.getBlock() instanceof WorldCoreBlock && state.getValue(WorldCoreBlock.HALF) == DoubleBlockHalf.UPPER))
            pos = pos.below();
        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof WorldAnchorBlockEntity || be instanceof WorldCoreBlockEntity || be instanceof PocketAnchorBlockEntity)) {
            src.sendFailure(Component.literal("That is not a World Anchor, a World Core or a Pocket Anchor."));
            return null;
        }
        return be;
    }

    /** The owner of the realm a World Anchor or World Core belongs to; tells the executor and returns null if none. */
    private static UUID realmOwnerOf(CommandSourceStack src, BlockEntity be) {
        UUID owner = be instanceof WorldAnchorBlockEntity a ? a.getOwnerUUID()
                : be instanceof WorldCoreBlockEntity c ? c.getOwnerUUID() : null;
        if (owner == null || !RealmManager.get(src.getServer()).realmExistsFor(owner)) {
            src.sendFailure(Component.literal(be instanceof WorldAnchorBlockEntity
                    ? "This anchor has never been linked to a realm."
                    : "This core belongs to no realm."));
            return null;
        }
        return owner;
    }

    /**
     * A Pocket Anchor you're looking at: its room is recorded as the new owner's, and so is the placed anchor. (Rooms
     * stay open to anyone either way.)
     */
    private static int setPocketOwner(CommandContext<CommandSourceStack> ctx, MinecraftServer server,
                                      PocketAnchorBlockEntity anchor) {
        CommandSourceStack src = ctx.getSource();
        UUID room = anchor.getPocketId();
        var mgr = com.pocketdimensions.manager.PocketRoomManager.get(server);
        if (room == null || !mgr.roomExists(room)) {
            src.sendFailure(Component.literal("This Pocket Anchor holds no room."));
            return 0;
        }
        NewOwner resolved = resolveNewOwner(ctx, server);
        if (resolved == null) return 0;
        if (resolved.id().equals(mgr.getRoomOwner(room)) && resolved.id().equals(anchor.getOwnerUUID())) {
            src.sendFailure(Component.literal(resolved.name() + " already holds this pocket."));
            return 0;
        }
        mgr.setRoomOwner(room, resolved.id());
        anchor.setOwnerUUID(resolved.id());
        src.sendSuccess(() -> Component.literal("The pocket now answers to " + resolved.name() + "."), true);
        return 1;
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private record NewOwner(UUID id, String name) {}

    /**
     * The "player" argument as a UUID and a name: a UUID is taken as given, a name is looked up (online, ops,
     * whitelist). Tells the executor and returns null when no one matches.
     */
    private static NewOwner resolveNewOwner(CommandContext<CommandSourceStack> ctx, MinecraftServer server) {
        String arg = StringArgumentType.getString(ctx, "player");
        UUID parsedDirectly = tryParseUUID(arg);
        if (parsedDirectly != null) {
            ServerPlayer online = server.getPlayerList().getPlayer(parsedDirectly);
            return new NewOwner(parsedDirectly, online != null ? online.getName().getString() : arg);
        }
        UUID byName = resolveByName(server, arg);
        if (byName == null) {
            ctx.getSource().sendFailure(Component.literal(
                    "No soul by the name '" + arg + "' could be found."
                    + " They must have joined this world before, or you may pass their UUID"
                    + " (xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx)."));
            return null;
        }
        return new NewOwner(byName, arg);
    }

    /**
     * Resolves a player name to a UUID.
     * Checks: online players -> op list -> whitelist -> username cache.
     * Returns null if the name cannot be found in any of those sources.
     */
    private static UUID resolveByName(MinecraftServer server, String name) {
        // 1. Online players
        ServerPlayer online = server.getPlayerList().getPlayerByName(name);
        if (online != null) return online.getUUID();

        // 2. Op list (covers previously-joined opped offline players)
        UUID fromOps = findInEntries(server.getPlayerList().getOps().getEntries(), name);
        if (fromOps != null) return fromOps;

        // 3. Whitelist (covers previously-joined whitelisted offline players)
        UUID fromWhitelist = findInEntries(server.getPlayerList().getWhiteList().getEntries(), name);
        if (fromWhitelist != null) return fromWhitelist;

        // 4. Forge's username cache (anyone who has ever joined)
        for (var e : UsernameCache.getMap().entrySet())
            if (e.getValue().equalsIgnoreCase(name)) return e.getKey();
        return null;
    }

    /** Returns the UUID if {@code s} is a valid UUID string, otherwise null. */
    private static UUID tryParseUUID(String s) {
        try { return UUID.fromString(s); } catch (IllegalArgumentException e) { return null; }
    }

    /** Scans a StoredUserList entry collection for a case-insensitive name match. */
    private static UUID findInEntries(
            Collection<? extends StoredUserEntry<NameAndId>> entries, String name) {
        for (StoredUserEntry<NameAndId> entry : entries) {
            NameAndId nai = entry.getUser();
            if (nai.name().equalsIgnoreCase(name)) return nai.id();
        }
        return null;
    }
}
