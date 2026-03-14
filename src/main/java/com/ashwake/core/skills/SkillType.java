package com.ashwake.core.skills;

public enum SkillType {
    WOODCUTTING("Woodcutting", "+10% woodcutting XP"),
    MINING("Mining", "+10% mining XP"),
    FISHING("Fishing", "+10% fishing XP"),
    FARMING("Farming", "+10% farming XP"),
    ATTACK("Attack", "+5% melee damage"),
    DEFENCE("Defence", "-4% incoming damage"),
    ARCHER("Archer", "+5% ranged damage");

    private final String displayName;
    private final String perkDescription;

    SkillType(String displayName, String perkDescription) {
        this.displayName = displayName;
        this.perkDescription = perkDescription;
    }

    public String getDisplayName() {
        return this.displayName;
    }

    public String getPerkDescription() {
        return this.perkDescription;
    }

    public String getCurrentBonusText(int level) {
        int perks = getPerksForLevel(level);
        return switch (this) {
            case WOODCUTTING, MINING, FISHING, FARMING -> "XP bonus +" + (perks * 10) + "%";
            case ATTACK -> "Melee bonus +" + (perks * 5) + "%";
            case DEFENCE -> "Damage taken -" + (perks * 4) + "%";
            case ARCHER -> "Ranged bonus +" + (perks * 5) + "%";
        };
    }

    public int getPerksForLevel(int level) {
        return level / 10;
    }

    public int getNextPerkLevel(int level) {
        return ((level / 10) + 1) * 10;
    }
}
