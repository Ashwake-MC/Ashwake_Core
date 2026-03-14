package com.ashwake.core.skills;

import java.util.EnumMap;
import java.util.Map;

public final class ClientSkillState {
    private static EnumMap<SkillType, SkillProgress> progress = createDefaultState();

    private ClientSkillState() {
    }

    public static void apply(Map<SkillType, SkillProgress> updatedProgress) {
        EnumMap<SkillType, SkillProgress> newProgress = createDefaultState();
        newProgress.putAll(updatedProgress);
        progress = newProgress;
    }

    public static SkillProgress get(SkillType skill) {
        return progress.getOrDefault(skill, SkillProgress.DEFAULT);
    }

    private static EnumMap<SkillType, SkillProgress> createDefaultState() {
        EnumMap<SkillType, SkillProgress> defaults = new EnumMap<>(SkillType.class);
        for (SkillType skill : SkillType.values()) {
            defaults.put(skill, SkillProgress.DEFAULT);
        }

        return defaults;
    }
}
