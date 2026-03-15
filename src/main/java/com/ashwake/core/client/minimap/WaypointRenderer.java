package com.ashwake.core.client.minimap;

import com.ashwake.core.AshwakeCore;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

import java.util.List;

@EventBusSubscriber(modid = AshwakeCore.MOD_ID, value = Dist.CLIENT)
public final class WaypointRenderer {
    private WaypointRenderer() {
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) {
            return;
        }

        List<MinimapWaypoint> waypoints = MinimapClientState.getWaypoints(minecraft);
        if (waypoints.isEmpty()) {
            return;
        }

        Vec3 cameraPos = event.getCamera().getPosition();
        PoseStack poseStack = event.getPoseStack();

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.depthMask(false);
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        for (MinimapWaypoint wp : waypoints) {
            if (!wp.dimensionKey().equals(MinimapClientState.getCurrentDimensionKey(minecraft))) {
                continue;
            }

            double dx = wp.x() + 0.5 - cameraPos.x;
            double dy = wp.y() - cameraPos.y;
            double dz = wp.z() + 0.5 - cameraPos.z;
            double distSq = dx * dx + dz * dz;

            // Only show beam when relatively close (e.g., 200 blocks)
            if (distSq < 200 * 200) {
                float alpha = 1.0f;
                // Fade out when very close to not be annoying
                if (distSq < 10 * 10) {
                    alpha = (float) (Math.sqrt(distSq) / 10.0);
                }
                // Also fade out at distance
                if (distSq > 150 * 150) {
                    alpha = (float) (1.0 - (Math.sqrt(distSq) - 150.0) / 50.0);
                }

                if (alpha > 0) {
                    renderBeam(poseStack, (float) dx, (float) dy, (float) dz, wp.color(), alpha);
                }
            }
        }

        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }

    private static void renderBeam(PoseStack poseStack, float x, float y, float z, int color, float alpha) {
        poseStack.pushPose();
        poseStack.translate(x, y, z); // Center on waypoint

        Matrix4f matrix = poseStack.last().pose();
        
        float r = ((color >> 16) & 0xFF) / 255.0f;
        float g = ((color >> 8) & 0xFF) / 255.0f;
        float b = (color & 0xFF) / 255.0f;

        float radius = 0.15f;
        float height = 256.0f;
        float bottom = 0.0f;

        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder buffer = tesselator.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);

        // North
        buffer.addVertex(matrix, -radius, bottom, -radius).setColor(r, g, b, alpha * 0.5f);
        buffer.addVertex(matrix, radius, bottom, -radius).setColor(r, g, b, alpha * 0.5f);
        buffer.addVertex(matrix, radius, height, -radius).setColor(r, g, b, 0.0f);
        buffer.addVertex(matrix, -radius, height, -radius).setColor(r, g, b, 0.0f);

        // South
        buffer.addVertex(matrix, -radius, bottom, radius).setColor(r, g, b, alpha * 0.5f);
        buffer.addVertex(matrix, radius, bottom, radius).setColor(r, g, b, alpha * 0.5f);
        buffer.addVertex(matrix, radius, height, radius).setColor(r, g, b, 0.0f);
        buffer.addVertex(matrix, -radius, height, radius).setColor(r, g, b, 0.0f);

        // East
        buffer.addVertex(matrix, radius, bottom, -radius).setColor(r, g, b, alpha * 0.5f);
        buffer.addVertex(matrix, radius, bottom, radius).setColor(r, g, b, alpha * 0.5f);
        buffer.addVertex(matrix, radius, height, radius).setColor(r, g, b, 0.0f);
        buffer.addVertex(matrix, radius, height, -radius).setColor(r, g, b, 0.0f);

        // West
        buffer.addVertex(matrix, -radius, bottom, -radius).setColor(r, g, b, alpha * 0.5f);
        buffer.addVertex(matrix, -radius, bottom, radius).setColor(r, g, b, alpha * 0.5f);
        buffer.addVertex(matrix, -radius, height, radius).setColor(r, g, b, 0.0f);
        buffer.addVertex(matrix, -radius, height, -radius).setColor(r, g, b, 0.0f);

        BufferUploader.drawWithShader(buffer.build());
        
        poseStack.popPose();
    }
}
