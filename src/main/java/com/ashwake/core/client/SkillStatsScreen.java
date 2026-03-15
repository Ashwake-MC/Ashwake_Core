package com.ashwake.core.client;

import com.ashwake.core.skills.ClientSkillState;
import com.ashwake.core.skills.PlayerSkills;
import com.ashwake.core.skills.SkillProgress;
import com.ashwake.core.skills.SkillType;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class SkillStatsScreen extends Screen {
    private static final int PANEL_WIDTH = 382;
    private static final int PANEL_HEIGHT = 404;
    private static final int PANEL_PADDING = 10;
    private static final int SUMMARY_GAP = 6;
    private static final int SUMMARY_HEIGHT = 34;
    private static final int ROW_HEIGHT = 36;
    private static final int ROW_GAP = 5;

    private final Screen parent;

    public SkillStatsScreen(Screen parent) {
        super(Component.literal("Ashwake Skills"));
        this.parent = parent;
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        // Draw our own background tint instead of calling renderBackground
        guiGraphics.fillGradient(0, 0, this.width, this.height, 0x22160F0B, 0x55160F0B);

        ScreenLayout layout = createLayout();
        double localMouseX = (mouseX - layout.left()) / layout.scale();
        double localMouseY = (mouseY - layout.top()) / layout.scale();
        int innerWidth = PANEL_WIDTH - (PANEL_PADDING * 2);

        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(layout.left(), layout.top(), 0.0F);
        guiGraphics.pose().scale(layout.scale(), layout.scale(), 1.0F);

        int left = 0;
        int top = 0;
        drawPanel(guiGraphics, left, top);

        guiGraphics.drawString(this.font, this.title, left + 16, top + 11, 0xFFF5ECDF, false);
        guiGraphics.drawString(this.font, Component.literal("Perks unlock every 10 levels"), left + 16, top + 24, 0xFFD6C4AA, false);

        int totalLevel = 0;
        int gatheringLevel = 0;
        int combatLevel = 0;
        int totalPerks = 0;

        for (SkillType skill : SkillType.values()) {
            SkillProgress progress = ClientSkillState.get(skill);
            totalLevel += progress.level();
            totalPerks += skill.getPerksForLevel(progress.level());
            switch (skill) {
                case WOODCUTTING, MINING, FISHING, FARMING -> gatheringLevel += progress.level();
                case ATTACK, DEFENCE, ARCHER -> combatLevel += progress.level();
            }
        }

        int summaryY = top + 46;
        int summaryWidth = (innerWidth - (SUMMARY_GAP * 3)) / 4;
        drawSummaryCard(guiGraphics, left + PANEL_PADDING, summaryY, summaryWidth, Component.literal("Total"), Integer.toString(totalLevel), 0xFFB98952);
        drawSummaryCard(
                guiGraphics,
                left + PANEL_PADDING + summaryWidth + SUMMARY_GAP,
                summaryY,
                summaryWidth,
                Component.literal("Gathering"),
                Integer.toString(gatheringLevel),
                0xFF7E9B52
        );
        drawSummaryCard(
                guiGraphics,
                left + PANEL_PADDING + ((summaryWidth + SUMMARY_GAP) * 2),
                summaryY,
                summaryWidth,
                Component.literal("Combat"),
                Integer.toString(combatLevel),
                0xFFC96B3E
        );
        drawSummaryCard(
                guiGraphics,
                left + PANEL_PADDING + ((summaryWidth + SUMMARY_GAP) * 3),
                summaryY,
                summaryWidth,
                Component.literal("Perks"),
                Integer.toString(totalPerks),
                0xFF5F8B82
        );

        SkillType hoveredSkill = null;
        int rowX = left + PANEL_PADDING;
        int rowY = top + 92;
        int rowWidth = innerWidth;

        for (SkillType skill : SkillType.values()) {
            if (drawSkillRow(guiGraphics, rowX, rowY, rowWidth, localMouseX, localMouseY, skill)) {
                hoveredSkill = skill;
            }
            rowY += ROW_HEIGHT + ROW_GAP;
        }

        guiGraphics.drawCenteredString(
                this.font,
                Component.literal("Hover a skill for details. Press E or ESC to return."),
                left + (PANEL_WIDTH / 2),
                top + PANEL_HEIGHT - 18,
                0xFFD2C0AA
        );

        guiGraphics.pose().popPose();

        if (hoveredSkill != null) {
            SkillProgress progress = ClientSkillState.get(hoveredSkill);
            guiGraphics.renderComponentTooltip(this.font, buildTooltip(hoveredSkill, progress), mouseX, mouseY);
        }

        // We manually render widgets instead of calling super.render(guiGraphics, mouseX, mouseY, partialTick)
        // to avoid triggering some "Blur" mods that hook into the base Screen.render method.
        for (var renderable : this.renderables) {
            renderable.render(guiGraphics, mouseX, mouseY, partialTick);
        }
    }

    @Override
    public void renderBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        // Explicitly override to do nothing, preventing some mods from applying blur effects
    }

    // Compatibility methods for various "Blur" mods to disable their effects on this screen
    public boolean blur() { return false; }
    public float getBlurAmount() { return 0.0F; }
    public int getBlurRadius() { return 0; }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (this.minecraft != null && this.minecraft.options.keyInventory.matches(keyCode, scanCode)) {
            this.onClose();
            return true;
        }

        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) {
            this.minecraft.setScreen(this.parent);
        }
    }

    private void drawPanel(GuiGraphics guiGraphics, int left, int top) {
        guiGraphics.fillGradient(left, top, left + PANEL_WIDTH, top + PANEL_HEIGHT, 0xF128211A, 0xF11C1611);
        guiGraphics.fill(left + 3, top + 3, left + PANEL_WIDTH - 3, top + PANEL_HEIGHT - 3, 0xF2CDBEA8);
        guiGraphics.fill(left + 6, top + 6, left + PANEL_WIDTH - 6, top + PANEL_HEIGHT - 6, 0xF4352C23);
        guiGraphics.fillGradient(left + 6, top + 6, left + PANEL_WIDTH - 6, top + 36, 0xFF8E7657, 0xFF66523E);
        guiGraphics.fill(left + 6, top + PANEL_HEIGHT - 34, left + PANEL_WIDTH - 6, top + PANEL_HEIGHT - 6, 0xFF2D251F);
        drawOutline(guiGraphics, left, top, PANEL_WIDTH, PANEL_HEIGHT, 0xFF1A140F);
        drawOutline(guiGraphics, left + 3, top + 3, PANEL_WIDTH - 6, PANEL_HEIGHT - 6, 0xFF554637);
    }

    private void drawSummaryCard(GuiGraphics guiGraphics, int x, int y, int width, Component label, String value, int accentColor) {
        guiGraphics.fill(x, y, x + width, y + SUMMARY_HEIGHT, 0xCC2A221A);
        guiGraphics.fill(x + 1, y + 1, x + width - 1, y + SUMMARY_HEIGHT - 1, 0xFF403327);
        guiGraphics.fill(x + 1, y + 1, x + width - 1, y + 6, accentColor);
        drawOutline(guiGraphics, x, y, width, SUMMARY_HEIGHT, withAlpha(accentColor, 130));
        guiGraphics.drawCenteredString(this.font, label, x + (width / 2), y + 10, 0xFFE9D9C3);
        guiGraphics.drawCenteredString(this.font, value, x + (width / 2), y + 21, 0xFFF7F0E5);
    }

    private boolean drawSkillRow(GuiGraphics guiGraphics, int x, int y, int width, double mouseX, double mouseY, SkillType skill) {
        SkillProgress progress = ClientSkillState.get(skill);
        int accentColor = getAccentColor(skill);
        boolean hovered = isHovered(x, y, width, ROW_HEIGHT, mouseX, mouseY);
        int outerColor = hovered ? 0xFF5A4736 : 0xFF3F3227;
        int innerColor = hovered ? 0xFF4A3A2D : 0xFF34291F;

        guiGraphics.fill(x, y, x + width, y + ROW_HEIGHT, outerColor);
        guiGraphics.fill(x + 1, y + 1, x + width - 1, y + ROW_HEIGHT - 1, innerColor);
        guiGraphics.fill(x + 1, y + 1, x + 5, y + ROW_HEIGHT - 1, accentColor);
        drawOutline(guiGraphics, x, y, width, ROW_HEIGHT, withAlpha(accentColor, hovered ? 170 : 115));

        guiGraphics.renderItem(getIconStack(skill), x + 9, y + 10);
        guiGraphics.drawString(this.font, Component.literal(skill.getDisplayName()), x + 32, y + 6, 0xFFF7F0E5, false);
        guiGraphics.drawString(this.font, Component.literal(skill.getCurrentBonusText(progress.level())), x + 32, y + 18, 0xFFD3C09B, false);

        int perks = skill.getPerksForLevel(progress.level());
        int nextLevelXp = PlayerSkills.getExperienceForNextLevel(progress.level());
        String xpText = progress.experience() + " / " + nextLevelXp;
        int levelChipWidth = 56;
        int perkChipWidth = 74;
        int chipRight = x + width - 10;
        int levelChipX = chipRight - levelChipWidth;
        int perkChipX = chipRight - perkChipWidth;
        int progressBarX = x + 152;
        int progressBarWidth = Math.max(96, perkChipX - 10 - progressBarX);
        int progressBarY = y + 22;
        float progressRatio = nextLevelXp <= 0 ? 1.0F : (float) progress.experience() / (float) nextLevelXp;
        int fillWidth = Math.round((progressBarWidth - 2) * Math.max(0.0F, Math.min(1.0F, progressRatio)));
        int xpX = progressBarX + Math.max(0, (progressBarWidth - this.font.width(xpText)) / 2);

        guiGraphics.drawString(this.font, Component.literal(xpText), xpX, y + 6, 0xFFE4D8C7, false);
        guiGraphics.fill(progressBarX, progressBarY, progressBarX + progressBarWidth, progressBarY + 6, 0xFF1C1712);
        guiGraphics.fill(progressBarX + 1, progressBarY + 1, progressBarX + progressBarWidth - 1, progressBarY + 5, 0xFF5A4A3A);
        if (fillWidth > 0) {
            guiGraphics.fill(progressBarX + 1, progressBarY + 1, progressBarX + 1 + fillWidth, progressBarY + 5, accentColor);
        }

        drawChip(guiGraphics, levelChipX, y + 6, levelChipWidth, 11, "Lv " + progress.level(), accentColor, 0xFFF7F0E5);
        drawChip(guiGraphics, perkChipX, y + 20, perkChipWidth, 11, perks + " perks", 0xFF8E7657, 0xFFEEDFC6);
        return hovered;
    }

    private void drawChip(GuiGraphics guiGraphics, int x, int y, int width, int height, String text, int fillColor, int textColor) {
        guiGraphics.fill(x, y, x + width, y + height, 0xFF1B1612);
        guiGraphics.fill(x + 1, y + 1, x + width - 1, y + height - 1, withAlpha(fillColor, 215));
        drawOutline(guiGraphics, x, y, width, height, withAlpha(fillColor, 150));
        guiGraphics.drawCenteredString(this.font, Component.literal(text), x + (width / 2), y + 2, textColor);
    }

    private List<Component> buildTooltip(SkillType skill, SkillProgress progress) {
        return List.of(
                Component.literal(skill.getDisplayName()).withStyle(ChatFormatting.GOLD),
                Component.literal("Level " + progress.level() + "  XP " + progress.experience() + " / " + PlayerSkills.getExperienceForNextLevel(progress.level()))
                        .withStyle(ChatFormatting.GRAY),
                Component.literal("Current: " + skill.getCurrentBonusText(progress.level())).withStyle(ChatFormatting.GREEN),
                Component.literal("Perk: " + skill.getPerkDescription() + " every 10 levels").withStyle(ChatFormatting.YELLOW),
                Component.literal("Next perk at level " + skill.getNextPerkLevel(progress.level())).withStyle(ChatFormatting.AQUA)
        );
    }

    private ScreenLayout createLayout() {
        float availableWidth = Math.max(1.0F, this.width - 16.0F);
        float availableHeight = Math.max(1.0F, this.height - 16.0F);
        float scale = Math.min(1.0F, Math.min(availableWidth / PANEL_WIDTH, availableHeight / PANEL_HEIGHT));
        int scaledWidth = Math.round(PANEL_WIDTH * scale);
        int scaledHeight = Math.round(PANEL_HEIGHT * scale);
        int left = Math.max(0, (this.width - scaledWidth) / 2);
        int top = Math.max(0, (this.height - scaledHeight) / 2);
        return new ScreenLayout(scale, left, top);
    }

    private static void drawOutline(GuiGraphics guiGraphics, int x, int y, int width, int height, int color) {
        guiGraphics.hLine(x, x + width - 1, y, color);
        guiGraphics.hLine(x, x + width - 1, y + height - 1, color);
        guiGraphics.vLine(x, y, y + height - 1, color);
        guiGraphics.vLine(x + width - 1, y, y + height - 1, color);
    }

    private static boolean isHovered(int x, int y, int width, int height, double mouseX, double mouseY) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    private static int withAlpha(int color, int alpha) {
        return ((alpha & 0xFF) << 24) | (color & 0x00FFFFFF);
    }

    private static int getAccentColor(SkillType skill) {
        return switch (skill) {
            case WOODCUTTING -> 0xFF7E9B52;
            case MINING -> 0xFF8B98A6;
            case FISHING -> 0xFF5C88B5;
            case FARMING -> 0xFFB59A45;
            case ATTACK -> 0xFFC96B3E;
            case DEFENCE -> 0xFF5F8B82;
            case ARCHER -> 0xFFA78052;
        };
    }

    private static ItemStack getIconStack(SkillType skill) {
        return switch (skill) {
            case WOODCUTTING -> new ItemStack(Items.OAK_LOG);
            case MINING -> new ItemStack(Items.IRON_PICKAXE);
            case FISHING -> new ItemStack(Items.FISHING_ROD);
            case FARMING -> new ItemStack(Items.WHEAT);
            case ATTACK -> new ItemStack(Items.IRON_SWORD);
            case DEFENCE -> new ItemStack(Items.SHIELD);
            case ARCHER -> new ItemStack(Items.BOW);
        };
    }

    private record ScreenLayout(float scale, int left, int top) {
    }
}
