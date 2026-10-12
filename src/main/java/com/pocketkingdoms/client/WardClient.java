package com.pocketkingdoms.client;

import com.pocketkingdoms.PocketKingdomsMod;
import com.pocketkingdoms.manager.RealmWard;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.LogicalSide;

/**
 * The ward's ceiling on the player's own side: before the player moves, upward speed is cut so the move ends at the
 * ceiling; after, anything left over is put back under it, unless that would push into a block (then the server finds
 * a free spot). The player (or their mount, which is what moves while riding) meets the ceiling like a surface instead
 * of being pulled back by the server.
 */
public final class WardClient {

    private WardClient() {}

    public static void register() {
        TickEvent.PlayerTickEvent.Pre.BUS.addListener(WardClient::beforeMove);
        TickEvent.PlayerTickEvent.Post.BUS.addListener(WardClient::afterMove);
    }

    private static Entity moverInRealm(Player player, LogicalSide side) {
        if (side != LogicalSide.CLIENT || player != Minecraft.getInstance().player) return null;
        if (!player.level().dimension().equals(PocketKingdomsMod.REALM_DIM) || player.isSpectator()) return null;
        return player.getRootVehicle();
    }

    private static void beforeMove(TickEvent.PlayerTickEvent.Pre event) {
        Player player = event.player();
        Entity mover = moverInRealm(player, event.side());
        if (mover == null) return;
        double room = RealmWard.CEILING_Y - player.getY();      // how far the feet may still rise
        Vec3 v = mover.getDeltaMovement();
        if (v.y > room) mover.setDeltaMovement(v.x, Math.max(0, room), v.z);
    }

    private static void afterMove(TickEvent.PlayerTickEvent.Post event) {
        Player player = event.player();
        Entity mover = moverInRealm(player, event.side());
        if (mover == null || !RealmWard.aboveCeiling(player.getY())) return;
        double over = player.getY() - RealmWard.CEILING_Y;
        // a mount on a floor near the top, or a block that got up there: never push into it, the server sets them down
        if (!player.level().noCollision(mover, mover.getBoundingBox().move(0, -over, 0))) return;
        mover.setPos(mover.getX(), mover.getY() - over, mover.getZ());
        Vec3 v = mover.getDeltaMovement();
        if (v.y > 0) mover.setDeltaMovement(v.x, 0, v.z);
    }
}
