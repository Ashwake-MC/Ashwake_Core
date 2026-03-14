package com.ashwake.core.network;

import com.ashwake.core.AshwakeCore;
import com.ashwake.core.client.ClientTreeHudState;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record SyncTreeHudPayload(boolean visible, String descriptionKey, int currentHits, int maxHits, int displayTicks)
        implements CustomPacketPayload {
    public static final Type<SyncTreeHudPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(
            AshwakeCore.MOD_ID,
            "sync_tree_hud"
    ));
    public static final StreamCodec<RegistryFriendlyByteBuf, SyncTreeHudPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public SyncTreeHudPayload decode(RegistryFriendlyByteBuf buffer) {
            boolean visible = buffer.readBoolean();
            if (!visible) {
                return clear();
            }

            return new SyncTreeHudPayload(
                    true,
                    buffer.readUtf(),
                    buffer.readVarInt(),
                    buffer.readVarInt(),
                    buffer.readVarInt()
            );
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buffer, SyncTreeHudPayload payload) {
            buffer.writeBoolean(payload.visible);
            if (!payload.visible) {
                return;
            }

            buffer.writeUtf(payload.descriptionKey);
            buffer.writeVarInt(payload.currentHits);
            buffer.writeVarInt(payload.maxHits);
            buffer.writeVarInt(payload.displayTicks);
        }
    };

    public static SyncTreeHudPayload show(String descriptionKey, int currentHits, int maxHits, int displayTicks) {
        return new SyncTreeHudPayload(true, descriptionKey, currentHits, maxHits, displayTicks);
    }

    public static SyncTreeHudPayload clear() {
        return new SyncTreeHudPayload(false, "", 0, 0, 0);
    }

    public static void handle(SyncTreeHudPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (payload.visible) {
                ClientTreeHudState.show(payload.descriptionKey, payload.currentHits, payload.maxHits, payload.displayTicks);
            } else {
                ClientTreeHudState.clear();
            }
        });
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
