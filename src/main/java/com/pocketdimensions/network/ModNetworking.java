package com.pocketdimensions.network;

import com.pocketdimensions.PocketDimensionsConfig;
import com.pocketdimensions.PocketDimensionsMod;
import com.pocketdimensions.advancement.Milestones;
import com.pocketdimensions.blockentity.WorldCoreBlockEntity;
import com.pocketdimensions.manager.RealmManager;
import com.pocketdimensions.manager.RealmRules;
import com.pocketdimensions.menu.WorldCoreMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.UsernameCache;
import net.minecraftforge.event.network.CustomPayloadEvent;
import net.minecraftforge.network.ChannelBuilder;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.SimpleChannel;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The World Core screen's packets: one action from the screen (add, remove, crown, rename, relocate), checked against
 * the sender's role on the server, and one sync of everything the screen shows. Also carries the siege bars' state
 * ({@link SiegeBarS2C}).
 */
public class ModNetworking {

    private static SimpleChannel CHANNEL;
    private static final UUID NONE = new UUID(0, 0);

    public static void register() {
        CHANNEL = ChannelBuilder
                .named(Identifier.fromNamespaceAndPath(PocketDimensionsMod.MODID, "core"))
                .optional()
                .simpleChannel();
        CHANNEL.messageBuilder(CoreActionC2S.class, NetworkDirection.PLAY_TO_SERVER)
                .encoder(CoreActionC2S::encode).decoder(CoreActionC2S::decode).consumerMainThread(CoreActionC2S::handle).add();
        CHANNEL.messageBuilder(CoreSyncS2C.class, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(CoreSyncS2C::encode).decoder(CoreSyncS2C::decode).consumerMainThread(CoreSyncS2C::handle).add();
        CHANNEL.messageBuilder(SiegeBarS2C.class, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(SiegeBarS2C::encode).decoder(SiegeBarS2C::decode).consumerMainThread(SiegeBarS2C::handle).add();
        CHANNEL.build();
    }

    public static SimpleChannel getChannel() { return CHANNEL; }

    public record PlayerEntry(UUID uuid, String name) {}
    public record AccessEntry(UUID uuid, String name, boolean manager) {}

    // -------------------------------------------------------------------------
    // C2S: an action from the core's screen
    // -------------------------------------------------------------------------

    public record CoreActionC2S(BlockPos corePos, int action, String text, UUID target) {
        public static final int ADD = 0, REMOVE = 1, TOGGLE_MANAGER = 2, RENAME = 3, RELOCATE = 4;

        public static void encode(CoreActionC2S m, FriendlyByteBuf buf) {
            buf.writeBlockPos(m.corePos); buf.writeVarInt(m.action); buf.writeUtf(m.text, 64); buf.writeUUID(m.target);
        }

        public static CoreActionC2S decode(FriendlyByteBuf buf) {
            return new CoreActionC2S(buf.readBlockPos(), buf.readVarInt(), buf.readUtf(64), buf.readUUID());
        }

        public static void handle(CoreActionC2S m, CustomPayloadEvent.Context ctx) {
            ServerPlayer sender = ctx.getSender();
            if (sender == null) return;
            if (!(sender.containerMenu instanceof WorldCoreMenu menu) || !menu.getBlockPos().equals(m.corePos)) return;
            if (!(sender.level().getBlockEntity(m.corePos) instanceof WorldCoreBlockEntity wc) || wc.getOwnerUUID() == null) return;

            MinecraftServer server = ((ServerLevel) sender.level()).getServer();
            RealmManager mgr = RealmManager.get(server);
            UUID owner = wc.getOwnerUUID();
            RealmRules.Role role = mgr.roleOf(owner, sender.getUUID());

            switch (m.action) {
                case ADD -> {
                    if (!RealmRules.canManage(role)) return;
                    addPlayer(sender, server, mgr, owner, m.text, m.target);
                }
                case REMOVE -> {
                    if (!RealmRules.canRemove(role, mgr.isManager(owner, m.target))) return;
                    if (mgr.removeAllowedPlayer(owner, m.target)) {
                        tell(sender, resolveName(m.target, server) + " has been cast from the realm's wards.");
                    }
                }
                case TOGGLE_MANAGER -> {
                    if (!RealmRules.canCrown(role) || !mgr.isAllowed(owner, m.target)) return;
                    boolean crowned = mgr.toggleManager(owner, m.target);
                    tell(sender, resolveName(m.target, server) + (crowned
                            ? " now keeps the realm beside you."
                            : " no longer keeps the realm."));
                }
                case RENAME -> {
                    if (!RealmRules.canManage(role)) return;
                    String name = RealmRules.cleanName(m.text);
                    mgr.setName(owner, name);
                    if (!name.isEmpty()) Milestones.reach(sender, Milestones.NAME_REALM);
                    tell(sender, "The realm will be known as " + RealmRules.displayName(name, resolveName(owner, server)) + ".");
                }
                case RELOCATE -> {
                    if (!RealmRules.canRelocate(role)) return;
                    ServerLevel realm = server.getLevel(PocketDimensionsMod.REALM_DIM);
                    if (realm == null) return;
                    sender.closeContainer();
                    mgr.relocate(owner, realm);
                    tell(sender, "The realm unravels, and somewhere new it begins to grow again.");
                    return;                                         // the old core is gone; nothing left to sync
                }
                default -> { return; }
            }
            sendSync(sender, wc);
        }

        private static void addPlayer(ServerPlayer sender, MinecraftServer server, RealmManager mgr, UUID owner, String name, UUID target) {
            UUID id = target;
            if (id.equals(NONE)) {
                ServerPlayer online = server.getPlayerList().getPlayerByName(name);
                if (online != null) id = online.getUUID();
                else for (var e : UsernameCache.getMap().entrySet()) if (e.getValue().equalsIgnoreCase(name)) { id = e.getKey(); break; }
            }
            if (id.equals(NONE)) { tell(sender, "No soul by that name has ever set foot in this world."); return; }
            if (id.equals(owner)) { tell(sender, "The realm's owner needs no leave to enter."); return; }
            int max = PocketDimensionsConfig.MAX_ALLOWED_PLAYERS.get();
            if (max > 0 && mgr.getAllowedPlayers(owner).size() >= max) {
                tell(sender, "The realm's wards can hold no more names. The limit of " + max + " has been reached."); return;
            }
            if (!mgr.addAllowedPlayer(owner, id)) { tell(sender, "That soul already walks freely through the realm's wards."); return; }
            tell(sender, resolveName(id, server) + " has been granted passage through the realm's wards.");
            Milestones.reach(sender, Milestones.GRANT_ACCESS);
        }
    }

    // -------------------------------------------------------------------------
    // S2C: everything the core's screen shows
    // -------------------------------------------------------------------------

    /**
     * {@code siege} is the core's siege state (WorldCoreBlockEntity.STATE_*), and {@code breachOpen} whether a completed
     * World Breacher holds the realm open. They ride here as well as in the menu's ContainerData: a data slot is only
     * resent when it changes, so one that reached the client before its screen existed would be lost for good.
     */
    public record CoreSyncS2C(int role, String name, String ownerName, List<AccessEntry> allowed,
                              List<PlayerEntry> online, List<PlayerEntry> inRealm, int siege, boolean breachOpen) {

        public static void encode(CoreSyncS2C m, FriendlyByteBuf buf) {
            buf.writeVarInt(m.role); buf.writeUtf(m.name, 64); buf.writeUtf(m.ownerName, 64);
            buf.writeVarInt(m.siege); buf.writeBoolean(m.breachOpen);
            buf.writeVarInt(m.allowed.size());
            for (AccessEntry e : m.allowed) { buf.writeUUID(e.uuid()); buf.writeUtf(e.name(), 64); buf.writeBoolean(e.manager()); }
            writePlayers(buf, m.online);
            writePlayers(buf, m.inRealm);
        }

        public static CoreSyncS2C decode(FriendlyByteBuf buf) {
            int role = buf.readVarInt(); String name = buf.readUtf(64), ownerName = buf.readUtf(64);
            int siege = buf.readVarInt(); boolean breachOpen = buf.readBoolean();
            int n = buf.readVarInt();
            List<AccessEntry> allowed = new ArrayList<>(n);
            for (int i = 0; i < n; i++) allowed.add(new AccessEntry(buf.readUUID(), buf.readUtf(64), buf.readBoolean()));
            return new CoreSyncS2C(role, name, ownerName, allowed, readPlayers(buf), readPlayers(buf), siege, breachOpen);
        }

        public static void handle(CoreSyncS2C m, CustomPayloadEvent.Context ctx) {
            var mc = net.minecraft.client.Minecraft.getInstance();
            if (mc.player != null && mc.player.containerMenu instanceof WorldCoreMenu menu) menu.applySync(m);
        }

        private static void writePlayers(FriendlyByteBuf buf, List<PlayerEntry> list) {
            buf.writeVarInt(list.size());
            for (PlayerEntry e : list) { buf.writeUUID(e.uuid()); buf.writeUtf(e.name(), 64); }
        }

        private static List<PlayerEntry> readPlayers(FriendlyByteBuf buf) {
            int n = buf.readVarInt();
            List<PlayerEntry> out = new ArrayList<>(n);
            for (int i = 0; i < n; i++) out.add(new PlayerEntry(buf.readUUID(), buf.readUtf(64)));
            return out;
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /** Everything player's screen at this core should show, as they are allowed to see it. */
    public static CoreSyncS2C buildSync(ServerPlayer player, WorldCoreBlockEntity core) {
        MinecraftServer server = ((ServerLevel) player.level()).getServer();
        RealmManager mgr = RealmManager.get(server);
        UUID owner = core.getOwnerUUID();
        if (owner == null) return new CoreSyncS2C(RealmRules.Role.VISITOR.ordinal(), "", "", List.of(), List.of(), List.of(),
                core.getSiegeState(), false);
        RealmRules.Role role = mgr.roleOf(owner, player.getUUID());

        List<UUID> allowedIds = mgr.getAllowedPlayers(owner);
        List<AccessEntry> allowed = new ArrayList<>();
        List<PlayerEntry> online = new ArrayList<>();
        if (RealmRules.canManage(role)) {                 // visitors never see who has access
            for (UUID id : allowedIds) allowed.add(new AccessEntry(id, resolveName(id, server), mgr.isManager(owner, id)));
            for (ServerPlayer sp : server.getPlayerList().getPlayers()) {
                if (!sp.getUUID().equals(owner) && !allowedIds.contains(sp.getUUID())) online.add(new PlayerEntry(sp.getUUID(), sp.getGameProfile().name()));
            }
        }
        List<PlayerEntry> inRealm = new ArrayList<>();
        ServerLevel realm = server.getLevel(PocketDimensionsMod.REALM_DIM);
        if (realm != null) for (ServerPlayer sp : realm.players()) {
            if (mgr.isWithinRealm(owner, sp.getX(), sp.getZ())) inRealm.add(new PlayerEntry(sp.getUUID(), sp.getGameProfile().name()));
        }
        return new CoreSyncS2C(role.ordinal(), mgr.getName(owner), resolveName(owner, server), allowed, online, inRealm,
                core.getSiegeState(), mgr.isBreachOpen(server, owner));
    }

    public static void sendSync(ServerPlayer player, WorldCoreBlockEntity core) {
        CHANNEL.send(buildSync(player, core), PacketDistributor.PLAYER.with(player));
    }

    private static void tell(ServerPlayer player, String msg) {
        player.displayClientMessage(Component.literal(msg), false);
    }

    public static String resolveName(UUID uuid, MinecraftServer server) {
        ServerPlayer online = server.getPlayerList().getPlayer(uuid);
        if (online != null) return online.getGameProfile().name();
        String cached = UsernameCache.getLastKnownUsername(uuid);
        return cached != null ? cached : uuid.toString().substring(0, 8) + "...";
    }
}
