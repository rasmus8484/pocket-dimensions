package com.pocketdimensions.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.pocketdimensions.PocketDimensionsMod;
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
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.Collection;
import java.util.UUID;

/**
 * Registers all "/pd" sub-commands.
 *
 * Syntax:
 *   /pd owner <name>           — transfer realm ownership by player name
 *   /pd owner uuid:<uuid>      — transfer realm ownership by explicit UUID
 *
 * Name resolution order:
 *   1. Online players
 *   2. Op list (covers previously-joined opped players)
 *   3. Whitelist (covers previously-joined whitelisted players)
 *   If still not found, instruct the admin to use uuid: prefix.
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
        );
    }

    // -------------------------------------------------------------------------
    // /pd owner <name|uuid:UUID>
    // -------------------------------------------------------------------------

    private static int executeSetOwner(CommandContext<CommandSourceStack> ctx)
            throws CommandSyntaxException {
        CommandSourceStack src = ctx.getSource();
        MinecraftServer server = src.getServer();

        // Must be run by a player (ray-cast requires a physical viewpoint)
        ServerPlayer executor = src.getPlayerOrException();
        ServerLevel level = src.getLevel();

        // Ray-cast from executor's eyes — find the WorldAnchor they are looking at (up to 5 blocks)
        Vec3 eyePos = executor.getEyePosition();
        Vec3 lookEnd = eyePos.add(executor.getLookAngle().scale(5.0));
        BlockHitResult hit = level.clip(new ClipContext(
                eyePos, lookEnd,
                ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, executor));

        if (hit.getType() != HitResult.Type.BLOCK) {
            src.sendFailure(Component.literal("[PD] Not looking at a block."));
            return 0;
        }

        BlockPos targetPos = hit.getBlockPos();
        if (!(level.getBlockEntity(targetPos) instanceof WorldAnchorBlockEntity anchor)) {
            src.sendFailure(Component.literal("[PD] Not looking at a WorldAnchor."));
            return 0;
        }

        UUID oldOwner = anchor.getOwnerUUID();
        if (oldOwner == null) {
            src.sendFailure(Component.literal("[PD] This WorldAnchor has no owner (not linked to a realm)."));
            return 0;
        }

        // ── Resolve new owner UUID from the argument ──────────────────────────
        String arg = StringArgumentType.getString(ctx, "player");
        UUID newOwner;
        String newOwnerName;

        if (arg.startsWith("uuid:")) {
            // Explicit UUID — e.g. /pd owner uuid:550e8400-e29b-41d4-a716-446655440000
            String uuidStr = arg.substring(5);
            try {
                newOwner = UUID.fromString(uuidStr);
            } catch (IllegalArgumentException e) {
                src.sendFailure(Component.literal("[PD] Invalid UUID format: " + uuidStr
                        + " — expected xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx"));
                return 0;
            }
            // Try to look up a display name; fall back to the UUID string itself
            ServerPlayer online = server.getPlayerList().getPlayer(newOwner);
            newOwnerName = (online != null) ? online.getName().getString() : uuidStr;

        } else {
            // Name lookup: online → ops list → whitelist
            newOwner = resolveByName(server, arg);
            if (newOwner == null) {
                src.sendFailure(Component.literal(
                        "[PD] Player '" + arg + "' not found."
                        + " They must be online, opped, or whitelisted for name lookup."
                        + " Use 'uuid:<uuid>' to set by UUID directly."));
                return 0;
            }
            newOwnerName = arg;
        }

        if (oldOwner.equals(newOwner)) {
            src.sendFailure(Component.literal("[PD] " + newOwnerName + " already owns this anchor."));
            return 0;
        }

        // ── Transfer ownership across all three data holders ──────────────────
        RealmManager mgr = RealmManager.get(server);
        mgr.transferOwnership(oldOwner, newOwner);
        anchor.setOwnerUUID(newOwner);

        // Update WorldCoreBlockEntity inside the realm dimension (if realm was already generated)
        BlockPos corePos = mgr.getWorldCorePos(newOwner);
        if (corePos != null) {
            ServerLevel realmLevel = server.getLevel(PocketDimensionsMod.REALM_DIM);
            if (realmLevel != null && realmLevel.getBlockEntity(corePos) instanceof WorldCoreBlockEntity wc) {
                wc.setOwnerUUID(newOwner);
            }
        }

        String finalName = newOwnerName;
        src.sendSuccess(() -> Component.literal(
                "[PD] Realm ownership transferred to " + finalName + "."), true);
        return 1;
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Resolves a player name to a UUID.
     * Checks: online players → op list → whitelist.
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
        return findInEntries(server.getPlayerList().getWhiteList().getEntries(), name);
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
