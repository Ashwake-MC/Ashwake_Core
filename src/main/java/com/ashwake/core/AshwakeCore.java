package com.ashwake.core;

import com.ashwake.core.command.AshwakeLocateCommand;
import com.mojang.logging.LogUtils;
import com.ashwake.core.world.SpawnStructureHandler;
import com.ashwake.core.world.SpawnStructureProtectionHandler;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;

@Mod(AshwakeCore.MOD_ID)
public final class AshwakeCore {
    public static final String MOD_ID = "ashwake_core";
    public static final String MOD_NAME = "Ashwake Core";
    public static final Logger LOGGER = LogUtils.getLogger();

    public AshwakeCore() {
        NeoForge.EVENT_BUS.addListener(SpawnStructureHandler::onLevelLoad);
        NeoForge.EVENT_BUS.addListener(SpawnStructureHandler::onPlayerLoggedIn);
        NeoForge.EVENT_BUS.addListener(SpawnStructureProtectionHandler::onBreak);
        NeoForge.EVENT_BUS.addListener(SpawnStructureProtectionHandler::onPlace);
        NeoForge.EVENT_BUS.addListener(SpawnStructureProtectionHandler::onMultiPlace);
        NeoForge.EVENT_BUS.addListener(SpawnStructureProtectionHandler::onFluidPlace);
        NeoForge.EVENT_BUS.addListener(SpawnStructureProtectionHandler::onToolModification);
        NeoForge.EVENT_BUS.addListener(SpawnStructureProtectionHandler::onExplosionDetonate);
        NeoForge.EVENT_BUS.addListener(SpawnStructureProtectionHandler::onPistonPre);
        NeoForge.EVENT_BUS.addListener(SpawnStructureProtectionHandler::onServerTick);
        NeoForge.EVENT_BUS.addListener(AshwakeLocateCommand::onRegisterCommands);
        LOGGER.info("{} is loading.", MOD_NAME);
    }
}
