package com.duperknight.client.modules;

import java.util.Objects;

/**
 * Represents a module in the DMLS client. Use this class to create new modules for the DMLS.
 * Each module has a minimum staff rank required to use it. While this is still not enforced, it will be in the future.
 */
public abstract class DMLSModule {
    private final StaffRank minimumStaffRank;

    /**
     * Creates a new DMLSModule with the specified minimum staff rank.
     *
     * @param minimumStaffRank the minimum staff rank required to use this module
     */
    protected DMLSModule(StaffRank minimumStaffRank) {
        this.minimumStaffRank = Objects.requireNonNull(minimumStaffRank, "minimumStaffRank");
    }

    /**
     * Returns the minimum staff rank required to use this module.
     *
     * @return the minimum staff rank required to use this module
     */
    public StaffRank minimumStaffRank() {
        return minimumStaffRank;
    }

    /**
     * Registers the module with the game.
     */
    public abstract void register();
}
