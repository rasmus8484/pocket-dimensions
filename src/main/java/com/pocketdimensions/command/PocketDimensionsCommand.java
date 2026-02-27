package com.pocketdimensions.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.pocketdimensions.PocketDimensionsMod;
import com.pocketdimensions.blockentity.WorldAnchorBlockEntity;
import com.pocketdimensions.blockentity.WorldCoreBlockEntity;
import com.pocketdimensions.manager.RealmManager;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permission;
import net.minecraft.server.permissions.PermissionLevel;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

/**
 * Registers all "pd" sub-commands.
 *
 * Syntax:
 *   /pd owner <player>  — transfer realm ownership of the WorldAnchor you are looking at
 *
 * All sub-commands require OP level 2.
 */
public class PocketDimensionsCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            Commands.literal("pd")
                .requires(src -> src.permissions().hasPermission(
                        new Permission.HasCommandLevel(PermissionLevel.GAMEMASTERS)))
                .then(Commands.literal("owner")
                    .then(Commands.argument("player", EntityArgument.player())
                        .executes(PocketDimensionsCommand::executeSetOwner)))
        );
    }

    // -------------------------------------------------------------------------
    // /pd owner <player>
    // -------------------------------------------------------------------------

    private static int executeSetOwner(CommandContext<CommandSourceStack> ctx)
            throws CommandSyntaxException {
        CommandSourceStack src = ctx.getSource();
        MinecraftServer server = src.getServer();

        // Must be run by a player (ray-cast requires a physical viewpoint)
        ServerPlayer executor = src.getPlayerOrException();
        ServerLevel level = src.getLevel();

        // Ray-cast from executor's eyes — find the block they are looking at (up to 5 blocks)
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

        // Resolve the target player
        ServerPlayer targetPlayer = EntityArgument.getPlayer(ctx, "player");
        UUID newOwner = targetPlayer.getUUID();
        String newOwnerName = targetPlayer.getName().getString();

        if (oldOwner.equals(newOwner)) {
            src.sendFailure(Component.literal("[PD] " + newOwnerName + " already owns this anchor."));
            return 0;
        }

        // Transfer realm data in RealmManager
        RealmManager mgr = RealmManager.get(server);
        mgr.transferOwnership(oldOwner, newOwner);

        // Update the WorldAnchorBlockEntity
        anchor.setOwnerUUID(newOwner);

        // Update WorldCoreBlockEntity in the realm dimension (if the realm was already generated)
        BlockPos corePos = mgr.getWorldCorePos(newOwner);
        if (corePos != null) {
            ServerLevel realmLevel = server.getLevel(PocketDimensionsMod.REALM_DIM);
            if (realmLevel != null && realmLevel.getBlockEntity(corePos) instanceof WorldCoreBlockEntity wc) {
                wc.setOwnerUUID(newOwner);
            }
        }

        src.sendSuccess(() -> Component.literal(
                "[PD] Realm ownership transferred to " + newOwnerName + "."), true);
        return 1;
    }
}
