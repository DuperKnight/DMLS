package com.duperknight.client.modules;

/**
 * Represents the different staff ranks in the game. In the future this will allow the staff member to configure
 * their current rank and with that the commands available to them.
 */
public enum StaffRank {
    HELPER("§b§lHELPER", 0),
    MODERATOR("§2§lMODERATOR", 1),
    SENIOR_MODERATOR("§6§lSR MOD", 2),
    SUPPORT("§f§lSUPPORT", 3),
    ADMIN("§4§lADMIN", 4);

    private final String displayName;
    private final int level;

    StaffRank(String displayName, int level) {
        this.displayName = displayName;
        this.level = level;
    }

    /**
     * Returns the display name of the staff rank.
     *
     * @return the display name of the staff rank
     */
    public String displayName() {
        return displayName;
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
}
