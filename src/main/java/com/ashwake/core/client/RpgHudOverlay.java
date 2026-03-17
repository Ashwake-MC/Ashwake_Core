package com.ashwake.core.client;

import com.ashwake.core.AshwakeCore;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ambient.AmbientCreature;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.WaterAnimal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.EntityHitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

@EventBusSubscriber(modid = AshwakeCore.MOD_ID, value = Dist.CLIENT)
public final class RpgHudOverlay {
    private static final int SLOT_COUNT = 9;
    private static final int VANILLA_CONTEXT_BAR_WIDTH = 182;
    private static final int SLOT_SIZE = 18;
    private static final int SLOT_GAP = 3;
    private static final int HOTBAR_PADDING = 10;
    private static final int HOTBAR_HEIGHT = 34;
    private static final int HUD_BOTTOM_MARGIN = 8;
    private static final int VANILLA_CONTEXT_BAR_INSET = 12;
    private static final int VANILLA_CONTEXT_BAR_TOP_OFFSET = 13;
    private static final int VANILLA_CONTEXT_BAR_HEIGHT = 6;
    private static final int SLOT_TOP_OFFSET = 12;
    private static final int SIDE_PANEL_WIDTH = 64;
    private static final int SIDE_PANEL_HEIGHT = 22;
    private static final int SIDE_PANEL_GAP = 10;
    private static final int XP_HEIGHT = 4;
    private static final int XP_GAP = 14;
    private static final int TREE_HUD_SAFE_GAP = 34;
    private static final float VALUE_SCALE = 0.72F;
    private static final ResourceLocation CONTEXTUAL_INFO_BAR = ResourceLocation.withDefaultNamespace("contextual_info_bar");
    private static final ResourceLocation CONTEXTUAL_INFO_BAR_BACKGROUND = ResourceLocation.withDefaultNamespace(
            "contextual_info_bar_background"
    );
    private static final Field GUI_CONTEXTUAL_INFO_BAR_FIELD = findGuiContextualInfoBarField();
    private static Object emptyContextualPair = null;
    private static final Set<ResourceLocation> BASE_HIDDEN_LAYERS = Set.of(
            VanillaGuiLayers.HOTBAR,
            VanillaGuiLayers.PLAYER_HEALTH,
            VanillaGuiLayers.ARMOR_LEVEL,
            VanillaGuiLayers.FOOD_LEVEL,
            VanillaGuiLayers.AIR_LEVEL,
            VanillaGuiLayers.EXPERIENCE_BAR,
            VanillaGuiLayers.EXPERIENCE_LEVEL,
            CONTEXTUAL_INFO_BAR,
            CONTEXTUAL_INFO_BAR_BACKGROUND
    );

    private RpgHudOverlay() {
    }

    @SubscribeEvent
    public static void onRenderLayerPre(RenderGuiLayerEvent.Pre event) {
        Minecraft minecraft = Minecraft.getInstance();
        suppressVanillaContextualBar(minecraft);
        if (!shouldUseCustomHud(minecraft)) {
            return;
        }

        if (shouldHideLayer(minecraft, event.getName())) {
            event.setCanceled(true);
            return;
        }

        if (event.getName().equals(VanillaGuiLayers.SELECTED_ITEM_NAME)) {
            event.getGuiGraphics().pose().pushPose();
            event.getGuiGraphics().pose().translate(0, -18, 0);
        }
    }

    @SubscribeEvent
    public static void onRenderLayerPost(RenderGuiLayerEvent.Post event) {
        if (event.getName().equals(VanillaGuiLayers.SELECTED_ITEM_NAME)) {
            event.getGuiGraphics().pose().popPose();
        }
    }

    @SubscribeEvent
    public static void onRender(RenderGuiEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        suppressVanillaContextualBar(minecraft);
        Player player = minecraft.player;
        MultiPlayerGameMode gameMode = minecraft.gameMode;
        if (!shouldUseCustomHud(minecraft) || player == null || gameMode == null) {
            return;
        }

        GuiGraphics guiGraphics = event.getGuiGraphics();
        Font font = minecraft.font;
        float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        float time = player.tickCount + partialTick;

        int screenWidth = minecraft.getWindow().getGuiScaledWidth();
        int screenHeight = minecraft.getWindow().getGuiScaledHeight();
        int hotbarWidth = SLOT_COUNT * SLOT_SIZE + (SLOT_COUNT - 1) * SLOT_GAP + HOTBAR_PADDING * 2;
        int hotbarX = (screenWidth - hotbarWidth) / 2;
        int hotbarY = screenHeight - HUD_BOTTOM_MARGIN - HOTBAR_HEIGHT;

        maskVanillaContextBarAbsolute(guiGraphics, screenWidth, screenHeight);
        drawHotbarBody(guiGraphics, hotbarX, hotbarY, hotbarWidth, HOTBAR_HEIGHT, time);
        drawHotbarSlots(guiGraphics, font, player, hotbarX, hotbarY, partialTick, time);

        ItemStack offhand = player.getOffhandItem();
        if (!offhand.isEmpty()) {
            int offhandX = player.getMainArm() == HumanoidArm.RIGHT
                    ? hotbarX - SLOT_SIZE - 8
                    : hotbarX + hotbarWidth + 8;
            drawOffhandSlot(guiGraphics, font, offhandX, hotbarY + 6, offhand, time);
        }

        if (gameMode.canHurtPlayer()) {
            drawStatusPanels(guiGraphics, font, player, hotbarX, hotbarY, hotbarWidth, time);
        }

        if (gameMode.hasExperience()) {
            drawExperienceBand(guiGraphics, font, player, hotbarX, hotbarY - XP_GAP, hotbarWidth, time);
        }

        drawTargetHud(guiGraphics, font, minecraft, screenWidth, time);
    }

    private static void drawTargetHud(GuiGraphics guiGraphics, Font font, Minecraft minecraft, int screenWidth, float time) {
        if (minecraft.hitResult instanceof EntityHitResult ehr && ehr.getEntity() instanceof LivingEntity entity) {
            if (entity == minecraft.player || !entity.isAlive() || entity.isInvisible()) {
                return;
            }

            if (!(entity instanceof Animal) && !(entity instanceof Enemy) &&
                    !(entity instanceof WaterAnimal) && !(entity instanceof AmbientCreature) &&
                    !(entity instanceof NeutralMob)) {
                return;
            }

            int width = 160;
            int height = 24;
            int x = (screenWidth - width) / 2;
            int y = 10;

            int barkEdge = color(0xFF1B120C);
            int bark = color(0xFF513624);
            int barkDark = color(0xFF2A1B13);
            int barkPanel = color(0xFF17110D);
            int gold = color(0xFFD4BF82);

            fillPlaque(guiGraphics, x, y, width, height, barkEdge, bark, barkDark, barkPanel);

            String name = entity.getDisplayName().getString();
            drawScaledText(guiGraphics, font, name, x + 6, y + 5, gold, 0.9F);

            float health = entity.getHealth();
            float maxHealth = entity.getMaxHealth();
            float healthProgress = clamp01(health / maxHealth);

            int barX = x + 6;
            int barY = y + 15;
            int barWidth = width - 12;
            int barHeight = 4;

            int healthStart = color(0xFFC75B4E);
            int healthEnd = color(0xFF71261F);

            drawMeter(guiGraphics, barX, barY, barWidth, barHeight, healthProgress, healthStart, healthEnd, barkEdge, barkDark, time);

            String hpText = (int) Math.ceil(health) + " / " + (int) Math.ceil(maxHealth);
            drawScaledText(guiGraphics, font, hpText, x + width - 6 - scaledWidth(font, hpText, 0.7F), y + 6, color(0xFFF1E9D0), 0.7F);
        }
    }

    public static int treeHudBottomOffset() {
        return HUD_BOTTOM_MARGIN + HOTBAR_HEIGHT + TREE_HUD_SAFE_GAP;
    }

    private static boolean shouldUseCustomHud(Minecraft minecraft) {
        return minecraft.player != null
                && minecraft.gameMode != null
                && !minecraft.options.hideGui
                && !minecraft.player.isSpectator();
    }

    private static boolean shouldHideLayer(Minecraft minecraft, ResourceLocation layer) {
        if (BASE_HIDDEN_LAYERS.contains(layer)) {
            return true;
        }

        return false;
    }

    private static void drawHotbarBody(GuiGraphics guiGraphics, int x, int y, int width, int height, float time) {
        float glowPulse = 0.58F + (float) Math.sin(time * 0.18F) * 0.12F;
        int shadow = color(0x4D000000);
        int barkEdge = color(0xFF1B120C);
        int bark = color(0xFF513624);
        int barkDark = color(0xFF2A1B13);
        int barkPanel = color(0xFF17110D);
        int gold = color(0xFFD4BF82);
        int goldShade = color(0xFF67502D);
        int glow = color(0x12000000 | ((int) (glowPulse * 255.0F) << 16) | ((int) (glowPulse * 210.0F) << 8) | 0x6B);

        guiGraphics.fill(x + 5, y + 3, x + width + 6, y + height + 6, shadow);
        guiGraphics.fill(x + 12, y + height - 1, x + width - 12, y + height + 2, glow);

        fillPlaque(guiGraphics, x, y, width, height, barkEdge, bark, barkDark, barkPanel);
        maskVanillaContextBar(guiGraphics, x, y, width, barkDark, barkPanel);
        drawSlotHeaderBeam(guiGraphics, x, y, width, barkEdge, bark, barkDark, goldShade);
        drawOutline(guiGraphics, x + 8, y + 3, width - 16, height - 6, goldShade);

        drawRune(guiGraphics, x + 14, y + height - 6, gold, goldShade);
        drawRune(guiGraphics, x + width - 18, y + height - 6, gold, goldShade);
    }

    private static void drawHotbarSlots(
            GuiGraphics guiGraphics,
            Font font,
            Player player,
            int hotbarX,
            int hotbarY,
            float partialTick,
            float time
    ) {
        Inventory inventory = player.getInventory();
        int selectedSlotIndex = resolveSelectedSlot(player, inventory);
        int startX = hotbarX + HOTBAR_PADDING;

        for (int index = 0; index < SLOT_COUNT; index++) {
            int slotX = startX + index * (SLOT_SIZE + SLOT_GAP);
            ItemStack stack = inventory.getItem(index);
            boolean selectedSlot = index == selectedSlotIndex;
            int slotY = hotbarY + SLOT_TOP_OFFSET - (selectedSlot ? 1 : 0);
            float pulse = selectedSlot ? 0.6F + 0.4F * (float) Math.sin(time * 0.42F) : 0.0F;

            drawSlotFrame(guiGraphics, slotX, slotY, selectedSlot, pulse);

            if (!stack.isEmpty()) {
                int itemX = slotX + 1;
                int itemY = slotY + 1;
                guiGraphics.renderItem(stack, itemX, itemY, index);
                guiGraphics.renderItemDecorations(font, stack, itemX, itemY);
            }

            if (selectedSlot) {
                float attackReady = player.getAttackStrengthScale(partialTick);
                drawAttackMeter(guiGraphics, slotX + SLOT_SIZE - 3, slotY + 2, attackReady);
            }
        }
    }

    private static void drawSlotFrame(GuiGraphics guiGraphics, int x, int y, boolean selected, float pulse) {
        int barkEdge = color(0xFF1E140F);
        int bark = color(0xFF2D1E16);
        int barkDark = color(0xFF130E0B);
        int gold = color(0xFFCDB77A);
        int goldShade = color(0xFF735B35);
        int highlight = color(selected ? 0x66E3CF8B : 0x16000000);

        guiGraphics.fill(x - 1, y - 1, x + SLOT_SIZE + 1, y + SLOT_SIZE + 1, selected ? goldShade : barkEdge);
        guiGraphics.fill(x, y, x + SLOT_SIZE, y + SLOT_SIZE, selected ? gold : barkEdge);
        guiGraphics.fill(x + 1, y + 1, x + SLOT_SIZE - 1, y + SLOT_SIZE - 1, bark);
        if (selected) {
            guiGraphics.fill(x + 1, y + 1, x + SLOT_SIZE - 1, y + SLOT_SIZE - 1, color(0x22FFFFFF));
        }
        guiGraphics.fillGradient(x + 2, y + 2, x + SLOT_SIZE - 2, y + SLOT_SIZE - 2, color(0xFF3A281D), barkDark);
        guiGraphics.fill(x + 3, y + 3, x + SLOT_SIZE - 3, y + 4, highlight);
        if (selected) {
            int glowWidth = Math.max(4, Math.round((SLOT_SIZE - 4) * (0.35F + pulse * 0.35F)));
            guiGraphics.fill(x + (SLOT_SIZE - glowWidth) / 2, y - 2, x + (SLOT_SIZE + glowWidth) / 2, y, color(0x55E5D49A));
        }
    }

    private static void drawAttackMeter(GuiGraphics guiGraphics, int x, int y, float progress) {
        int height = SLOT_SIZE - 6;
        int fillHeight = Math.max(0, Math.round(height * clamp01(progress)));

        guiGraphics.fill(x, y, x + 2, y + height, color(0xFF140F0C));
        guiGraphics.fill(x + 1, y + 1, x + 1, y + height - 1, color(0xFF4A3924));
        if (fillHeight > 0) {
            guiGraphics.fillGradient(x, y + height - fillHeight, x + 2, y + height, color(0xFFB5DB89), color(0xFF53733B));
        }
    }

    private static void drawOffhandSlot(GuiGraphics guiGraphics, Font font, int x, int y, ItemStack stack, float time) {
        drawSlotFrame(guiGraphics, x, y, true, 0.45F + 0.25F * (float) Math.sin(time * 0.27F));
        guiGraphics.renderItem(stack, x + 1, y + 1, 99);
        guiGraphics.renderItemDecorations(font, stack, x + 1, y + 1);
    }

    private static void drawStatusPanels(
            GuiGraphics guiGraphics,
            Font font,
            Player player,
            int hotbarX,
            int hotbarY,
            int hotbarWidth,
            float time
    ) {
        float health = Math.max(0.0F, player.getHealth());
        float maxHealth = Math.max(1.0F, player.getMaxHealth());
        float absorption = player.getAbsorptionAmount();
        float healthProgress = clamp01(health / maxHealth);
        float absorptionProgress = clamp01(absorption / maxHealth);
        float armorProgress = clamp01(player.getArmorValue() / 20.0F);

        FoodData foodData = player.getFoodData();
        float foodProgress = clamp01(foodData.getFoodLevel() / 20.0F);
        boolean showBreath = player.getAirSupply() < player.getMaxAirSupply();
        float auxRightProgress = showBreath
                ? clamp01(player.getAirSupply() / (float) Math.max(1, player.getMaxAirSupply()))
                : clamp01(foodData.getSaturationLevel() / 20.0F);

        int leftX = hotbarX - SIDE_PANEL_WIDTH - SIDE_PANEL_GAP;
        int rightX = hotbarX + hotbarWidth + SIDE_PANEL_GAP;
        int panelY = hotbarY + 3;

        drawStatusPanel(
                guiGraphics,
                font,
                leftX,
                panelY,
                "HP",
                Math.round(health + absorption) + "/" + Math.round(maxHealth),
                healthProgress,
                color(0xFFC75B4E),
                color(0xFF71261F),
                absorptionProgress,
                color(0xFFEAD683),
                color(0xFF9B8236),
                armorProgress,
                color(0xFFA8BAC9),
                color(0xFF4D5D69),
                time
        );
        drawStatusPanel(
                guiGraphics,
                font,
                rightX,
                panelY,
                showBreath ? "AIR" : "HUNGER",
                foodData.getFoodLevel() + "/20",
                foodProgress,
                color(0xFFD3A352),
                color(0xFF76501A),
                0.0F,
                0,
                0,
                auxRightProgress,
                showBreath ? color(0xFF72D0E4) : color(0xFFBFD277),
                showBreath ? color(0xFF2C6F80) : color(0xFF556A2E),
                time + 8.0F
        );
    }

    private static void drawStatusPanel(
            GuiGraphics guiGraphics,
            Font font,
            int x,
            int y,
            String label,
            String value,
            float progress,
            int fillStart,
            int fillEnd,
            float absorptionProgress,
            int absorptionStart,
            int absorptionEnd,
            float auxProgress,
            int auxStart,
            int auxEnd,
            float time
    ) {
        int shadow = color(0x4A000000);
        int barkEdge = color(0xFF1B120D);
        int bark = color(0xFF4A3223);
        int barkDark = color(0xFF261913);
        int panel = color(0xFF14100C);
        int gold = color(0xFFD0BE82);
        int text = color(0xFFF1E9D0);
        int textDim = color(0xFFCDBF9D);
        int barFrame = color(0xFF352418);
        int barBg = color(0xFF0E0B09);
        int valueX = x + SIDE_PANEL_WIDTH - 8 - scaledWidth(font, value, VALUE_SCALE);
        float labelScale = fittedScale(font, label, Math.max(18, valueX - (x + 8) - 4), 0.72F, 1.0F);

        guiGraphics.fill(x + 4, y + 3, x + SIDE_PANEL_WIDTH + 4, y + SIDE_PANEL_HEIGHT + 4, shadow);
        fillPlaque(guiGraphics, x, y, SIDE_PANEL_WIDTH, SIDE_PANEL_HEIGHT, barkEdge, bark, barkDark, panel);
        drawOutline(guiGraphics, x + 6, y + 3, SIDE_PANEL_WIDTH - 12, SIDE_PANEL_HEIGHT - 6, gold);

        drawScaledText(guiGraphics, font, label, x + 8, y + 4, text, labelScale);
        drawScaledText(guiGraphics, font, value, valueX, y + 5, textDim, VALUE_SCALE);

        int barX = x + 8;
        int barY = y + 12;
        int barWidth = SIDE_PANEL_WIDTH - 16;
        drawMeter(guiGraphics, barX, barY, barWidth, 4, progress, fillStart, fillEnd, barFrame, barBg, time);

        if (absorptionProgress > 0.0F) {
            int absWidth = Math.max(0, Math.round((barWidth - 2) * absorptionProgress));
            if (absWidth > 0) {
                guiGraphics.fillGradient(barX + 1, barY + 3, barX + 1 + absWidth, barY + 4, absorptionStart, absorptionEnd);
            }
        }

        if (auxProgress > 0.0F) {
            drawMeter(guiGraphics, barX, barY + 7, barWidth, 2, auxProgress, auxStart, auxEnd, barkEdge, barBg, time + 12.0F);
        }
    }

    private static void drawExperienceBand(
            GuiGraphics guiGraphics,
            Font font,
            Player player,
            int hotbarX,
            int y,
            int hotbarWidth,
            float time
    ) {
        int bandX = hotbarX + 30;
        int bandWidth = hotbarWidth - 60;
        int shadow = color(0x33000000);
        int frame = color(0xFF4A3A1F);
        int bg = color(0xFF0B0D08);
        int fillStart = color(0xFF8BC46A);
        int fillEnd = color(0xFF3B6B31);
        int gem = color(0xFFDCCB86);
        int gemShade = color(0xFF6E5B31);
        int text = color(0xFFFFFFFF);

        guiGraphics.fill(bandX + 2, y + 2, bandX + bandWidth + 2, y + XP_HEIGHT + 3, shadow);
        guiGraphics.fill(bandX - 1, y - 1, bandX + bandWidth + 1, y + XP_HEIGHT + 1, frame);
        guiGraphics.fill(bandX, y, bandX + bandWidth, y + XP_HEIGHT, bg);

        int fillWidth = Math.max(0, Math.round((bandWidth - 2) * clamp01(player.experienceProgress)));
        if (fillWidth > 0) {
            guiGraphics.fillGradient(bandX + 1, y + 1, bandX + 1 + fillWidth, y + XP_HEIGHT - 1, fillStart, fillEnd);
            drawShimmer(guiGraphics, bandX + 1, y + 1, fillWidth, XP_HEIGHT - 2, time);
        }

        String level = Integer.toString(Math.max(0, player.experienceLevel));
        int levelWidth = font.width(level);
        int gemWidth = Math.max(14, levelWidth + 6);
        int gemX = hotbarX + hotbarWidth / 2 - gemWidth / 2;
        int gemY = y - 8;

        // Shadow
        guiGraphics.fill(gemX + 1, gemY + 1, gemX + gemWidth + 1, gemY + 15, shadow);

        // Gem drawing - now with dynamic width
        guiGraphics.fill(gemX, gemY, gemX + gemWidth, gemY + 14, gemShade);
        guiGraphics.fill(gemX + 1, gemY + 1, gemX + gemWidth - 1, gemY + 13, gem);
        guiGraphics.fill(gemX + 2, gemY + 2, gemX + gemWidth - 2, gemY + 12, color(0xFF17110D));
        guiGraphics.fill(gemX + 2, gemY + 2, gemX + gemWidth - 2, gemY + 5, color(0x559CC97C));

        // Center text in the dynamic gem
        guiGraphics.drawString(font, level, gemX + (gemWidth - levelWidth) / 2, gemY + 3, text, true);
    }

    private static void drawMeter(
            GuiGraphics guiGraphics,
            int x,
            int y,
            int width,
            int height,
            float progress,
            int fillStart,
            int fillEnd,
            int frame,
            int background,
            float time
    ) {
        guiGraphics.fill(x - 1, y - 1, x + width + 1, y + height + 1, frame);
        guiGraphics.fill(x, y, x + width, y + height, background);

        int fillWidth = Math.max(0, Math.round((width - 2) * clamp01(progress)));
        if (fillWidth > 0) {
            guiGraphics.fillGradient(x + 1, y + 1, x + 1 + fillWidth, y + height - 1, fillStart, fillEnd);
            if (height > 2) {
                guiGraphics.fillGradient(x + 1, y + 1, x + 1 + fillWidth, y + 2, color(0x44FFF4CA), 0x00000000);
            }
            drawShimmer(guiGraphics, x + 1, y + 1, fillWidth, Math.max(1, height - 2), time);
        }
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

    private static void maskVanillaContextBar(GuiGraphics guiGraphics, int x, int y, int width, int barkDark, int panel) {
        int left = x + VANILLA_CONTEXT_BAR_INSET;
        int top = y + VANILLA_CONTEXT_BAR_TOP_OFFSET;
        int right = x + width - VANILLA_CONTEXT_BAR_INSET;
        int bottom = top + VANILLA_CONTEXT_BAR_HEIGHT;

        guiGraphics.fill(left - 1, top - 1, right + 1, bottom + 1, barkDark);
        guiGraphics.fillGradient(left, top, right, bottom, panel, barkDark);
    }

    private static void maskVanillaContextBarAbsolute(GuiGraphics guiGraphics, int screenWidth, int screenHeight) {
        int left = (screenWidth - VANILLA_CONTEXT_BAR_WIDTH) / 2;
        int top = screenHeight - 24 - VANILLA_CONTEXT_BAR_HEIGHT;
        int right = left + VANILLA_CONTEXT_BAR_WIDTH;
        int bottom = top + VANILLA_CONTEXT_BAR_HEIGHT;
        int shadow = color(0xFF281A13);
        int fill = color(0xFF17110D);

        guiGraphics.fill(left - 2, top - 2, right + 2, bottom + 2, shadow);
        guiGraphics.fill(left - 1, top - 1, right + 1, bottom + 1, shadow);
        guiGraphics.fill(left, top, right, bottom, fill);
    }

    private static void drawSlotHeaderBeam(
            GuiGraphics guiGraphics,
            int x,
            int y,
            int width,
            int barkEdge,
            int bark,
            int barkDark,
            int trim
    ) {
        int left = x + 10;
        int right = x + width - 10;
        int top = y + 7;
        int bottom = y + 12;

        guiGraphics.fill(left - 1, top - 1, right + 1, bottom + 1, barkEdge);
        guiGraphics.fill(left, top, right, bottom, bark);
        guiGraphics.fillGradient(left + 1, top + 1, right - 1, bottom - 1, color(0xFF6A4932), barkDark);
        guiGraphics.fill(left + 2, top + 1, right - 2, top + 2, trim);

        for (int index = 1; index < SLOT_COUNT; index++) {
            int dividerX = x + HOTBAR_PADDING + index * (SLOT_SIZE + SLOT_GAP) - (SLOT_GAP / 2);
            guiGraphics.fill(dividerX, top + 1, dividerX + 1, bottom - 1, barkEdge);
        }
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

    private static void drawOutline(GuiGraphics guiGraphics, int x, int y, int width, int height, int color) {
        guiGraphics.hLine(x, x + width - 1, y, color);
        guiGraphics.hLine(x, x + width - 1, y + height - 1, color);
        guiGraphics.vLine(x, y, y + height - 1, color);
        guiGraphics.vLine(x + width - 1, y, y + height - 1, color);
    }

    private static int scaledWidth(Font font, String text, float scale) {
        return Math.round(font.width(text) * scale);
    }

    private static float fittedScale(Font font, String text, int maxWidth, float minScale, float maxScale) {
        if (text.isEmpty()) {
            return maxScale;
        }

        int naturalWidth = Math.max(1, font.width(text));
        float fittedScale = Math.min(maxScale, maxWidth / (float) naturalWidth);
        return Math.max(minScale, fittedScale);
    }

    private static void drawScaledText(GuiGraphics guiGraphics, Font font, String text, int x, int y, int color, float scale) {
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(x, y, 0.0F);
        guiGraphics.pose().scale(scale, scale, 1.0F);
        guiGraphics.drawString(font, text, 0, 0, color, false);
        guiGraphics.pose().popPose();
    }

    private static float clamp01(float value) {
        return Math.max(0.0F, Math.min(1.0F, value));
    }

    private static int resolveSelectedSlot(Player player, Inventory inventory) {
        return inventory.selected;
    }


    private static void suppressVanillaContextualBar(Minecraft minecraft) {
        if (minecraft == null || minecraft.gui == null || GUI_CONTEXTUAL_INFO_BAR_FIELD == null) {
            return;
        }

        try {
            if (emptyContextualPair == null) {
                Class<?> contextualInfoClass = Class.forName("net.minecraft.client.gui.Gui$ContextualInfo");
                @SuppressWarnings("unchecked")
                Object emptyContextualInfo = Enum.valueOf((Class<? extends Enum>) contextualInfoClass.asSubclass(Enum.class), "EMPTY");
                Class<?> rendererClass = Class.forName("net.minecraft.client.gui.contextualbar.ContextualBarRenderer");
                Object emptyRenderer = rendererClass.getField("EMPTY").get(null);
                Class<?> pairClass = GUI_CONTEXTUAL_INFO_BAR_FIELD.getType();
                emptyContextualPair = pairClass.getMethod("of", Object.class, Object.class).invoke(null, emptyContextualInfo, emptyRenderer);
            }
            GUI_CONTEXTUAL_INFO_BAR_FIELD.set(minecraft.gui, emptyContextualPair);
        } catch (ReflectiveOperationException ignored) {
            // If runtime mappings differ, the layer mask still handles the common case.
        }
    }

    private static Field findGuiContextualInfoBarField() {
        try {
            Field field = minecraftGuiClass().getDeclaredField("contextualInfoBar");
            field.setAccessible(true);
            return field;
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    private static Class<?> minecraftGuiClass() throws ClassNotFoundException {
        return Class.forName("net.minecraft.client.gui.Gui");
    }

    private static int color(int rgb) {
        return (rgb & 0xFF000000) == 0 && (rgb & 0x00FFFFFF) != 0
                ? 0xFF000000 | (rgb & 0x00FFFFFF)
                : rgb;
    }
}
