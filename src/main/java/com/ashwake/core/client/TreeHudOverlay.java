package com.ashwake.core.client;

import com.ashwake.core.AshwakeCore;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

@EventBusSubscriber(modid = AshwakeCore.MOD_ID, value = Dist.CLIENT)
public final class TreeHudOverlay {
    private static final int MIN_WIDTH = 144;
    private static final int HUD_HEIGHT = 22;
    private static final int BOTTOM_OFFSET = RpgHudOverlay.treeHudBottomOffset();
    private static final float TITLE_SCALE = 0.72F;
    private static final float HP_SCALE = 0.68F;

    private TreeHudOverlay() {
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        ClientTreeHudState.tick();
    }

    @SubscribeEvent
    public static void onRender(RenderGuiEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientTreeHudState.TreeHudData hud = ClientTreeHudState.get();
        if (hud == null || minecraft.player == null || minecraft.options.hideGui) {
            return;
        }

        GuiGraphics guiGraphics = event.getGuiGraphics();
        Font font = minecraft.font;
        Component title = Component.translatable(hud.descriptionKey());
        String hpText = hud.currentHits() + " / " + hud.maxHits() + " HP";

        int screenWidth = minecraft.getWindow().getGuiScaledWidth();
        int screenHeight = minecraft.getWindow().getGuiScaledHeight();
        int titleWidth = scaledWidth(font, title, TITLE_SCALE);
        int hpWidth = scaledWidth(font, hpText, HP_SCALE);
        int width = Math.min(screenWidth - 20, Math.max(MIN_WIDTH, titleWidth + hpWidth + 44));
        float intro = hud.introProgress();
        float hitPulse = hud.hitPulse();
        float alpha = hud.alpha();
        float age = hud.ageSeconds();
        float idleDrift = (float) Math.sin(age * 2.8F) * 0.8F;
        float scale = 0.95F + (0.05F * intro) + (0.015F * hitPulse);
        float slideY = (1.0F - intro) * 10.0F;
        int y = screenHeight - BOTTOM_OFFSET - HUD_HEIGHT - Math.round(slideY - idleDrift);

        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(screenWidth / 2.0F, y + (HUD_HEIGHT / 2.0F), 0.0F);
        guiGraphics.pose().scale(scale, scale, 1.0F);
        guiGraphics.pose().translate(-(width / 2.0F), -(HUD_HEIGHT / 2.0F), 0.0F);
        drawHud(guiGraphics, font, 0, 0, width, title, hpText, hud.progress(), alpha, hitPulse, hud.shimmerPhase(), age);
        guiGraphics.pose().popPose();
    }

    private static void drawHud(
            GuiGraphics guiGraphics,
            Font font,
            int x,
            int y,
            int width,
            Component title,
            String hpText,
            float progress,
            float alpha,
            float hitPulse,
            float shimmerPhase,
            float ageSeconds
    ) {
        int shadow = color(0x54000000, alpha);
        int barkEdge = color(0xFF1B120D, alpha);
        int bark = color(0xFF4F3424, alpha);
        int barkDeep = color(0xFF281A13, alpha);
        int barkPanel = color(0xFF1A140F, alpha);
        int moss = color(0xFF6E9353, alpha);
        int mossDark = color(0xFF304621, alpha);
        int gold = color(0xFFD5BC7A, alpha);
        int goldShade = color(0xFF6E5430, alpha);
        int textMain = color(0xFFF4E9CC, alpha);
        int textDim = color(0xFFD9CAAC, alpha);
        int barBg = color(0xFF0D0B08, alpha);
        int barFrame = color(0xFF3E2A1D, alpha);
        int pulseGlow = color(0x5090D47A, alpha * (0.35F + (hitPulse * 0.55F)));

        int fillStart;
        int fillEnd;
        if (progress <= 0.25F) {
            fillStart = color(0xFFC44C3C, alpha);
            fillEnd = color(0xFF6F1E18, alpha);
        } else if (progress <= 0.6F) {
            fillStart = color(0xFFD59A45, alpha);
            fillEnd = color(0xFF885117, alpha);
        } else {
            fillStart = color(0xFF6FA551, alpha);
            fillEnd = color(0xFF2E5B29, alpha);
        }

        guiGraphics.fill(x + 5, y + 3, x + width + 4, y + HUD_HEIGHT + 5, shadow);
        guiGraphics.fill(x + 10, y + HUD_HEIGHT - 4, x + width - 10, y + HUD_HEIGHT - 1, pulseGlow);

        fillPlaque(guiGraphics, x, y, width, barkEdge, bark, barkDeep, barkPanel);
        drawOutline(guiGraphics, x + 8, y + 3, width - 16, HUD_HEIGHT - 6, goldShade);
        guiGraphics.fillGradient(x + 10, y + 4, x + width - 10, y + 8, color(0x66B9A873, alpha), 0x00000000);
        guiGraphics.fill(x + 10, y + 4, x + width - 10, y + 5, mossDark);
        drawVineAccent(guiGraphics, x + 8, y + 3, moss, mossDark, false);
        drawVineAccent(guiGraphics, x + width - 18, y + 3, moss, mossDark, true);
        drawEmblemRing(guiGraphics, x + 8, y + 4, alpha, ageSeconds, hitPulse);

        int titleX = x + 31;
        int titleY = y + 4;
        int hpX = x + width - 9 - scaledWidth(font, hpText, HP_SCALE);
        drawScaledText(guiGraphics, font, title, titleX, titleY, textMain, TITLE_SCALE);
        drawScaledText(guiGraphics, font, hpText, hpX, titleY + 1, textDim, HP_SCALE);

        int barX = titleX;
        int barY = y + 14;
        int barWidth = width - 41;
        int barHeight = 4;
        guiGraphics.fill(barX - 1, barY - 1, barX + barWidth + 1, barY + barHeight + 1, barkEdge);
        guiGraphics.fill(barX, barY, barX + barWidth, barY + barHeight, barFrame);
        guiGraphics.fill(barX + 1, barY + 1, barX + barWidth - 1, barY + barHeight - 1, barBg);

        int fillWidth = Math.max(0, Math.round((barWidth - 2) * progress));
        if (fillWidth > 0) {
            guiGraphics.fillGradient(barX + 1, barY + 1, barX + 1 + fillWidth, barY + barHeight - 1, fillStart, fillEnd);
            guiGraphics.fillGradient(
                    barX + 1,
                    barY + 1,
                    barX + 1 + fillWidth,
                    barY + 2,
                    color(0x66FFF1C5, alpha),
                    0x00000000
            );
            drawShimmer(guiGraphics, barX + 1, barY + 1, fillWidth, barHeight - 2, shimmerPhase, alpha);
        }

        guiGraphics.fill(barX - 4, barY + 1, barX - 2, barY + barHeight - 1, goldShade);
        guiGraphics.fill(barX + barWidth + 2, barY + 1, barX + barWidth + 4, barY + barHeight - 1, goldShade);
        drawDiamond(guiGraphics, barX - 8, barY + 1, gold, goldShade);
        drawDiamond(guiGraphics, barX + barWidth + 5, barY + 1, gold, goldShade);
    }

    private static void fillPlaque(GuiGraphics guiGraphics, int x, int y, int width, int barkEdge, int bark, int barkDeep, int barkPanel) {
        guiGraphics.fill(x + 8, y, x + width - 8, y + HUD_HEIGHT, barkEdge);
        guiGraphics.fill(x + 4, y + 4, x + 8, y + HUD_HEIGHT - 4, barkEdge);
        guiGraphics.fill(x + width - 8, y + 4, x + width - 4, y + HUD_HEIGHT - 4, barkEdge);
        guiGraphics.fill(x + 9, y + 1, x + width - 9, y + HUD_HEIGHT - 1, bark);
        guiGraphics.fill(x + 5, y + 5, x + 9, y + HUD_HEIGHT - 5, bark);
        guiGraphics.fill(x + width - 9, y + 5, x + width - 5, y + HUD_HEIGHT - 5, bark);
        guiGraphics.fillGradient(x + 10, y + 2, x + width - 10, y + HUD_HEIGHT - 2, color(0xFF6A4A33, 1.0F), barkDeep);
        guiGraphics.fill(x + 12, y + 4, x + width - 12, y + HUD_HEIGHT - 4, barkPanel);
    }

    private static void drawEmblemRing(GuiGraphics guiGraphics, int x, int y, float alpha, float ageSeconds, float hitPulse) {
        int gold = color(0xFFD8C78A, alpha);
        int goldShade = color(0xFF6A5331, alpha);
        int bark = color(0xFF66442E, alpha);
        int leaf = color(0xFF7CA765, alpha);
        int leafDark = color(0xFF365228, alpha);
        int glow = color(0x55A7E48A, alpha * (0.45F + hitPulse * 0.35F));
        int sway = Math.round((float) Math.sin(ageSeconds * 4.0F) * 1.0F);

        guiGraphics.fill(x + 2, y + 2, x + 16, y + 16, goldShade);
        guiGraphics.fill(x + 3, y + 3, x + 15, y + 15, gold);
        guiGraphics.fill(x + 4, y + 4, x + 14, y + 14, bark);
        guiGraphics.fill(x + 4, y + 4, x + 14, y + 6, glow);

        guiGraphics.fill(x + 8, y + 8, x + 10, y + 14, color(0xFF2B1D13, alpha));
        guiGraphics.fill(x + 9, y + 7, x + 11, y + 14, bark);

        guiGraphics.fill(x + 6, y + 6 + sway, x + 14, y + 8 + sway, leafDark);
        guiGraphics.fill(x + 5, y + 7 + sway, x + 15, y + 10 + sway, leaf);
        guiGraphics.fill(x + 7, y + 5 + sway, x + 13, y + 7 + sway, leaf);
        guiGraphics.fill(x + 8, y + 4 + sway, x + 12, y + 6 + sway, leafDark);
        guiGraphics.fill(x + 7, y + 13, x + 9, y + 14, gold);
        guiGraphics.fill(x + 11, y + 13, x + 13, y + 14, gold);
    }

    private static void drawVineAccent(GuiGraphics guiGraphics, int x, int y, int leaf, int leafDark, boolean flipped) {
        int direction = flipped ? -1 : 1;
        int stemX = flipped ? x + 8 : x;

        fillRect(guiGraphics, stemX, y + 1, stemX + direction, y + 10, leafDark);
        fillRect(guiGraphics, stemX + direction, y + 2, stemX + direction * 3, y + 4, leaf);
        fillRect(guiGraphics, stemX + direction, y + 5, stemX + direction * 4, y + 7, leaf);
        fillRect(guiGraphics, stemX + direction, y + 8, stemX + direction * 3, y + 10, leaf);
    }

    private static void drawShimmer(
            GuiGraphics guiGraphics,
            int x,
            int y,
            int width,
            int height,
            float shimmerPhase,
            float alpha
    ) {
        if (width < 6 || height <= 0) {
            return;
        }

        int shimmerWidth = Math.max(4, width / 5);
        int travel = width + shimmerWidth;
        int offset = Math.round(shimmerPhase * travel) - shimmerWidth;
        int start = Math.max(x, x + offset);
        int end = Math.min(x + width, x + offset + shimmerWidth);
        if (end <= start) {
            return;
        }

        guiGraphics.fillGradient(
                start,
                y,
                end,
                y + height,
                color(0x58FFF7D9, alpha),
                color(0x10FFF7D9, alpha)
        );
    }

    private static void drawDiamond(GuiGraphics guiGraphics, int x, int y, int main, int shade) {
        guiGraphics.fill(x + 1, y, x + 3, y + 1, shade);
        guiGraphics.fill(x, y + 1, x + 4, y + 3, main);
        guiGraphics.fill(x + 1, y + 3, x + 3, y + 4, shade);
    }

    private static void fillRect(GuiGraphics guiGraphics, int left, int top, int right, int bottom, int color) {
        guiGraphics.fill(Math.min(left, right), Math.min(top, bottom), Math.max(left, right), Math.max(top, bottom), color);
    }

    private static void drawOutline(GuiGraphics guiGraphics, int x, int y, int width, int height, int color) {
        guiGraphics.hLine(x, x + width - 1, y, color);
        guiGraphics.hLine(x, x + width - 1, y + height - 1, color);
        guiGraphics.vLine(x, y, y + height - 1, color);
        guiGraphics.vLine(x + width - 1, y, y + height - 1, color);
    }

    private static int scaledWidth(Font font, Component text, float scale) {
        return Math.round(font.width(text) * scale);
    }

    private static int scaledWidth(Font font, String text, float scale) {
        return Math.round(font.width(text) * scale);
    }

    private static void drawScaledText(
            GuiGraphics guiGraphics,
            Font font,
            Component text,
            int x,
            int y,
            int color,
            float scale
    ) {
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(x, y, 0.0F);
        guiGraphics.pose().scale(scale, scale, 1.0F);
        guiGraphics.drawString(font, text, 0, 0, color, false);
        guiGraphics.pose().popPose();
    }

    private static void drawScaledText(
            GuiGraphics guiGraphics,
            Font font,
            String text,
            int x,
            int y,
            int color,
            float scale
    ) {
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(x, y, 0.0F);
        guiGraphics.pose().scale(scale, scale, 1.0F);
        guiGraphics.drawString(font, text, 0, 0, color, false);
        guiGraphics.pose().popPose();
    }

    private static int color(int rgb, float alpha) {
        int a = Math.max(0, Math.min(255, Math.round(((rgb >>> 24) & 0xFF) * alpha)));
        return (a << 24) | (rgb & 0x00FFFFFF);
    }
}
