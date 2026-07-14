package com.duperknight.client.modules;

import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * Represents the Stoneworks staff ranks recognized from the hub scoreboard.
 */
public enum StaffRank {
    NONE("dmls.staff_rank.none", Formatting.GRAY, -1),
    HELPER("dmls.staff_rank.helper", Formatting.AQUA, 0),
    MODERATOR("dmls.staff_rank.moderator", Formatting.DARK_GREEN, 1),
    SENIOR_MODERATOR("dmls.staff_rank.senior_moderator", Formatting.GOLD, 2),
    SUPPORT("dmls.staff_rank.support", Formatting.WHITE, 3),
    ADMIN("dmls.staff_rank.admin", Formatting.DARK_RED, 4);

    private final String translationKey;
    private final Formatting color;
    private final int level;

    StaffRank(String translationKey, Formatting color, int level) {
        this.translationKey = translationKey;
        this.color = color;
        this.level = level;
    }

    /**
     * Returns the display name of the staff rank.
     *
     * @return the display name of the staff rank
     */
    public Text displayName() {
        return Text.translatable(translationKey).formatted(color, Formatting.BOLD);
    }

    /**
     * Returns the level of the staff rank.
     *
     * @return the level of the staff rank
     */
    public int level() {
        return level;
    }

    /**
     * Checks if the staff rank is at least as high as the specified minimum rank.
     *
     * @param minimumRank the minimum rank to compare against
     * @return true if the staff rank is at least as high as the minimum rank, false otherwise
     */
    public boolean isAtLeast(StaffRank minimumRank) {
        return level >= minimumRank.level;
    }

    /** Returns whether this value represents a recognized Stoneworks staff rank. */
    public boolean isStaff() {
        return this != NONE;
    }
}
