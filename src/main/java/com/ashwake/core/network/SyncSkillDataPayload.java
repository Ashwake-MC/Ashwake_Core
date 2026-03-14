package com.ashwake.core.network;

import com.ashwake.core.AshwakeCore;
import com.ashwake.core.skills.ClientSkillState;
import com.ashwake.core.skills.PlayerSkills;
import com.ashwake.core.skills.SkillProgress;
import com.ashwake.core.skills.SkillType;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record SyncSkillDataPayload(EnumMap<SkillType, SkillProgress> progress) implements CustomPacketPayload {
    public static final Type<SyncSkillDataPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(
            AshwakeCore.MOD_ID,
            "sync_skill_data"
    ));
    public static final StreamCodec<RegistryFriendlyByteBuf, SyncSkillDataPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public SyncSkillDataPayload decode(RegistryFriendlyByteBuf buffer) {
            EnumMap<SkillType, SkillProgress> decoded = new EnumMap<>(SkillType.class);
            for (SkillType skill : SkillType.values()) {
                decoded.put(skill, new SkillProgress(buffer.readVarInt(), buffer.readVarInt()));
            }

            return new SyncSkillDataPayload(decoded);
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buffer, SyncSkillDataPayload payload) {
            for (SkillType skill : SkillType.values()) {
                SkillProgress skillProgress = payload.progress.getOrDefault(skill, SkillProgress.DEFAULT);
                buffer.writeVarInt(skillProgress.level());
                buffer.writeVarInt(skillProgress.experience());
            }
        }
    };

    public SyncSkillDataPayload(Map<SkillType, SkillProgress> progress) {
        this(new EnumMap<>(progress));
    }

    public static SyncSkillDataPayload fromPlayer(ServerPlayer player) {
        return new SyncSkillDataPayload(PlayerSkills.snapshot(player));
    }

    public static void handle(SyncSkillDataPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> ClientSkillState.apply(payload.progress));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
