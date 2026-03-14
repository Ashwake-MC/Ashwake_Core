package com.ashwake.core.world;

import com.ashwake.core.dev.DevModeState;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.neoforged.neoforge.common.util.BlockSnapshot;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.event.level.PistonEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

public final class SpawnStructureProtectionHandler {
    private static final int WARNING_DURATION_TICKS = 28;
    private static final int WARNING_FRAME_LENGTH = 6;
    private static final Map<UUID, Integer> ACTIVE_WARNINGS = new HashMap<>();

    private SpawnStructureProtectionHandler() {
    }

    public static void onBreak(BlockEvent.BreakEvent event) {
        if (DevModeState.isEnabled(event.getPlayer())) {
            return;
        }

        if (isProtected(event.getLevel(), event.getPos())) {
            event.setCanceled(true);
            warnPlayer(event.getPlayer());
        }
    }

    public static void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (hasDevBypass(event.getEntity())) {
            return;
        }

        if (isProtected(event.getLevel(), event.getPos())) {
            event.setCanceled(true);
            warnEntity(event.getEntity());
        }
    }

    public static void onMultiPlace(BlockEvent.EntityMultiPlaceEvent event) {
        if (hasDevBypass(event.getEntity())) {
            return;
        }

        for (BlockSnapshot snapshot : event.getReplacedBlockSnapshots()) {
            if (isProtected(snapshot.getLevel(), snapshot.getPos())) {
                event.setCanceled(true);
                warnEntity(event.getEntity());
                return;
            }
        }
    }

    public static void onFluidPlace(BlockEvent.FluidPlaceBlockEvent event) {
        if (isProtected(event.getLevel(), event.getPos()) || isProtected(event.getLevel(), event.getLiquidPos())) {
            event.setCanceled(true);
        }
    }

    public static void onToolModification(BlockEvent.BlockToolModificationEvent event) {
        if (DevModeState.isEnabled(event.getPlayer())) {
            return;
        }

        if (isProtected(event.getLevel(), event.getPos())) {
            event.setCanceled(true);
            warnPlayer(event.getPlayer());
        }
    }

    public static void onExplosionDetonate(ExplosionEvent.Detonate event) {
        BoundingBox protectionBounds = getProtectionBounds(event.getLevel());
        if (protectionBounds == null) {
            return;
        }

        event.getAffectedBlocks().removeIf(protectionBounds::isInside);
    }

    public static void onPistonPre(PistonEvent.Pre event) {
        BoundingBox protectionBounds = getProtectionBounds(event.getLevel());
        if (protectionBounds == null) {
            return;
        }

        if (protectionBounds.isInside(event.getPos())) {
            event.setCanceled(true);
            return;
        }

        if (!event.getStructureHelper().resolve()) {
            return;
        }

        for (BlockPos pos : event.getStructureHelper().getToPush()) {
            if (protectionBounds.isInside(pos) || protectionBounds.isInside(pos.relative(event.getDirection()))) {
                event.setCanceled(true);
                return;
            }
        }

        for (BlockPos pos : event.getStructureHelper().getToDestroy()) {
            if (protectionBounds.isInside(pos)) {
                event.setCanceled(true);
                return;
            }
        }
    }

    public static void onServerTick(ServerTickEvent.Post event) {
        if (ACTIVE_WARNINGS.isEmpty()) {
            return;
        }

        MinecraftServer server = event.getServer();
        int currentTick = server.getTickCount();
        Iterator<Map.Entry<UUID, Integer>> iterator = ACTIVE_WARNINGS.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, Integer> entry = iterator.next();
            if (currentTick > entry.getValue()) {
                iterator.remove();
                continue;
            }

            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player == null) {
                iterator.remove();
                continue;
            }

            player.displayClientMessage(buildWarningMessage(currentTick), true);
        }
    }

    private static boolean isProtected(net.minecraft.world.level.LevelAccessor level, BlockPos pos) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return false;
        }

        return SpawnStructureSavedData.get(serverLevel).isProtected(pos);
    }

    private static BoundingBox getProtectionBounds(net.minecraft.world.level.LevelAccessor level) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return null;
        }

        SpawnStructureSavedData savedData = SpawnStructureSavedData.get(serverLevel);
        return savedData.isPlaced() ? savedData.getProtectionBounds() : null;
    }

    private static void warnEntity(Entity entity) {
        if (entity instanceof Player player) {
            warnPlayer(player);
        }
    }

    private static boolean hasDevBypass(Entity entity) {
        return entity instanceof Player player && DevModeState.isEnabled(player);
    }

    private static void warnPlayer(Player player) {
        if (player instanceof ServerPlayer serverPlayer) {
            int expiresAt = serverPlayer.server.getTickCount() + WARNING_DURATION_TICKS;
            ACTIVE_WARNINGS.put(serverPlayer.getUUID(), expiresAt);
            serverPlayer.displayClientMessage(buildWarningMessage(serverPlayer.server.getTickCount()), true);
        }
    }

    private static Component buildWarningMessage(int tick) {
        int frame = (tick / WARNING_FRAME_LENGTH) % 2;
        ChatFormatting accent = (frame == 0) ? ChatFormatting.GOLD : ChatFormatting.YELLOW;
        ChatFormatting danger = (frame == 0) ? ChatFormatting.DARK_RED : ChatFormatting.RED;

        MutableComponent message = Component.empty()
                .append(Component.literal("[!] ").withStyle(accent, ChatFormatting.BOLD))
                .append(Component.literal("Ashwake").withStyle(accent, ChatFormatting.BOLD))
                .append(Component.literal(" spawn structure is ").withStyle(ChatFormatting.WHITE))
                .append(Component.literal("protected.").withStyle(danger, ChatFormatting.BOLD));

        return message;
    }
}
