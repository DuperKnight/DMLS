package com.duperknight.client.modules;

public enum StaffRank {
    HELPER("Helper", 0),
    MODERATOR("Moderator", 1),
    SENIOR_MODERATOR("Senior Moderator", 2),
    SUPPORT("Support", 3),
    ADMIN("Admin", 4);

    private final String displayName;
    private final int level;

    StaffRank(String displayName, int level) {
        this.displayName = displayName;
        this.level = level;
    }

    public String displayName() {
        return displayName;
    }

    public int level() {
        return level;
    }

    public boolean isAtLeast(StaffRank minimumRank) {
        return level >= minimumRank.level;
    }
}
