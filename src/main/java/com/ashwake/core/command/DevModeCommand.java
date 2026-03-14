package com.ashwake.core.command;

import com.ashwake.core.dev.DevModeState;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

public final class DevModeCommand {
    private DevModeCommand() {
    }

    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(
                Commands.literal("dev")
                        .requires(source -> source.getPlayer() != null)
                        .then(Commands.literal("on")
                                .executes(context -> setDevMode(context, true)))
                        .then(Commands.literal("off")
                                .executes(context -> setDevMode(context, false)))
        );
    }

    private static int setDevMode(CommandContext<CommandSourceStack> context, boolean enabled) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        DevModeState.setEnabled(player, enabled);
        player.setGameMode(enabled ? GameType.CREATIVE : GameType.SURVIVAL);

        context.getSource().sendSuccess(
                () -> Component.literal(enabled
                        ? "Dev mode enabled. Creative mode and Ashwake dev powers are now active."
                        : "Dev mode disabled. You are now a normal survival player."),
                false
        );
        return 1;
    }
}
