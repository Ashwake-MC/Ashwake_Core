package com.ashwake.core.client;

import com.ashwake.core.AshwakeCore;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ambient.AmbientCreature;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.WaterAnimal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.phys.EntityHitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLivingEvent;

@EventBusSubscriber(modid = AshwakeCore.MOD_ID, value = Dist.CLIENT)
public final class EntityHealthOverlay {
    private static final float MAX_DISTANCE = 16.0f;
    private static final float SHOW_DISTANCE = 8.0f;
    private static final int BAR_WIDTH = 40;
    private static final int HEALTH_BAR_HEIGHT = 3;
    private static final int ARMOR_BAR_HEIGHT = 2;
    private static final int GAP = 1;
    
    private static final int COLOR_BG = 0xCC000000;
    private static final int COLOR_BORDER = 0xFF1B120C;
    private static final int COLOR_TEXT = 0xFFF1E9D0;
    private static final int COLOR_NAME = 0xFFFFFFFF;
    
    private static final int COLOR_HEALTH_START = 0xFFC75B4E;
    private static final int COLOR_HEALTH_END = 0xFF71261F;
    private static final int COLOR_ARMOR_START = 0xFFA8BAC9;
    private static final int COLOR_ARMOR_END = 0xFF4D5D69;
    private static final int COLOR_ABSORPTION_START = 0xFFEAD683;
    private static final int COLOR_ABSORPTION_END = 0xFF9B8236;

    private EntityHealthOverlay() {
    }

    @SubscribeEvent
    public static void onRenderLiving(RenderLivingEvent.Post<?, ?> event) {
        LivingEntity entity = event.getEntity();
        if (entity == Minecraft.getInstance().player || entity.isInvisible() || !entity.isAlive()) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui || mc.getCameraEntity() == null) {
            return;
        }
        
        double distSq = entity.distanceToSqr(mc.getCameraEntity());
        if (distSq > MAX_DISTANCE * MAX_DISTANCE) {
            return;
        }

        // Only show if damaged, or very close, or being looked at
        boolean isDamaged = entity.getHealth() < entity.getMaxHealth();
        boolean isClose = distSq < SHOW_DISTANCE * SHOW_DISTANCE;
        boolean isTarget = mc.hitResult instanceof EntityHitResult ehr && ehr.getEntity() == entity;

        if (!isDamaged && !isClose && !isTarget) {
            return;
        }

        // Only animals and hostiles as requested
        if (!(entity instanceof Animal) && !(entity instanceof Enemy) && 
            !(entity instanceof WaterAnimal) && !(entity instanceof AmbientCreature) && 
            !(entity instanceof NeutralMob)) {
            return;
        }

        renderOverlay(event, entity, (float) distSq);
    }

    private static void renderOverlay(RenderLivingEvent.Post<?, ?> event, LivingEntity entity, float distSq) {
        PoseStack poseStack = event.getPoseStack();
        MultiBufferSource bufferSource = event.getMultiBufferSource();
        Minecraft mc = Minecraft.getInstance();

        float alphaMult = 1.0f - (distSq / (MAX_DISTANCE * MAX_DISTANCE));
        if (alphaMult <= 0.1f) {
            return;
        }

        poseStack.pushPose();

        // Move above the entity's head.
        float yOffset = entity.getBbHeight() + 0.4f;
        poseStack.translate(0, yOffset, 0);

        // Face the camera
        poseStack.mulPose(mc.getEntityRenderDispatcher().cameraOrientation());
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F));

        // Scale for world space rendering
        float scale = 0.025f;
        poseStack.scale(scale, -scale, scale);

        // Use GuiGraphics for cleaner drawing
        MultiBufferSource.BufferSource bs = bufferSource instanceof MultiBufferSource.BufferSource 
                ? (MultiBufferSource.BufferSource) bufferSource 
                : mc.renderBuffers().bufferSource();
        GuiGraphics guiGraphics = new GuiGraphics(mc, bs);
        
        guiGraphics.pose().pushPose();
        // Match the current matrix from poseStack
        guiGraphics.pose().last().pose().mul(poseStack.last().pose());
        guiGraphics.pose().last().normal().mul(poseStack.last().normal());

        int x = -BAR_WIDTH / 2;
        int y = 0;

        int bgColor = applyAlpha(COLOR_BG, alphaMult);
        int borderColor = applyAlpha(COLOR_BORDER, alphaMult);
        int textColor = applyAlpha(COLOR_TEXT, alphaMult);
        
        float health = entity.getHealth();
        float maxHealth = entity.getMaxHealth();
        float absorption = entity.getAbsorptionAmount();
        int armor = entity.getArmorValue();
        
        // 1. Name and Text above
        String name = entity.getDisplayName().getString();
        String healthText = (int) Math.ceil(health + absorption) + " / " + (int) Math.ceil(maxHealth);
        float textScale = 0.4f;
        
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(0, y - 10, 0);
        guiGraphics.pose().scale(textScale, textScale, textScale);
        
        // Draw Name
        guiGraphics.drawString(mc.font, name, -mc.font.width(name) / 2, 0, applyAlpha(COLOR_NAME, alphaMult), true);
        
        // Draw HP Text below name
        guiGraphics.pose().translate(0, 10, 0);
        guiGraphics.drawString(mc.font, healthText, -mc.font.width(healthText) / 2, 0, textColor, true);
        
        if (armor > 0) {
            String armorText = armor + " AR";
            int textX = mc.font.width(healthText) / 2 + 4;
            guiGraphics.drawString(mc.font, armorText, textX, 0, applyAlpha(COLOR_ARMOR_START, alphaMult), true);
        }
        guiGraphics.pose().popPose();

        // 2. Health Bar
        int totalHeight = HEALTH_BAR_HEIGHT + (armor > 0 ? GAP + ARMOR_BAR_HEIGHT : 0);
        
        // Background & Border
        guiGraphics.fill(x - 1, y - 1, x + BAR_WIDTH + 1, y + totalHeight + 1, borderColor);
        guiGraphics.fill(x, y, x + BAR_WIDTH, y + totalHeight, bgColor);

        // Health Fill
        float healthProgress = Math.clamp(health / maxHealth, 0.0f, 1.0f);
        int healthWidth = (int) (BAR_WIDTH * healthProgress);
        if (healthWidth > 0) {
            guiGraphics.fillGradient(x, y, x + healthWidth, y + HEALTH_BAR_HEIGHT, 
                    applyAlpha(COLOR_HEALTH_START, alphaMult), applyAlpha(COLOR_HEALTH_END, alphaMult));
        }
        
        // Absorption Fill (on top of health bar)
        if (absorption > 0) {
            float absProgress = Math.clamp(absorption / maxHealth, 0.0f, 1.0f);
            int absWidth = (int) (BAR_WIDTH * absProgress);
            if (absWidth > 0) {
                // Draw absorption as a thinner line on top of the health bar or overlapping
                guiGraphics.fillGradient(x, y + HEALTH_BAR_HEIGHT - 1, x + absWidth, y + HEALTH_BAR_HEIGHT, 
                        applyAlpha(COLOR_ABSORPTION_START, alphaMult), applyAlpha(COLOR_ABSORPTION_END, alphaMult));
            }
        }

        // 3. Armor Bar
        if (armor > 0) {
            int ay = y + HEALTH_BAR_HEIGHT + GAP;
            float armorProgress = Math.clamp(armor / 20.0f, 0.0f, 1.0f);
            int armorWidth = (int) (BAR_WIDTH * armorProgress);
            if (armorWidth > 0) {
                guiGraphics.fillGradient(x, ay, x + armorWidth, ay + ARMOR_BAR_HEIGHT, 
                        applyAlpha(COLOR_ARMOR_START, alphaMult), applyAlpha(COLOR_ARMOR_END, alphaMult));
            }
        }

        guiGraphics.pose().popPose();
        poseStack.popPose();
    }

    private static int applyAlpha(int color, float alphaMult) {
        int alpha = (int) (((color >> 24) & 0xFF) * alphaMult);
        return (alpha << 24) | (color & 0x00FFFFFF);
    }
}
