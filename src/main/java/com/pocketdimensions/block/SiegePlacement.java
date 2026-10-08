package com.pocketdimensions.block;

import com.pocketdimensions.blockentity.WorldAnchorBlockEntity;
import com.pocketdimensions.manager.RealmManager;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.context.BlockPlaceContext;

/**
 * Both siege blocks can only be set on a linked anchor while the realm has one of its own inside (its owner or someone
 * on its access list; breach visitors and smuggled players don't count), so the defenders are there when the siege
 * starts. The owner is the exception: they may set one on their own anchor at any time (RealmRules.maySetSiege).
 * Checked on the server; the client's guess is corrected.
 */
final class SiegePlacement {

    private SiegePlacement() {}

    /** True when the siege block may go here; otherwise the placer is told why. */
    static boolean defenderInside(BlockPlaceContext ctx, String refusal) {
        if (!(ctx.getLevel() instanceof ServerLevel sl)) return true;
        if (!(sl.getBlockEntity(ctx.getClickedPos().below(2)) instanceof WorldAnchorBlockEntity anchor)
                || anchor.getOwnerUUID() == null) return true;
        java.util.UUID placer = ctx.getPlayer() == null ? null : ctx.getPlayer().getUUID();
        boolean inside = RealmManager.get(sl.getServer()).defenderInside(sl.getServer(), anchor.getOwnerUUID());
        if (com.pocketdimensions.manager.RealmRules.maySetSiege(placer, anchor.getOwnerUUID(), inside)) return true;
        if (ctx.getPlayer() != null) ctx.getPlayer().displayClientMessage(Component.literal(refusal), true);
        return false;
    }
}
