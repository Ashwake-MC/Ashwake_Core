package com.ashwake.core.client;

import com.ashwake.core.AshwakeCore;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.world.inventory.Slot;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ContainerScreenEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;

@EventBusSubscriber(modid = AshwakeCore.MOD_ID, value = Dist.CLIENT)
public final class RpgInventoryOverlay {
    private static final int OUTER_LEFT_MARGIN = 14;
    private static final int OUTER_RIGHT_MARGIN = 42;
    private static final int OUTER_TOP_MARGIN = 16;
    private static final int OUTER_BOTTOM_MARGIN = 16;
    private static final int SLOT_SIZE = 16;
    private static final int SLOT_FRAME_SIZE = 18;
    private static final int SIDE_PANEL_WIDTH = 28;
    private static final int SIDE_PANEL_HEIGHT = 150;
    private static final int RECIPE_BOOK_WIDTH = 28;
    private static final int RECIPE_BOOK_HEIGHT = 24;
    private static boolean customRenderInProgress;

    private RpgInventoryOverlay() {
    }

    @SubscribeEvent
    public static void onRenderPre(ScreenEvent.Render.Pre event) {
        if (!(event.getScreen() instanceof InventoryScreen screen) || customRenderInProgress) {
            return;
        }

        event.setCanceled(true);
        renderCustomInventory(screen, event.getGuiGraphics(), event.getMouseX(), event.getMouseY(), event.getPartialTick());
    }

    private static void renderCustomInventory(
            InventoryScreen screen,
            GuiGraphics guiGraphics,
            int mouseX,
            int mouseY,
            float partialTick
    ) {
        int left = screen.getGuiLeft();
        int top = screen.getGuiTop();
        int width = screen.getXSize();
        int height = screen.getYSize();
        float time = getHudTime() + partialTick;

        // Draw backdrop and plaque outside the screen bounds
        drawBackdrop(guiGraphics, screen.width, screen.height, left, top, width, height, time);
        drawOuterPlaque(guiGraphics, left, top, width, height, time);

        try {
            customRenderInProgress = true;
            screen.render(guiGraphics, mouseX, mouseY, partialTick);
        } finally {
            customRenderInProgress = false;
        }
    }

    @SubscribeEvent
    public static void onContainerForeground(ContainerScreenEvent.Render.Foreground event) {
        if (!(event.getContainerScreen() instanceof InventoryScreen screen)) {
            return;
        }

        GuiGraphics guiGraphics = event.getGuiGraphics();
        Minecraft minecraft = Minecraft.getInstance();
        int width = screen.getXSize();
        int height = screen.getYSize();
        float time = getHudTime();

        // These now draw over the vanilla background because Foreground event is after renderBg
        drawInventoryBody(guiGraphics, 0, 0, width, height, time);
        drawSideRelicPanel(guiGraphics, 0, 0, time);
        drawSectionPanels(guiGraphics, 0, 0, time);
        drawSlotBeds(guiGraphics, screen, 0, 0, time);
        drawCraftArrow(guiGraphics, 0, 0, time);
        drawRecipeBookBed(guiGraphics, screen, time);

        if (minecraft.player != null) {
            drawPortraitGlow(guiGraphics, 0, 0, time);
            InventoryScreen.renderEntityInInventoryFollowsMouse(
                    guiGraphics,
                    26,
                    8,
                    75,
                    78,
                    30,
                    0.0625F,
                    (float) event.getMouseX() - screen.getGuiLeft(),
                    (float) event.getMouseY() - screen.getGuiTop(),
                    minecraft.player
            );
        }

        drawForegroundDetails(guiGraphics, screen, time);
        drawSlotOverlays(guiGraphics, screen, event.getMouseX(), event.getMouseY(), time);
    }

    private static void drawBackdrop(
            GuiGraphics guiGraphics,
            int screenWidth,
            int screenHeight,
            int left,
            int top,
            int width,
            int height,
            float time
    ) {
        guiGraphics.fillGradient(0, 0, screenWidth, screenHeight, color(0x62140F0B), color(0xB0060404));
        guiGraphics.fillGradient(0, 0, screenWidth, 26, withAlpha(color(0xFF24160F), 150), 0x00000000);
        guiGraphics.fillGradient(0, screenHeight - 40, screenWidth, screenHeight, 0x00000000, withAlpha(color(0xFF080505), 190));

        int centerX = left + width / 2;
        int centerY = top + height / 2;
        for (int index = 0; index < 12; index++) {
            float phase = time * (0.015F + index * 0.0012F) + index * 0.91F;
            int driftX = Math.round((float) Math.sin(phase) * (88 + index * 4));
            int driftY = Math.round((float) Math.cos(phase * 0.87F) * (54 + index * 3));
            int size = index % 3 == 0 ? 2 : 1;
            int alpha = 18 + (int) ((Math.sin(phase * 1.3F) * 0.5F + 0.5F) * 28.0F);
            int color = index % 2 == 0 ? color(0xFFD8C98C) : color(0xFF9CB06E);

            int x = centerX + driftX;
            int y = centerY + driftY;
            guiGraphics.fill(x, y, x + size, y + size, withAlpha(color, alpha));
        }
    }

    private static void drawOuterPlaque(GuiGraphics guiGraphics, int left, int top, int width, int height, float time) {
        int barkEdge = color(0xFF1B120D);
        int bark = color(0xFF4D3425);
        int barkDark = color(0xFF251913);
        int panel = color(0xFF120D0A);
        int trim = color(0xFF6E5532);
        int warmGlow = withAlpha(color(0xFFE0D098), 34 + (int) ((Math.sin(time * 0.13F) * 0.5F + 0.5F) * 30.0F));

        int x = left - OUTER_LEFT_MARGIN;
        int y = top - OUTER_TOP_MARGIN;
        int totalWidth = width + OUTER_LEFT_MARGIN + OUTER_RIGHT_MARGIN;
        int totalHeight = height + OUTER_TOP_MARGIN + OUTER_BOTTOM_MARGIN;

        guiGraphics.fill(x + 6, y + 6, x + totalWidth + 6, y + totalHeight + 7, color(0x5A000000));
        guiGraphics.fill(x + 12, y + totalHeight - 1, x + totalWidth - 16, y + totalHeight + 3, warmGlow);
        fillPlaque(guiGraphics, x, y, totalWidth, totalHeight, barkEdge, bark, barkDark, panel);
        drawOutline(guiGraphics, x + 8, y + 5, totalWidth - 16, totalHeight - 10, trim);
        drawCornerCap(guiGraphics, x + 10, y + 8, false, time);
        drawCornerCap(guiGraphics, x + totalWidth - 28, y + 8, true, time);
        drawCornerCap(guiGraphics, x + 10, y + totalHeight - 20, false, time + 5.0F);
        drawCornerCap(guiGraphics, x + totalWidth - 28, y + totalHeight - 20, true, time + 5.0F);
    }

    private static void drawInventoryBody(GuiGraphics guiGraphics, int x, int y, int width, int height, float time) {
        int barkEdge = color(0xFF1A120D);
        int bark = color(0xFF513727);
        int barkDark = color(0xFF281B14);
        int panel = color(0xFF18110D);
        int trim = color(0xFF7A6037);

        // Opaque base fill to fully suppress vanilla background and any white line artifacts
        guiGraphics.fill(x, y, x + width, y + height, barkEdge);
        fillPlaque(guiGraphics, x, y, width, height, barkEdge, bark, barkDark, panel);
        drawOutline(guiGraphics, x + 5, y + 4, width - 10, height - 8, trim);
        guiGraphics.fillGradient(x + 12, y + 8, x + width - 12, y + 28, withAlpha(color(0xFFAE8E5F), 48), 0x00000000);
        guiGraphics.fillGradient(x + 12, y + height - 22, x + width - 12, y + height - 8, 0x00000000, withAlpha(color(0xFF050403), 96));

        int shimmerX = x + 18 + Math.round(((float) Math.sin(time * 0.08F) * 0.5F + 0.5F) * (width - 56));
        guiGraphics.fill(shimmerX, y + 12, shimmerX + 12, y + 13, withAlpha(color(0xFFF3E7B9), 68));
    }

    private static void drawSideRelicPanel(GuiGraphics guiGraphics, int offsetX, int offsetY, float time) {
        int x = offsetX + 176;
        int y = offsetY + 8;
        int panelEdge = color(0xFF1E140F);
        int panelTop = color(0xFF3D2A1F);
        int panelBottom = color(0xFF17110D);
        int recess = color(0xFF0C0908);
        int trim = color(0xFF7A5E35);
        int leaf = withAlpha(color(0xFF94B470), 56 + (int) ((Math.sin(time * 0.11F) * 0.5F + 0.5F) * 34.0F));

        drawInsetPanel(guiGraphics, x, y, SIDE_PANEL_WIDTH, SIDE_PANEL_HEIGHT, panelEdge, panelTop, panelBottom, recess, trim);
        guiGraphics.fill(x + 7, y + 20, x + 21, y + 21, leaf);
        guiGraphics.fill(x + 7, y + SIDE_PANEL_HEIGHT - 22, x + 21, y + SIDE_PANEL_HEIGHT - 21, leaf);
        drawVineGlyph(guiGraphics, x + 11, y + 33, time);
        drawVineGlyph(guiGraphics, x + 11, y + 104, time + 4.0F);
    }

    private static void drawSectionPanels(GuiGraphics guiGraphics, int x, int y, float time) {
        int panelEdge = color(0xFF20160F);
        int panelTop = color(0xFF3D2C21);
        int panelBottom = color(0xFF16110D);
        int recess = color(0xFF0D0A08);
        int trim = color(0xFF7A5E36);
        int gold = withAlpha(color(0xFFF1E1A7), 34 + (int) ((Math.sin(time * 0.16F) * 0.5F + 0.5F) * 30.0F));
        int runeColor = withAlpha(color(0xFFD2BF84), 48 + (int) ((Math.sin(time * 0.12F) * 0.5F + 0.5F) * 40.0F));
        int runeShade = color(0xFF6A5230);

        drawInsetPanel(guiGraphics, x + 4, y + 4, 22, 76, panelEdge, panelTop, panelBottom, recess, trim); // Armor
        drawInsetPanel(guiGraphics, x + 27, y + 6, 50, 74, panelEdge, panelTop, panelBottom, recess, trim); // Character
        drawInsetPanel(guiGraphics, x + 82, y + 6, 88, 50, panelEdge, panelTop, panelBottom, recess, trim); // Crafting
        drawInsetPanel(guiGraphics, x + 74, y + 58, 22, 22, panelEdge, panelTop, panelBottom, recess, trim); // Offhand
        drawInsetPanel(guiGraphics, x + 6, y + 82, 164, 78, panelEdge, panelTop, panelBottom, recess, trim); // Inventory

        guiGraphics.fill(x + 32, y + 17, x + 72, y + 18, withAlpha(trim, 160));
        guiGraphics.fill(x + 88, y + 17, x + 166, y + 18, withAlpha(trim, 160));
        guiGraphics.fill(x + 9, y + 90, x + 167, y + 91, gold);
        guiGraphics.fill(x + 80, y + 63, x + 90, y + 64, withAlpha(trim, 150));

        drawShimmer(guiGraphics, x + 6, y + 82, 164, 4, time * 0.8F);
        drawShimmer(guiGraphics, x + 82, y + 6, 88, 3, time * 1.2F);
        
        drawRune(guiGraphics, x + 10, y + 148, runeColor, runeShade);
        drawRune(guiGraphics, x + 160, y + 148, runeColor, runeShade);
    }

    private static void drawPortraitGlow(GuiGraphics guiGraphics, int x, int y, float time) {
        int glow = withAlpha(color(0xFFE8D79D), 22 + (int) ((Math.sin(time * 0.15F) * 0.5F + 0.5F) * 26.0F));
        guiGraphics.fill(x + 35, y + 18, x + 69, y + 72, glow);
        guiGraphics.fill(x + 38, y + 22, x + 66, y + 68, withAlpha(color(0xFF5D6B38), 22));
    }

    private static void drawSlotBeds(GuiGraphics guiGraphics, InventoryScreen screen, int offsetX, int offsetY, float time) {
        int slotOuter = color(0xFF1C140E);
        int slotFrame = color(0xFF7A5F38);
        int slotShade = color(0xFF281D16);
        int slotInnerTop = color(0xFF3A2A1E);
        int slotInnerBottom = color(0xFF100C09);
        int slotGlow = withAlpha(color(0xFFD9CC94), 16 + (int) ((Math.sin(time * 0.09F) * 0.5F + 0.5F) * 18.0F));

        for (Slot slot : screen.getMenu().slots) {
            if (!slot.isActive()) {
                continue;
            }

            int x = offsetX + slot.x - 1;
            int y = offsetY + slot.y - 1;

            boolean isArmor = slot.index >= 5 && slot.index <= 8;
            boolean isOffhand = slot.index == 45;
            boolean isEmpty = !slot.hasItem();

            int top = slotInnerTop;
            int bottom = slotInnerBottom;
            int frame = slotFrame;

            if ((isArmor || isOffhand)) {
                top = color(0xFF4A3A2F);
                frame = color(0xFF9E8454); // More golden frame for gear slots
            }

            guiGraphics.fill(x - 1, y - 1, x + SLOT_FRAME_SIZE + 1, y + SLOT_FRAME_SIZE + 1, slotOuter);
            guiGraphics.fill(x, y, x + SLOT_FRAME_SIZE, y + SLOT_FRAME_SIZE, frame);
            guiGraphics.fill(x + 1, y + 1, x + SLOT_FRAME_SIZE - 1, y + SLOT_FRAME_SIZE - 1, slotShade);
            guiGraphics.fillGradient(x + 2, y + 2, x + SLOT_FRAME_SIZE - 2, y + SLOT_FRAME_SIZE - 2, top, bottom);
            
            if ((isArmor || isOffhand) && isEmpty) {
                // Subtle golden highlight to make the empty icon pop more
                guiGraphics.fill(x + 2, y + 2, x + SLOT_FRAME_SIZE - 2, y + SLOT_FRAME_SIZE - 2, withAlpha(color(0xFFD4C38A), 25));
            }

            guiGraphics.fill(x + 3, y + 3, x + SLOT_FRAME_SIZE - 3, y + 4, slotGlow);
            guiGraphics.fill(x + 3, y + SLOT_FRAME_SIZE - 4, x + SLOT_FRAME_SIZE - 3, y + SLOT_FRAME_SIZE - 3, withAlpha(color(0xFF000000), 40));
        }
    }

    private static void drawCraftArrow(GuiGraphics guiGraphics, int x, int y, float time) {
        int arrowX = x + 136;
        int arrowY = y + 28;
        int arrow = color(0xFFD0BC82);
        int arrowShade = color(0xFF6D552E);
        int sweep = Math.round(((float) Math.sin(time * 0.18F) * 0.5F + 0.5F) * 6.0F);

        guiGraphics.fill(arrowX, arrowY + 4, arrowX + 10, arrowY + 6, arrowShade);
        guiGraphics.fill(arrowX + 1, arrowY + 3, arrowX + 11, arrowY + 5, arrow);
        guiGraphics.fill(arrowX + 6, arrowY, arrowX + 8, arrowY + 8, arrow);
        guiGraphics.fill(arrowX + 8, arrowY + 1, arrowX + 13, arrowY + 4, arrow);
        guiGraphics.fill(arrowX + 8, arrowY + 4, arrowX + 13, arrowY + 7, arrow);
        guiGraphics.fill(arrowX + 1 + sweep, arrowY + 3, arrowX + 4 + sweep, arrowY + 4, withAlpha(color(0xFFF8EDC8), 90));
    }

    private static void drawRecipeBookBed(GuiGraphics guiGraphics, InventoryScreen screen, float time) {
        int x = 100;
        int y = screen.getYSize() / 2 - 26;
        int panelEdge = color(0xFF1F150F);
        int panelTop = color(0xFF473124);
        int panelBottom = color(0xFF1B130F);
        int recess = color(0xFF100C09);
        int trim = color(0xFF86663A);
        int glow = withAlpha(color(0xFFD8CA91), 22 + (int) ((Math.sin(time * 0.14F) * 0.5F + 0.5F) * 22.0F));

        drawInsetPanel(guiGraphics, x, y, RECIPE_BOOK_WIDTH, RECIPE_BOOK_HEIGHT, panelEdge, panelTop, panelBottom, recess, trim);
        guiGraphics.fill(x + 5, y + 4, x + RECIPE_BOOK_WIDTH - 5, y + 5, glow);
    }

    private static void drawForegroundDetails(GuiGraphics guiGraphics, InventoryScreen screen, float time) {
        Font font = Minecraft.getInstance().font;

        drawHeaderRibbon(guiGraphics, 89, 4, 76, time);
        drawLabel(guiGraphics, font, "CRAFT", 103, 7, color(0xFFF0E8CC), 0.82F);
        drawLabel(guiGraphics, font, "GEAR", 8, 8, color(0xFFDAC88D), 0.72F);
        drawLabel(guiGraphics, font, "PACK", 9, 83, color(0xFFE9DEB2), 0.8F);
        drawLabel(guiGraphics, font, "WARD", 76, 62, color(0xFFD4C38A), 0.62F);

        int shimmerX = 10 + Math.round(((float) Math.sin(time * 0.07F) * 0.5F + 0.5F) * 134.0F);
        guiGraphics.fill(shimmerX, 91, shimmerX + 16, 92, withAlpha(color(0xFFF1E6BA), 86));
        guiGraphics.fill(32, 18, 72, 19, withAlpha(color(0xFFAB8B54), 130));
        guiGraphics.fill(87, 18, 166, 19, withAlpha(color(0xFFAB8B54), 130));
        drawMiniDivider(guiGraphics, 13, 25, 4, time);
        drawMiniDivider(guiGraphics, 13, 59, 4, time + 3.0F);
    }

    private static void drawSlotOverlays(GuiGraphics guiGraphics, InventoryScreen screen, int mouseX, int mouseY, float time) {
        Slot hoveredSlot = screen.getSlotUnderMouse();
        int localMouseX = mouseX - screen.getGuiLeft();
        int localMouseY = mouseY - screen.getGuiTop();

        for (Slot slot : screen.getMenu().slots) {
            if (!slot.isActive()) {
                continue;
            }

            boolean hovered = hoveredSlot == slot || isHoveringSlot(slot, localMouseX, localMouseY);
            drawSlotFrame(guiGraphics, slot.x - 1, slot.y - 1, hovered, time);
        }
    }

    private static void drawHeaderRibbon(GuiGraphics guiGraphics, int x, int y, int width, float time) {
        int barkEdge = color(0xFF1C130E);
        int bark = color(0xFF543929);
        int barkDark = color(0xFF261A13);
        int trim = color(0xFF8A6C3E);
        int sweep = 5 + Math.round(((float) Math.sin(time * 0.09F) * 0.5F + 0.5F) * (width - 18));

        guiGraphics.fill(x - 1, y - 1, x + width + 1, y + 13, barkEdge);
        guiGraphics.fill(x, y, x + width, y + 12, bark);
        guiGraphics.fillGradient(x + 1, y + 1, x + width - 1, y + 11, color(0xFF705036), barkDark);
        guiGraphics.fill(x + 3, y + 2, x + width - 3, y + 3, trim);
        guiGraphics.fill(x + sweep, y + 3, x + sweep + 10, y + 4, withAlpha(color(0xFFF7EBBE), 92));
    }

    private static void drawSlotFrame(GuiGraphics guiGraphics, int x, int y, boolean hovered, float time) {
        int outer = hovered ? color(0xFFC2AA70) : color(0xFF291D15);
        int inner = hovered ? color(0xFFE3D39A) : color(0xFF705733);
        int glowAlpha = hovered
                ? 84 + (int) ((Math.sin(time * 0.32F) * 0.5F + 0.5F) * 66.0F)
                : 18 + (int) ((Math.sin(time * 0.08F) * 0.5F + 0.5F) * 10.0F);
        int glow = withAlpha(color(0xFFF3E9C0), glowAlpha);

        guiGraphics.fill(x - 1, y - 1, x + SLOT_FRAME_SIZE + 1, y + SLOT_FRAME_SIZE + 1, outer);
        guiGraphics.fill(x, y, x + SLOT_FRAME_SIZE, y + SLOT_FRAME_SIZE, inner);
        guiGraphics.fill(x + 2, y + 2, x + SLOT_FRAME_SIZE - 2, y + 3, glow);

        if (hovered) {
            guiGraphics.fill(x - 2, y - 2, x + SLOT_FRAME_SIZE + 2, y - 1, withAlpha(color(0xFFFFF1C8), 120));
            guiGraphics.fill(x - 2, y + SLOT_FRAME_SIZE + 1, x + SLOT_FRAME_SIZE + 2, y + SLOT_FRAME_SIZE + 2, withAlpha(color(0xFFD8C98A), 72));
        }
    }

    private static boolean isHoveringSlot(Slot slot, int mouseX, int mouseY) {
        return mouseX >= slot.x - 1
                && mouseX < slot.x + SLOT_SIZE + 1
                && mouseY >= slot.y - 1
                && mouseY < slot.y + SLOT_SIZE + 1;
    }

    private static void drawInsetPanel(
            GuiGraphics guiGraphics,
            int x,
            int y,
            int width,
            int height,
            int edge,
            int top,
            int bottom,
            int recess,
            int trim
    ) {
        guiGraphics.fill(x - 1, y - 1, x + width + 1, y + height + 1, edge);
        guiGraphics.fillGradient(x, y, x + width, y + height, top, bottom);
        guiGraphics.fill(x + 2, y + 2, x + width - 2, y + height - 2, recess);
        guiGraphics.fill(x + 3, y + 3, x + width - 3, y + 4, withAlpha(trim, 180));
        drawOutline(guiGraphics, x, y, width, height, trim);
    }

    private static void drawCornerCap(GuiGraphics guiGraphics, int x, int y, boolean flip, float time) {
        int gold = color(0xFFD2BF84);
        int shade = color(0xFF6A5230);
        int leaf = withAlpha(color(0xFF8BAA67), 36 + (int) ((Math.sin(time * 0.14F) * 0.5F + 0.5F) * 26.0F));

        if (flip) {
            guiGraphics.fill(x + 4, y, x + 10, y + 1, shade);
            guiGraphics.fill(x + 2, y + 1, x + 10, y + 2, gold);
            guiGraphics.fill(x, y + 2, x + 8, y + 3, gold);
            guiGraphics.fill(x + 6, y + 3, x + 8, y + 7, leaf);
        } else {
            guiGraphics.fill(x, y, x + 6, y + 1, shade);
            guiGraphics.fill(x, y + 1, x + 8, y + 2, gold);
            guiGraphics.fill(x + 2, y + 2, x + 10, y + 3, gold);
            guiGraphics.fill(x + 2, y + 3, x + 4, y + 7, leaf);
        }
    }

    private static void drawVineGlyph(GuiGraphics guiGraphics, int x, int y, float time) {
        int vine = withAlpha(color(0xFF88A968), 48 + (int) ((Math.sin(time * 0.2F) * 0.5F + 0.5F) * 30.0F));
        int gold = color(0xFFD3C084);

        guiGraphics.fill(x + 2, y, x + 4, y + 10, vine);
        guiGraphics.fill(x, y + 2, x + 2, y + 4, gold);
        guiGraphics.fill(x + 4, y + 5, x + 6, y + 7, gold);
        guiGraphics.fill(x, y + 8, x + 2, y + 10, gold);
    }

    private static void drawRune(GuiGraphics guiGraphics, int x, int y, int main, int shade) {
        guiGraphics.fill(x + 1, y, x + 5, y + 1, shade);
        guiGraphics.fill(x, y + 1, x + 6, y + 3, main);
        guiGraphics.fill(x + 1, y + 3, x + 5, y + 4, shade);
    }

    private static void drawShimmer(GuiGraphics guiGraphics, int x, int y, int width, int height, float time) {
        if (width < 5 || height <= 0) {
            return;
        }

        int shimmerWidth = Math.max(4, width / 4);
        int travel = width + shimmerWidth;
        int offset = Math.round(((time * 0.055F) % 1.0F) * travel) - shimmerWidth;
        int start = Math.max(x, x + offset);
        int end = Math.min(x + width, x + offset + shimmerWidth);
        if (end <= start) {
            return;
        }

        guiGraphics.fillGradient(start, y, end, y + height, color(0x40FFF8DC), color(0x08FFF8DC));
    }

    private static void drawMiniDivider(GuiGraphics guiGraphics, int x, int y, int height, float time) {
        int main = color(0xFFD3C084);
        int glow = withAlpha(color(0xFFF6EBBA), 32 + (int) ((Math.sin(time * 0.22F) * 0.5F + 0.5F) * 28.0F));

        guiGraphics.fill(x, y, x + 1, y + height, main);
        guiGraphics.fill(x + 1, y + 1, x + 2, y + height - 1, glow);
    }

    private static void drawLabel(GuiGraphics guiGraphics, Font font, String text, int x, int y, int color, float scale) {
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(x, y, 0.0F);
        guiGraphics.pose().scale(scale, scale, 1.0F);
        guiGraphics.drawString(font, text, 0, 0, color, false);
        guiGraphics.pose().popPose();
    }

    private static void fillPlaque(
            GuiGraphics guiGraphics,
            int x,
            int y,
            int width,
            int height,
            int barkEdge,
            int bark,
            int barkDark,
            int panel
    ) {
        guiGraphics.fill(x + 7, y, x + width - 7, y + height, barkEdge);
        guiGraphics.fill(x + 3, y + 4, x + 7, y + height - 4, barkEdge);
        guiGraphics.fill(x + width - 7, y + 4, x + width - 3, y + height - 4, barkEdge);
        guiGraphics.fill(x + 8, y + 1, x + width - 8, y + height - 1, bark);
        guiGraphics.fill(x + 4, y + 5, x + 8, y + height - 5, bark);
        guiGraphics.fill(x + width - 8, y + 5, x + width - 4, y + height - 5, bark);
        guiGraphics.fillGradient(x + 9, y + 2, x + width - 9, y + height - 2, color(0xFF65472F), barkDark);
        guiGraphics.fill(x + 11, y + 4, x + width - 11, y + height - 4, panel);
    }

    private static void drawOutline(GuiGraphics guiGraphics, int x, int y, int width, int height, int color) {
        guiGraphics.hLine(x, x + width - 1, y, color);
        guiGraphics.hLine(x, x + width - 1, y + height - 1, color);
        guiGraphics.vLine(x, y, y + height - 1, color);
        guiGraphics.vLine(x + width - 1, y, y + height - 1, color);
    }

    private static float getHudTime() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.player != null ? minecraft.player.tickCount : (System.currentTimeMillis() / 50.0F);
    }

    private static int withAlpha(int color, int alpha) {
        return ((alpha & 0xFF) << 24) | (color & 0x00FFFFFF);
    }

    private static int color(int rgb) {
        return (rgb & 0xFF000000) == 0 && (rgb & 0x00FFFFFF) != 0
                ? 0xFF000000 | (rgb & 0x00FFFFFF)
                : rgb;
    }
}
