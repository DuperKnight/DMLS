package com.duperknight.client.modules;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

/**
 * Represents the Stoneworks staff ranks recognized from the hub scoreboard.
 */
public enum StaffRank {
    NONE("dmls.staff_rank.none", ChatFormatting.GRAY, -1),
    HELPER("dmls.staff_rank.helper", ChatFormatting.AQUA, 0),
    MODERATOR("dmls.staff_rank.moderator", ChatFormatting.DARK_GREEN, 1),
    SENIOR_MODERATOR("dmls.staff_rank.senior_moderator", ChatFormatting.GOLD, 2),
    SUPPORT("dmls.staff_rank.support", ChatFormatting.WHITE, 3),
    ADMIN("dmls.staff_rank.admin", ChatFormatting.DARK_RED, 4);

    private final String translationKey;
    private final ChatFormatting color;
    private final int level;

    StaffRank(String translationKey, ChatFormatting color, int level) {
        this.translationKey = translationKey;
        this.color = color;
        this.level = level;
    }

    /**
     * Returns the display name of the staff rank.
     *
     * @return the display name of the staff rank
     */
    public Component displayName() {
        return Component.translatable(translationKey).withStyle(color, ChatFormatting.BOLD);
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
