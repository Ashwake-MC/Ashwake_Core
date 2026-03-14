package com.ashwake.core.skills;

public record SkillProgress(int level, int experience) {
    public static final SkillProgress DEFAULT = new SkillProgress(1, 0);
}
