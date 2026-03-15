package com.ashwake.core.client.minimap;

import com.ashwake.core.AshwakeCore;
import net.minecraft.client.KeyMapping;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import org.lwjgl.glfw.GLFW;

@EventBusSubscriber(modid = AshwakeCore.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class MinimapKeyMappings {
    public static final KeyMapping OPEN_MINIMAP = new KeyMapping(
            "key.ashwake_core.open_minimap",
            GLFW.GLFW_KEY_M,
            "key.categories.misc"
    );

    public static final KeyMapping ZOOM_IN = new KeyMapping(
            "key.ashwake_core.minimap_zoom_in",
            GLFW.GLFW_KEY_EQUAL,
            "key.categories.misc"
    );

    public static final KeyMapping ZOOM_OUT = new KeyMapping(
            "key.ashwake_core.minimap_zoom_out",
            GLFW.GLFW_KEY_MINUS,
            "key.categories.misc"
    );

    private MinimapKeyMappings() {
    }

    @SubscribeEvent
    public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(OPEN_MINIMAP);
        event.register(ZOOM_IN);
        event.register(ZOOM_OUT);
    }
}
