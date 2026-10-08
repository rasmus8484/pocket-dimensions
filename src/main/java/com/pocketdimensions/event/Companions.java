package com.pocketdimensions.event;

import com.pocketdimensions.manager.TravelRules;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Leashable;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Moves a player into or out of a pocket room or a realm, and on a trip they chose brings what comes along
 * (TravelRules): the mount they ride with everything aboard except other players, and the animals on their lead.
 * A forced trip moves only the player, who is set down from any mount, as vanilla does.
 */
public final class Companions {

    private Companions() {}

    public static void travel(ServerPlayer player, ServerLevel target, Vec3 pos, float yRot, float xRot,
                              TravelRules.Journey journey) {
        TeleportTransition there = new TeleportTransition(target, pos, Vec3.ZERO, yRot, xRot, TeleportTransition.DO_NOTHING);
        if (!TravelRules.bringsCompany(journey)) {
            player.teleport(there);
            return;
        }

        Entity mount = player.getRootVehicle();
        List<Entity> aboard = new ArrayList<>();
        if (mount != player) {
            aboard.add(mount);
            mount.getIndirectPassengers().forEach(aboard::add);
        }
        List<Entity> leashed = leashedTo(player, aboard);

        boolean steeredByOther = mount != player && mount.getControllingPassenger() instanceof Player p && p != player;
        if (mount != player && TravelRules.takesMount(steeredByOther, fits(mount, target, pos))) {
            for (Entity e : aboard)
                if (e != player && e != mount && !TravelRules.passengerComes(e instanceof Player)) e.stopRiding();
            mount.teleport(there);                                      // vanilla moves the riders, then seats them again
            if (player.level() != target) player.teleport(there);       // the ride failed somewhere: go on foot
        } else {
            player.teleport(there);                                     // not asPassenger: set down from the mount
        }
        if (player.level() != target) return;                           // the trip itself was refused

        for (Entity e : leashed) {
            if (e.isRemoved() || !(e instanceof Leashable l)) continue;
            l.setLeashData(null);                                       // let go quietly (no lead dropped), and tie again there
            Entity moved = e.teleport(there);
            if (moved instanceof Leashable ml) ml.setLeashedTo(player, true);
        }
    }

    /** Animals on the player's lead within lead range, not already aboard the mount, not monsters. */
    private static List<Entity> leashedTo(ServerPlayer player, List<Entity> aboard) {
        return player.level().getEntities((Entity) null, player.getBoundingBox().inflate(Leashable.LEASH_TOO_FAR_DIST),
                e -> e instanceof Leashable l && l.getLeashHolder() == player
                        && TravelRules.leashedFollows(e instanceof Enemy, aboard.contains(e)));
    }

    /** Whether the mount has room to stand where the player arrives. */
    private static boolean fits(Entity mount, ServerLevel target, Vec3 pos) {
        target.getChunk(BlockPos.containing(pos));
        return target.noCollision(mount.getType().getDimensions().makeBoundingBox(pos));
    }
}
