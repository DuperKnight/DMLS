package com.duperknight.client.modules;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DepartmentRankTest {
    @Test
    void eachDepartmentOffersOnlyItsConfiguredRanks() {
        assertEquals(List.of(
                DepartmentRank.NOT_ASSIGNED,
                DepartmentRank.SENIOR_BANS_STAFF,
                DepartmentRank.BANS_STAFF,
                DepartmentRank.JR_BANS_STAFF
        ), DepartmentRank.optionsFor(StaffDepartment.BANS));
        assertEquals(List.of(
                DepartmentRank.NOT_ASSIGNED,
                DepartmentRank.EVENTS_ADMIN,
                DepartmentRank.EVENTS_MODERATOR,
                DepartmentRank.EVENTS_HELPER
        ), DepartmentRank.optionsFor(StaffDepartment.EVENTS));
        assertEquals(List.of(
                DepartmentRank.NOT_ASSIGNED,
                DepartmentRank.SENIOR_WAR_STAFF,
                DepartmentRank.WAR_STAFF,
                DepartmentRank.JR_WAR_STAFF
        ), DepartmentRank.optionsFor(StaffDepartment.WAR));
    }

    @Test
    void rankHierarchyAppliesOnlyWithinTheSameDepartment() {
        assertTrue(DepartmentRank.EVENTS_ADMIN.isAtLeast(DepartmentRank.EVENTS_MODERATOR));
        assertTrue(DepartmentRank.EVENTS_MODERATOR.isAtLeast(DepartmentRank.EVENTS_HELPER));
        assertFalse(DepartmentRank.EVENTS_HELPER.isAtLeast(DepartmentRank.EVENTS_MODERATOR));
        assertFalse(DepartmentRank.SENIOR_BANS_STAFF.isAtLeast(DepartmentRank.EVENTS_HELPER));
        assertFalse(DepartmentRank.NOT_ASSIGNED.isAtLeast(DepartmentRank.EVENTS_HELPER));
    }
}
