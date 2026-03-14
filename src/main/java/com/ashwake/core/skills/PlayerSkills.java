package com.ashwake.core.skills;

import java.util.EnumMap;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

public final class PlayerSkills {
    private static final String ROOT_TAG = "ashwake_core.skills";
    private static final String LEVEL_TAG = "level";
    private static final String EXPERIENCE_TAG = "experience";
    private static final float GLOBAL_XP_GAIN_MULTIPLIER = 0.4F;

    private PlayerSkills() {
    }

    public static SkillGainResult addExperience(ServerPlayer player, SkillType skill, int baseAmount) {
        if (baseAmount <= 0 || player.isCreative()) {
            return SkillGainResult.NONE;
        }

        SkillProgress current = getProgress(player, skill);
        int adjustedAmount = Math.max(1, Math.round(applySkillGainBonus(skill, current.level(), baseAmount) * GLOBAL_XP_GAIN_MULTIPLIER));
        int level = current.level();
        int experience = current.experience() + adjustedAmount;
        boolean leveledUp = false;

        while (experience >= getExperienceForNextLevel(level)) {
            experience -= getExperienceForNextLevel(level);
            level++;
            leveledUp = true;
        }

        setProgress(player, skill, new SkillProgress(level, experience));
        boolean perkUnlocked = leveledUp && level % 10 == 0;
        if (leveledUp) {
            player.sendSystemMessage(
                    Component.literal(skill.getDisplayName() + " reached level " + level + ".")
                            .withStyle(ChatFormatting.GOLD)
            );
            if (perkUnlocked) {
                player.sendSystemMessage(
                        Component.literal(skill.getDisplayName() + " perk unlocked: " + skill.getPerkDescription() + ".")
                                .withStyle(ChatFormatting.GREEN)
                );
            }
        }

        return new SkillGainResult(adjustedAmount, level, leveledUp, perkUnlocked);
    }

    public static SkillProgress getProgress(Player player, SkillType skill) {
        CompoundTag skillTag = getSkillTag(player, skill);
        int level = Math.max(1, skillTag.getInt(LEVEL_TAG));
        int experience = Math.max(0, skillTag.getInt(EXPERIENCE_TAG));
        return new SkillProgress(level, experience);
    }

    public static Map<SkillType, SkillProgress> snapshot(Player player) {
        EnumMap<SkillType, SkillProgress> snapshot = new EnumMap<>(SkillType.class);
        for (SkillType skill : SkillType.values()) {
            snapshot.put(skill, getProgress(player, skill));
        }

        return snapshot;
    }

    public static float getAttackBonus(Player player) {
        return getPerkCount(player, SkillType.ATTACK) * 0.05F;
    }

    public static float getDefenceReduction(Player player) {
        return getPerkCount(player, SkillType.DEFENCE) * 0.04F;
    }

    public static float getArcherBonus(Player player) {
        return getPerkCount(player, SkillType.ARCHER) * 0.05F;
    }

    public static int getExperienceForNextLevel(int level) {
        return 50 + ((Math.max(1, level) - 1) * 25);
    }

    public static void copyFrom(Player source, Player target) {
        CompoundTag sourcePersisted = source.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        if (!sourcePersisted.contains(ROOT_TAG)) {
            return;
        }

        CompoundTag targetPersisted = target.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        targetPersisted.put(ROOT_TAG, sourcePersisted.getCompound(ROOT_TAG).copy());
        target.getPersistentData().put(Player.PERSISTED_NBT_TAG, targetPersisted);
    }

    private static int applySkillGainBonus(SkillType skill, int level, int baseAmount) {
        int perks = skill.getPerksForLevel(level);
        return switch (skill) {
            case WOODCUTTING, MINING, FISHING, FARMING -> Math.max(1, Math.round(baseAmount * (1.0F + (perks * 0.10F))));
            default -> baseAmount;
        };
    }

    private static int getPerkCount(Player player, SkillType skill) {
        return skill.getPerksForLevel(getProgress(player, skill).level());
    }

    private static void setProgress(Player player, SkillType skill, SkillProgress progress) {
        CompoundTag persisted = player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        CompoundTag rootTag = persisted.getCompound(ROOT_TAG);
        CompoundTag skillTag = new CompoundTag();
        skillTag.putInt(LEVEL_TAG, progress.level());
        skillTag.putInt(EXPERIENCE_TAG, progress.experience());
        rootTag.put(skill.name(), skillTag);
        persisted.put(ROOT_TAG, rootTag);
        player.getPersistentData().put(Player.PERSISTED_NBT_TAG, persisted);
    }

    private static CompoundTag getSkillTag(Player player, SkillType skill) {
        CompoundTag persisted = player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        CompoundTag rootTag = persisted.getCompound(ROOT_TAG);
        if (!rootTag.contains(skill.name())) {
            CompoundTag defaultTag = new CompoundTag();
            defaultTag.putInt(LEVEL_TAG, SkillProgress.DEFAULT.level());
            defaultTag.putInt(EXPERIENCE_TAG, SkillProgress.DEFAULT.experience());
            rootTag.put(skill.name(), defaultTag);
            persisted.put(ROOT_TAG, rootTag);
            player.getPersistentData().put(Player.PERSISTED_NBT_TAG, persisted);
            return defaultTag;
        }

        return rootTag.getCompound(skill.name());
    }

    public record SkillGainResult(int adjustedAmount, int newLevel, boolean leveledUp, boolean perkUnlocked) {
        public static final SkillGainResult NONE = new SkillGainResult(0, 1, false, false);
    }
}
