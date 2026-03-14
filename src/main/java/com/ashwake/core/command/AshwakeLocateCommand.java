package com.ashwake.core.command;

import com.ashwake.core.world.SpawnStructureSavedData;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

public final class AshwakeLocateCommand {
    private AshwakeLocateCommand() {
    }

    public static void onRegisterCommands(RegisterCommandsEvent event) {
        if (isProductionEnvironment()) {
            return;
        }

        event.getDispatcher().register(
                Commands.literal("ashwake")
                        .requires(AshwakeLocateCommand::canUseDevCommands)
                        .then(Commands.literal("structure")
                                .then(Commands.literal("spawn")
                                        .then(Commands.literal("locate")
                                                .executes(AshwakeLocateCommand::locateSpawnStructure))
                                        .then(Commands.literal("teleport")
                                                .executes(AshwakeLocateCommand::teleportToSpawnStructure))
                                        .then(Commands.literal("tp")
                                                .executes(AshwakeLocateCommand::teleportToSpawnStructure))))
        );
    }

    private static boolean isProductionEnvironment() {
        return AshwakeLocateCommand.class.getClassLoader().getResource("net/minecraft/DetectedVersion.class") == null;
    }

    private static boolean canUseDevCommands(CommandSourceStack source) {
        return !isProductionEnvironment() && source.hasPermission(2);
    }

    private static int locateSpawnStructure(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        BlockPos spawnPos = getSpawnPos(source);
        if (spawnPos == null) return 0;

        source.sendSuccess(
                () -> Component.literal(
                        "Ashwake spawn structure is at X: "
                                + spawnPos.getX()
                                + " Y: "
                                + spawnPos.getY()
                                + " Z: "
                                + spawnPos.getZ()
                                + " in the Overworld."
                ),
                false
        );
        return 1;
    }

    private static int teleportToSpawnStructure(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        BlockPos spawnPos = getSpawnPos(source);
        if (spawnPos == null) {
            return 0;
        }

        ServerLevel overworld = source.getServer().getLevel(Level.OVERWORLD);
        if (overworld == null) {
            source.sendFailure(Component.literal("The Overworld is not available."));
            return 0;
        }

        ServerPlayer player = source.getPlayerOrException();
        player.teleportTo(
                overworld,
                spawnPos.getX() + 0.5D,
                spawnPos.getY(),
                spawnPos.getZ() + 0.5D,
                player.getYRot(),
                player.getXRot()
        );
        source.sendSuccess(
                () -> Component.literal(
                        "Teleported to the Ashwake spawn structure at X: "
                                + spawnPos.getX()
                                + " Y: "
                                + spawnPos.getY()
                                + " Z: "
                                + spawnPos.getZ()
                                + "."
                ),
                false
        );
        return 1;
    }

    private static BlockPos getSpawnPos(CommandSourceStack source) {
        ServerLevel overworld = source.getServer().getLevel(Level.OVERWORLD);
        if (overworld == null) {
            source.sendFailure(Component.literal("The Overworld is not available."));
            return null;
        }

        SpawnStructureSavedData savedData = SpawnStructureSavedData.get(overworld);
        if (!savedData.isPlaced()) {
            source.sendFailure(Component.literal("The Ashwake spawn structure has not been created in this world yet."));
            return null;
        }

        return savedData.getSpawnPos();
    }
}
