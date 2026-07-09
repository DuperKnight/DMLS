package com.duperknight.client.modules;

import java.util.Objects;

public abstract class DMLSModule {
    private final StaffRank minimumStaffRank;

    protected DMLSModule(StaffRank minimumStaffRank) {
        this.minimumStaffRank = Objects.requireNonNull(minimumStaffRank, "minimumStaffRank");
    }

    public StaffRank minimumStaffRank() {
        return minimumStaffRank;
    }

    public abstract void register();
}
