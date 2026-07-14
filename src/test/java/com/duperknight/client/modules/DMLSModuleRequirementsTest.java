package com.duperknight.client.modules;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DMLSModuleRequirementsTest {
    @Test
    void combinedRequirementNeedsBothMainAndDepartmentRanks() {
        TestModule module = new TestModule(StaffRank.MODERATOR, DepartmentRank.EVENTS_MODERATOR);

        assertEquals(StaffRank.MODERATOR, module.minimumStaffRank());
        assertEquals(StaffDepartment.EVENTS, module.requiredDepartment().orElseThrow());
        assertEquals(DepartmentRank.EVENTS_MODERATOR, module.minimumDepartmentRank().orElseThrow());
        assertTrue(module.isAvailableFor(StaffRank.MODERATOR, DepartmentRank.EVENTS_MODERATOR));
        assertTrue(module.isAvailableFor(StaffRank.ADMIN, DepartmentRank.EVENTS_ADMIN));
        assertFalse(module.isAvailableFor(StaffRank.HELPER, DepartmentRank.EVENTS_ADMIN));
        assertFalse(module.isAvailableFor(StaffRank.ADMIN, DepartmentRank.EVENTS_HELPER));
        assertFalse(module.isAvailableFor(StaffRank.ADMIN, DepartmentRank.SENIOR_BANS_STAFF));
    }

    @Test
    void departmentRankOnlyDoesNotImposeAMainRankThreshold() {
        TestModule module = new TestModule(DepartmentRank.EVENTS_MODERATOR);

        assertEquals(StaffRank.NONE, module.minimumStaffRank());
        assertTrue(module.isAvailableFor(StaffRank.HELPER, DepartmentRank.EVENTS_MODERATOR));
        assertFalse(module.isAvailableFor(StaffRank.NONE, DepartmentRank.EVENTS_ADMIN));
        assertFalse(module.isAvailableFor(StaffRank.ADMIN, DepartmentRank.EVENTS_HELPER));
    }

    @Test
    void departmentOnlyAcceptsAnyAssignedRankInThatDepartment() {
        TestModule module = new TestModule(StaffDepartment.EVENTS);

        assertTrue(module.minimumDepartmentRank().isEmpty());
        assertTrue(module.isAvailableFor(StaffRank.HELPER, DepartmentRank.EVENTS_HELPER));
        assertTrue(module.isAvailableFor(StaffRank.HELPER, DepartmentRank.EVENTS_ADMIN));
        assertFalse(module.isAvailableFor(StaffRank.HELPER, DepartmentRank.NOT_ASSIGNED));
        assertFalse(module.isAvailableFor(StaffRank.HELPER, DepartmentRank.BANS_STAFF));
    }

    @Test
    void notAssignedCannotBeDeclaredAsARequirement() {
        assertThrows(IllegalArgumentException.class, () -> new TestModule(DepartmentRank.NOT_ASSIGNED));
    }

    private static final class TestModule extends DMLSModule {
        private TestModule(StaffRank staffRank, DepartmentRank departmentRank) {
            super(staffRank, departmentRank);
        }

        private TestModule(DepartmentRank departmentRank) {
            super(departmentRank);
        }

        private TestModule(StaffDepartment department) {
            super(department);
        }

        @Override
        public Text displayName() {
            return Text.literal("Test");
        }

        @Override
        public ItemStack icon() {
            return ItemStack.EMPTY;
        }

        @Override
        public List<Text> description() {
            return List.of();
        }

        @Override
        public void openScreen(MinecraftClient client, Screen parent) {
        }

        @Override
        public void register() {
        }
    }
}
