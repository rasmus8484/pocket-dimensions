package com.pocketkingdoms.client.ward;

import com.pocketkingdoms.network.WardSyncS2C;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/**
 * The ward of the realm the player is in, as last sent by the server, with its Vaulted Ward laid out once. It belongs to the
 * level it arrived in: after any dimension change it is forgotten until the next sync.
 */
public final class WardView {

    public record State(WardSyncS2C sync, Vault.Layout layout) {}

    private static @Nullable State state;
    private static @Nullable Level level;

    private WardView() {}

    public static void receive(WardSyncS2C m) {
        Minecraft mc = Minecraft.getInstance();
        if (!m.present()) { state = null; return; }
        Vault.Layout layout = state != null && sameShape(state.sync(), m) ? state.layout()
                : Vault.build(m.coreX() + 0.5, m.coreZ() + 0.5, m.minX(), m.minZ(), m.maxX(), m.maxZ());
        state = new State(m, layout);
        level = mc.level;
    }

    public static @Nullable State current() {
        Minecraft mc = Minecraft.getInstance();
        if (state == null || mc.level == null || mc.level != level) return null;
        return state;
    }

    private static boolean sameShape(WardSyncS2C a, WardSyncS2C b) {
        return a.coreX() == b.coreX() && a.coreZ() == b.coreZ() && a.minX() == b.minX() && a.minZ() == b.minZ()
                && a.maxX() == b.maxX() && a.maxZ() == b.maxZ();
    }
}
