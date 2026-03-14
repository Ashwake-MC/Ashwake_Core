package com.ashwake.core.dev;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

public final class DevModeState {
    private static final String DEV_MODE_TAG = "ashwake_core.dev_mode";

    private DevModeState() {
    }

    public static boolean canUseDevPowers(CommandSourceStack source) {
        return source.hasPermission(2) || isEnabled(source.getPlayer());
    }

    public static boolean isEnabled(Player player) {
        if (player == null) {
            return false;
        }

        return player.getPersistentData()
                .getCompound(Player.PERSISTED_NBT_TAG)
                .getBoolean(DEV_MODE_TAG);
    }

    public static void setEnabled(ServerPlayer player, boolean enabled) {
        CompoundTag persistedData = player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        if (enabled) {
            persistedData.putBoolean(DEV_MODE_TAG, true);
        } else {
            persistedData.remove(DEV_MODE_TAG);
        }

        player.getPersistentData().put(Player.PERSISTED_NBT_TAG, persistedData);
    }

    public static void onPlayerClone(PlayerEvent.Clone event) {
        CompoundTag originalPersistedData = event.getOriginal().getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        if (!originalPersistedData.contains(DEV_MODE_TAG)) {
            return;
        }

        CompoundTag newPersistedData = event.getEntity().getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        newPersistedData.putBoolean(DEV_MODE_TAG, true);
        event.getEntity().getPersistentData().put(Player.PERSISTED_NBT_TAG, newPersistedData);
    }
}
