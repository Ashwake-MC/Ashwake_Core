package com.ashwake.core.network;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

public final class SkillNetworking {
    private SkillNetworking() {
    }

    public static void onRegisterPayloadHandlers(RegisterPayloadHandlersEvent event) {
        event.registrar("1")
                .playToClient(SyncSkillDataPayload.TYPE, SyncSkillDataPayload.STREAM_CODEC, SyncSkillDataPayload::handle)
                .playToClient(SyncTreeHudPayload.TYPE, SyncTreeHudPayload.STREAM_CODEC, SyncTreeHudPayload::handle);
    }

    public static void sync(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, SyncSkillDataPayload.fromPlayer(player));
    }

    public static void showTreeHud(ServerPlayer player, String descriptionKey, int currentHits, int maxHits, int displayTicks) {
        PacketDistributor.sendToPlayer(player, SyncTreeHudPayload.show(descriptionKey, currentHits, maxHits, displayTicks));
    }

    public static void clearTreeHud(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, SyncTreeHudPayload.clear());
    }
}
