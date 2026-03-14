package com.ashwake.core.client;

import com.ashwake.core.AshwakeCore;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;

@EventBusSubscriber(modid = AshwakeCore.MOD_ID, value = Dist.CLIENT)
public final class SkillInventoryButtonHandler {
    private static final int BUTTON_WIDTH = 26;
    private static final int BUTTON_HEIGHT = 24;
    private static final int BUTTON_OFFSET_X = 178;
    private static final int BUTTON_OFFSET_Y = 6;

    private SkillInventoryButtonHandler() {
    }

    @SubscribeEvent
    public static void onRender(ScreenEvent.Render.Post event) {
        if (!(event.getScreen() instanceof InventoryScreen inventoryScreen)) {
            return;
        }

        int buttonX = getButtonX(inventoryScreen);
        int buttonY = getButtonY(inventoryScreen);
        boolean hovered = isHovered(inventoryScreen, event.getMouseX(), event.getMouseY());
        GuiGraphics guiGraphics = event.getGuiGraphics();

        drawButton(guiGraphics, buttonX, buttonY, hovered);

        if (hovered) {
            guiGraphics.renderTooltip(Minecraft.getInstance().font, Component.literal("Ashwake Skills"), event.getMouseX(), event.getMouseY());
        }
    }

    @SubscribeEvent
    public static void onMousePressed(ScreenEvent.MouseButtonPressed.Pre event) {
        if (!(event.getScreen() instanceof InventoryScreen inventoryScreen) || event.getButton() != 0) {
            return;
        }

        if (!isHovered(inventoryScreen, event.getMouseX(), event.getMouseY())) {
            return;
        }

        Minecraft.getInstance().setScreen(new SkillStatsScreen(inventoryScreen));
        event.setCanceled(true);
    }

    private static int getButtonX(AbstractContainerScreen<?> screen) {
        return screen.getGuiLeft() + BUTTON_OFFSET_X;
    }

    private static int getButtonY(AbstractContainerScreen<?> screen) {
        return screen.getGuiTop() + BUTTON_OFFSET_Y;
    }

    private static boolean isHovered(AbstractContainerScreen<?> screen, double mouseX, double mouseY) {
        int buttonX = getButtonX(screen);
        int buttonY = getButtonY(screen);
        return mouseX >= buttonX && mouseX < buttonX + BUTTON_WIDTH && mouseY >= buttonY && mouseY < buttonY + BUTTON_HEIGHT;
    }

    private static void drawButton(GuiGraphics guiGraphics, int x, int y, boolean hovered) {
        float time = getHudTime();
        int frameColor = hovered ? 0xFF715437 : 0xFF58412D;
        int trimColor = hovered ? 0xFFD5B178 : 0xFFBE9763;
        int faceTopColor = hovered ? 0xFF473428 : 0xFF3A2B21;
        int faceBottomColor = hovered ? 0xFF2E221A : 0xFF251C15;
        int velvetColor = hovered ? 0xFF345A56 : 0xFF294742;
        int shieldRimColor = hovered ? 0xFFE3C890 : 0xFFCCAF73;
        int shieldFillColor = hovered ? 0xFF6B4B34 : 0xFF563C2A;
        int jewelColor = hovered ? 0xFFD64A42 : 0xFFB83A33;
        int jewelGlowColor = hovered ? 0xFFF09B86 : 0xFFD57764;
        int iconColor = hovered ? 0xFFF1D36A : 0xFFDAB857;
        int pulse = withAlpha(trimColor, hovered ? 126 : 72 + (int) ((Math.sin(time * 0.18F) * 0.5F + 0.5F) * 30.0F));
        int sweepX = x + 4 + Math.round(((float) Math.sin(time * 0.12F) * 0.5F + 0.5F) * 9.0F);

        guiGraphics.fill(x + 1, y + 2, x + BUTTON_WIDTH + 1, y + BUTTON_HEIGHT + 1, 0x88150F0B);
        guiGraphics.fill(x, y + 1, x + BUTTON_WIDTH, y + BUTTON_HEIGHT, 0xCC201711);
        guiGraphics.fill(x, y, x + BUTTON_WIDTH - 1, y + BUTTON_HEIGHT - 1, frameColor);
        guiGraphics.fill(x + 1, y + 1, x + BUTTON_WIDTH - 2, y + BUTTON_HEIGHT - 2, trimColor);
        guiGraphics.fillGradient(x + 2, y + 2, x + BUTTON_WIDTH - 3, y + BUTTON_HEIGHT - 3, faceTopColor, faceBottomColor);
        guiGraphics.fill(x + 3, y + 4, x + BUTTON_WIDTH - 4, y + BUTTON_HEIGHT - 4, velvetColor);
        guiGraphics.fillGradient(x + 4, y + 3, x + BUTTON_WIDTH - 5, y + 8, withAlpha(trimColor, 150), 0x00000000);
        guiGraphics.fill(sweepX, y + 4, sweepX + 6, y + 5, pulse);
        drawOutline(guiGraphics, x, y, BUTTON_WIDTH - 1, BUTTON_HEIGHT - 1, 0xFF241912);
        drawOutline(guiGraphics, x + 1, y + 1, BUTTON_WIDTH - 3, BUTTON_HEIGHT - 3, withAlpha(frameColor, 170));

        drawStud(guiGraphics, x + 2, y + 2, trimColor, frameColor);
        drawStud(guiGraphics, x + BUTTON_WIDTH - 5, y + 2, trimColor, frameColor);
        drawStud(guiGraphics, x + 2, y + BUTTON_HEIGHT - 5, trimColor, frameColor);
        drawStud(guiGraphics, x + BUTTON_WIDTH - 5, y + BUTTON_HEIGHT - 5, trimColor, frameColor);

        guiGraphics.fill(x + 9, y + 3, x + 17, y + 5, shieldRimColor);
        guiGraphics.fill(x + 10, y + 2, x + 16, y + 3, shieldRimColor);
        guiGraphics.fill(x + 11, y + 1, x + 15, y + 2, jewelGlowColor);
        guiGraphics.fill(x + 11, y + 2, x + 15, y + 4, jewelColor);
        guiGraphics.fill(x + 12, y + 3, x + 14, y + 4, jewelGlowColor);

        int shieldX = x + 7;
        int shieldY = y + 6;
        guiGraphics.fill(shieldX + 3, shieldY - 1, shieldX + 9, shieldY, shieldRimColor);
        guiGraphics.fill(shieldX + 1, shieldY, shieldX + 11, shieldY + 1, shieldRimColor);
        guiGraphics.fill(shieldX, shieldY + 1, shieldX + 12, shieldY + 9, shieldRimColor);
        guiGraphics.fill(shieldX + 1, shieldY + 9, shieldX + 11, shieldY + 11, shieldRimColor);
        guiGraphics.fill(shieldX + 3, shieldY + 11, shieldX + 9, shieldY + 13, shieldRimColor);
        guiGraphics.fill(shieldX + 1, shieldY + 1, shieldX + 11, shieldY + 10, shieldFillColor);
        guiGraphics.fill(shieldX + 2, shieldY + 10, shieldX + 10, shieldY + 11, shieldFillColor);
        guiGraphics.fill(shieldX + 4, shieldY + 11, shieldX + 8, shieldY + 12, shieldFillColor);
        guiGraphics.fill(shieldX + 5, shieldY + 1, shieldX + 7, shieldY + 10, withAlpha(trimColor, 105));
        guiGraphics.fill(shieldX + 2, shieldY + 5, shieldX + 10, shieldY + 6, withAlpha(trimColor, 85));

        guiGraphics.fill(shieldX + 2, shieldY + 6, shieldX + 4, shieldY + 10, iconColor);
        guiGraphics.fill(shieldX + 5, shieldY + 4, shieldX + 7, shieldY + 10, iconColor);
        guiGraphics.fill(shieldX + 8, shieldY + 2, shieldX + 10, shieldY + 10, iconColor);
        guiGraphics.fill(shieldX + 2, shieldY + 10, shieldX + 10, shieldY + 11, withAlpha(iconColor, 210));

        guiGraphics.fill(x + 6, y + 20, x + 20, y + 21, trimColor);
        guiGraphics.fill(x + 9, y + 21, x + 17, y + 22, shieldFillColor);
        guiGraphics.fill(x + 7, y + 19, x + 8, y + 22, withAlpha(trimColor, 185));
        guiGraphics.fill(x + 18, y + 19, x + 19, y + 22, withAlpha(trimColor, 185));
    }

    private static void drawStud(GuiGraphics guiGraphics, int x, int y, int studColor, int shadowColor) {
        guiGraphics.fill(x, y, x + 2, y + 2, shadowColor);
        guiGraphics.fill(x, y, x + 1, y + 1, studColor);
    }

    private static void drawOutline(GuiGraphics guiGraphics, int x, int y, int width, int height, int color) {
        guiGraphics.hLine(x, x + width, y, color);
        guiGraphics.hLine(x, x + width, y + height, color);
        guiGraphics.vLine(x, y, y + height, color);
        guiGraphics.vLine(x + width, y, y + height, color);
    }

    private static int withAlpha(int color, int alpha) {
        return ((alpha & 0xFF) << 24) | (color & 0x00FFFFFF);
    }

    private static float getHudTime() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.player != null ? minecraft.player.tickCount : (System.currentTimeMillis() / 50.0F);
    }
}
