package com.duperknight.client.modules;

import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.Arrays;
import java.util.List;

/** A persisted rank within one of the three Stoneworks staff departments. */
public enum DepartmentRank {
    NOT_ASSIGNED(null, "dmls.department_rank.not_assigned", -1),

    SENIOR_BANS_STAFF(StaffDepartment.BANS, "dmls.department_rank.senior_bans_staff", 2),
    BANS_STAFF(StaffDepartment.BANS, "dmls.department_rank.bans_staff", 1),
    JR_BANS_STAFF(StaffDepartment.BANS, "dmls.department_rank.jr_bans_staff", 0),

    EVENTS_ADMIN(StaffDepartment.EVENTS, "dmls.department_rank.events_admin", 2),
    EVENTS_MODERATOR(StaffDepartment.EVENTS, "dmls.department_rank.events_moderator", 1),
    EVENTS_HELPER(StaffDepartment.EVENTS, "dmls.department_rank.events_helper", 0),

    SENIOR_WAR_STAFF(StaffDepartment.WAR, "dmls.department_rank.senior_war_staff", 2),
    WAR_STAFF(StaffDepartment.WAR, "dmls.department_rank.war_staff", 1),
    JR_WAR_STAFF(StaffDepartment.WAR, "dmls.department_rank.jr_war_staff", 0);

    private final StaffDepartment department;
    private final String translationKey;
    private final int level;

    DepartmentRank(StaffDepartment department, String translationKey, int level) {
        this.department = department;
        this.translationKey = translationKey;
        this.level = level;
    }

    public Text displayName() {
        return Text.translatable(translationKey).formatted(
                this == NOT_ASSIGNED ? Formatting.GRAY : Formatting.WHITE);
    }

    public boolean belongsTo(StaffDepartment department) {
        return this == NOT_ASSIGNED || this.department == department;
    }

    public StaffDepartment department() {
        if (department == null) {
            throw new IllegalStateException("Not assigned has no department");
        }
        return department;
    }

    /** Returns whether this is an assigned rank in the supplied department. */
    public boolean isAssignedTo(StaffDepartment department) {
        return this != NOT_ASSIGNED && this.department == department;
    }

    /** Department ranks are hierarchical only within their own department. */
    public boolean isAtLeast(DepartmentRank minimumRank) {
        return minimumRank != null
                && this != NOT_ASSIGNED
                && minimumRank != NOT_ASSIGNED
                && department == minimumRank.department
                && level >= minimumRank.level;
    }

    public static List<DepartmentRank> optionsFor(StaffDepartment department) {
        return Arrays.stream(values()).filter(rank -> rank.belongsTo(department)).toList();
    }
}
