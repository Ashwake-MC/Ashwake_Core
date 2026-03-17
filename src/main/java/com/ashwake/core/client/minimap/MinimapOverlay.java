package com.ashwake.core.client.minimap;

import com.ashwake.core.AshwakeCore;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

@EventBusSubscriber(modid = AshwakeCore.MOD_ID, value = Dist.CLIENT)
public final class MinimapOverlay {
    private MinimapOverlay() {
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        if (MinimapClientState.getWorldMap() != null) {
            MinimapClientState.getWorldMap().saveAll();
        }
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        MinimapClientState.tick(minecraft);

        while (MinimapKeyMappings.OPEN_MINIMAP.consumeClick()) {
            MinimapClientState.toggleMapScreen(minecraft);
        }

        while (MinimapKeyMappings.ZOOM_IN.consumeClick()) {
            MinimapClientState.zoomIn();
        }

        while (MinimapKeyMappings.ZOOM_OUT.consumeClick()) {
            MinimapClientState.zoomOut();
        }
    }

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null || minecraft.options.hideGui) {
            return;
        }

        if (minecraft.screen != null && !(minecraft.screen instanceof ChatScreen)) {
            return;
        }

        MinimapRenderUtil.renderHud(event.getGuiGraphics(), minecraft, event.getPartialTick().getGameTimeDeltaTicks());
    }
}
