package com.pocketdimensions.network;

import com.pocketdimensions.PocketDimensionsConfig;
import com.pocketdimensions.PocketDimensionsMod;
import com.pocketdimensions.blockentity.WorldCoreBlockEntity;
import com.pocketdimensions.manager.RealmManager;
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

public class ModNetworking {

    private static SimpleChannel CHANNEL;

    public static void register() {
        CHANNEL = ChannelBuilder
                .named(Identifier.fromNamespaceAndPath(PocketDimensionsMod.MODID, "allowlist"))
                .optional()
                .simpleChannel();

        CHANNEL.messageBuilder(AllowlistActionC2S.class, NetworkDirection.PLAY_TO_SERVER)
                .encoder(AllowlistActionC2S::encode)
                .decoder(AllowlistActionC2S::decode)
                .consumerMainThread(AllowlistActionC2S::handle)
                .add();

        CHANNEL.messageBuilder(AllowlistSyncS2C.class, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(AllowlistSyncS2C::encode)
                .decoder(AllowlistSyncS2C::decode)
                .consumerMainThread(AllowlistSyncS2C::handle)
                .add();

        CHANNEL.build();
    }

    public static SimpleChannel getChannel() {
        return CHANNEL;
    }

    // -------------------------------------------------------------------------
    // C2S: Client requests add/remove
    // -------------------------------------------------------------------------

    public static class AllowlistActionC2S {
        public static final int ACTION_ADD = 0;
        public static final int ACTION_REMOVE = 1;

        private final BlockPos corePos;
        private final int action;
        private final String playerName;
        private final UUID targetUUID;

        public AllowlistActionC2S(BlockPos corePos, int action, String playerName, UUID targetUUID) {
            this.corePos = corePos;
            this.action = action;
            this.playerName = playerName;
            this.targetUUID = targetUUID;
        }

        public static void encode(AllowlistActionC2S msg, FriendlyByteBuf buf) {
            buf.writeBlockPos(msg.corePos);
            buf.writeVarInt(msg.action);
            buf.writeUtf(msg.playerName, 64);
            buf.writeUUID(msg.targetUUID);
        }

        public static AllowlistActionC2S decode(FriendlyByteBuf buf) {
            return new AllowlistActionC2S(
                    buf.readBlockPos(),
                    buf.readVarInt(),
                    buf.readUtf(64),
                    buf.readUUID()
            );
        }

        public static void handle(AllowlistActionC2S msg, CustomPayloadEvent.Context ctx) {
            ServerPlayer sender = ctx.getSender();
            if (sender == null) return;

            // Validate sender has open WorldCoreMenu at the right position
            if (!(sender.containerMenu instanceof WorldCoreMenu menu)) return;
            if (!menu.getBlockPos().equals(msg.corePos)) return;

            // Validate sender is the realm owner
            if (!(sender.level().getBlockEntity(msg.corePos) instanceof WorldCoreBlockEntity wc)) return;
            if (wc.getOwnerUUID() == null || !wc.getOwnerUUID().equals(sender.getUUID())) return;

            MinecraftServer server = ((ServerLevel) sender.level()).getServer();
            RealmManager mgr = RealmManager.get(server);
            UUID ownerUUID = wc.getOwnerUUID();

            if (msg.action == ACTION_ADD) {
                // Resolve player UUID by name if targetUUID is nil
                UUID targetUUID = msg.targetUUID;
                if (targetUUID.equals(new UUID(0, 0))) {
                    // Try to find by name from online players
                    ServerPlayer target = server.getPlayerList().getPlayerByName(msg.playerName);
                    if (target != null) {
                        targetUUID = target.getUUID();
                    } else {
                        // Try UsernameCache for offline players
                        for (var entry : UsernameCache.getMap().entrySet()) {
                            if (entry.getValue().equalsIgnoreCase(msg.playerName)) {
                                targetUUID = entry.getKey();
                                break;
                            }
                        }
                    }
                }

                if (targetUUID.equals(new UUID(0, 0))) {
                    sender.displayClientMessage(Component.literal(
                            "No soul by that name has ever set foot in this world."), false);
                    return;
                }

                if (targetUUID.equals(ownerUUID)) {
                    sender.displayClientMessage(Component.literal(
                            "You already command this realm, there is no need."), false);
                    return;
                }

                int max = PocketDimensionsConfig.MAX_ALLOWED_PLAYERS.get();
                if (max > 0 && mgr.getAllowedPlayers(ownerUUID).size() >= max) {
                    sender.displayClientMessage(Component.literal(
                            "Your realm's wards can hold no more names. The limit of " + max + " has been reached."), false);
                    return;
                }

                if (!mgr.addAllowedPlayer(ownerUUID, targetUUID)) {
                    sender.displayClientMessage(Component.literal(
                            "That soul already walks freely through your wards."), false);
                    return;
                }

                String name = resolveName(targetUUID, server);
                sender.displayClientMessage(Component.literal(
                        name + " has been granted passage through your realm's wards."), false);

            } else if (msg.action == ACTION_REMOVE) {
                if (!mgr.removeAllowedPlayer(ownerUUID, msg.targetUUID)) return;

                String name = resolveName(msg.targetUUID, server);
                sender.displayClientMessage(Component.literal(
                        name + " has been cast from your realm's wards."), false);
            }

            // Send updated allowlist to client
            sendAllowlistSync(sender, ownerUUID);
        }
    }

    // -------------------------------------------------------------------------
    // S2C: Server sends updated allowlist + online players
    // -------------------------------------------------------------------------

    public record PlayerEntry(UUID uuid, String name) {}

    public static class AllowlistSyncS2C {
        private final List<PlayerEntry> allowed;
        private final List<PlayerEntry> online;

        public AllowlistSyncS2C(List<PlayerEntry> allowed, List<PlayerEntry> online) {
            this.allowed = allowed;
            this.online = online;
        }

        public static void encode(AllowlistSyncS2C msg, FriendlyByteBuf buf) {
            buf.writeVarInt(msg.allowed.size());
            for (PlayerEntry e : msg.allowed) {
                buf.writeUUID(e.uuid);
                buf.writeUtf(e.name, 64);
            }
            buf.writeVarInt(msg.online.size());
            for (PlayerEntry e : msg.online) {
                buf.writeUUID(e.uuid);
                buf.writeUtf(e.name, 64);
            }
        }

        public static AllowlistSyncS2C decode(FriendlyByteBuf buf) {
            int allowedCount = buf.readVarInt();
            List<PlayerEntry> allowed = new ArrayList<>(allowedCount);
            for (int i = 0; i < allowedCount; i++) {
                allowed.add(new PlayerEntry(buf.readUUID(), buf.readUtf(64)));
            }
            int onlineCount = buf.readVarInt();
            List<PlayerEntry> online = new ArrayList<>(onlineCount);
            for (int i = 0; i < onlineCount; i++) {
                online.add(new PlayerEntry(buf.readUUID(), buf.readUtf(64)));
            }
            return new AllowlistSyncS2C(allowed, online);
        }

        public static void handle(AllowlistSyncS2C msg, CustomPayloadEvent.Context ctx) {
            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
            if (mc.player != null && mc.player.containerMenu instanceof WorldCoreMenu menu) {
                menu.setAllowedPlayers(msg.allowed);
                menu.setOnlinePlayers(msg.online);
            }
        }

        public List<PlayerEntry> getAllowed() { return allowed; }
        public List<PlayerEntry> getOnline() { return online; }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    public static void sendAllowlistSync(ServerPlayer player, UUID ownerUUID) {
        MinecraftServer server = ((ServerLevel) player.level()).getServer();
        RealmManager mgr = RealmManager.get(server);

        List<UUID> allowed = mgr.getAllowedPlayers(ownerUUID);
        List<PlayerEntry> allowedEntries = new ArrayList<>();
        for (UUID uuid : allowed) {
            allowedEntries.add(new PlayerEntry(uuid, resolveName(uuid, server)));
        }

        List<PlayerEntry> onlineEntries = new ArrayList<>();
        for (ServerPlayer sp : server.getPlayerList().getPlayers()) {
            UUID spUUID = sp.getUUID();
            if (!spUUID.equals(ownerUUID) && !allowed.contains(spUUID)) {
                onlineEntries.add(new PlayerEntry(spUUID, sp.getGameProfile().name()));
            }
        }

        CHANNEL.send(new AllowlistSyncS2C(allowedEntries, onlineEntries),
                PacketDistributor.PLAYER.with(player));
    }

    private static String resolveName(UUID uuid, MinecraftServer server) {
        ServerPlayer online = server.getPlayerList().getPlayer(uuid);
        if (online != null) return online.getGameProfile().name();
        String cached = UsernameCache.getLastKnownUsername(uuid);
        if (cached != null) return cached;
        return uuid.toString().substring(0, 8) + "...";
    }
}
