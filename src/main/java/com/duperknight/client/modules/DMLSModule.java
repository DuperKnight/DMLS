package com.duperknight.client.modules;

import com.duperknight.client.utils.ChatUtils;
import com.duperknight.client.utils.DMLSConfig;
import com.duperknight.client.utils.ServerGuard;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Represents a module in the DMLS client. Use this class to create new modules for the DMLS.
 * A module may require a hub-detected staff rank, a configured department rank, or both.
 */
public abstract class DMLSModule {
    private static final String PREFIX = "§8[§6DMLS§8] §7";

    private final StaffRank minimumStaffRank;
    private final StaffDepartment requiredDepartment;
    private final DepartmentRank minimumDepartmentRank;

    /**
     * Creates a new DMLSModule with the specified minimum staff rank.
     *
     * @param minimumStaffRank the minimum staff rank required to use this module
     */
    protected DMLSModule(StaffRank minimumStaffRank) {
        this(minimumStaffRank, null, null);
    }

    /** Requires only the supplied department rank; the department is inferred from it. */
    protected DMLSModule(DepartmentRank minimumDepartmentRank) {
        this(StaffRank.NONE, departmentOf(minimumDepartmentRank), minimumDepartmentRank);
    }

    /** Requires any assigned rank in the supplied department. */
    protected DMLSModule(StaffDepartment requiredDepartment) {
        this(StaffRank.NONE, Objects.requireNonNull(requiredDepartment, "requiredDepartment"), null);
    }

    /** Requires both the supplied main staff rank and department rank. */
    protected DMLSModule(StaffRank minimumStaffRank, DepartmentRank minimumDepartmentRank) {
        this(minimumStaffRank, departmentOf(minimumDepartmentRank), minimumDepartmentRank);
    }

    /** Requires both the supplied main staff rank and membership in a department. */
    protected DMLSModule(StaffRank minimumStaffRank, StaffDepartment requiredDepartment) {
        this(minimumStaffRank, Objects.requireNonNull(requiredDepartment, "requiredDepartment"), null);
    }

    private DMLSModule(
            StaffRank minimumStaffRank,
            StaffDepartment requiredDepartment,
            DepartmentRank minimumDepartmentRank
    ) {
        this.minimumStaffRank = Objects.requireNonNull(minimumStaffRank, "minimumStaffRank");
        this.requiredDepartment = requiredDepartment;
        this.minimumDepartmentRank = minimumDepartmentRank;
    }

    /**
     * Returns the minimum staff rank required to use this module.
     *
     * @return the minimum staff rank required to use this module
     */
    public StaffRank minimumStaffRank() {
        return minimumStaffRank;
    }

    public Optional<StaffDepartment> requiredDepartment() {
        return Optional.ofNullable(requiredDepartment);
    }

    public Optional<DepartmentRank> minimumDepartmentRank() {
        return Optional.ofNullable(minimumDepartmentRank);
    }

    /** The name displayed on the DMLS home menu. */
    public abstract Text displayName();

    /** The icon displayed on the DMLS home menu. */
    public abstract ItemStack icon();

    /** The description displayed at the top of this module's screen. */
    public abstract List<Text> description();

    /** The collapsible home-screen section containing this module. */
    public ModuleCategory category() {
        return ModuleCategory.GENERAL;
    }

    /** Opens this module's screen, returning to the supplied DMLS home screen. */
    public abstract void openScreen(MinecraftClient client, Screen parent);

    /**
     * Checks if the detected staff rank meets the minimum rank of this module.
     * Sends a chat message if it doesn't.
     *
     * @param client the Minecraft client
     * @return true if the detected rank is high enough
     */
    protected boolean hasRequiredRank(MinecraftClient client) {
        StaffRank currentRank = DMLSConfig.staffRank();
        if (!currentRank.isStaff()) {
            ChatUtils.sendTranslatedMessage(client, PREFIX, "dmls.chat.staff.required");
            return false;
        }
        if (minimumStaffRank.isStaff() && !currentRank.isAtLeast(minimumStaffRank)) {
            ChatUtils.sendTranslatedMessage(client, PREFIX, "dmls.chat.rank.required",
                    minimumStaffRank.displayName(), currentRank.displayName());
            return false;
        }

        if (requiredDepartment == null) return true;
        DepartmentRank currentDepartmentRank = DMLSConfig.departmentRank(requiredDepartment);
        if (minimumDepartmentRank != null && !currentDepartmentRank.isAtLeast(minimumDepartmentRank)) {
            ChatUtils.sendTranslatedMessage(client, PREFIX, "dmls.chat.department_rank.required",
                    minimumDepartmentRank.displayName(), requiredDepartment.displayName(),
                    currentDepartmentRank.displayName());
            return false;
        }
        if (minimumDepartmentRank == null && !currentDepartmentRank.isAssignedTo(requiredDepartment)) {
            ChatUtils.sendTranslatedMessage(client, PREFIX, "dmls.chat.department.required",
                    requiredDepartment.displayName());
            return false;
        }
        return true;
    }

    /** The detected rank controls visibility only; the server remains authoritative. */
    protected boolean canRunPrivilegedOperation(MinecraftClient client) {
        if (!hasRequiredRank(client)) return false;
        // A captured dry-run can safely validate and preview commands without a live connection.
        if (DMLSConfig.dryRun()) return true;
        ServerGuard.GuardResult guard = ServerGuard.check(client);
        if (guard.allowed()) return true;
        ChatUtils.sendTranslatedMessage(client, PREFIX, "dmls.chat.server_guard.blocked",
                guard.reason(), guard.address());
        return false;
    }

    /** Returns whether the detected staff rank may use this module without producing chat output. */
    public final boolean isAvailableToDetectedRank() {
        StaffRank currentStaffRank = DMLSConfig.staffRank();
        DepartmentRank currentDepartmentRank = requiredDepartment == null
                ? DepartmentRank.NOT_ASSIGNED
                : DMLSConfig.departmentRank(requiredDepartment);
        return isAvailableFor(currentStaffRank, currentDepartmentRank);
    }

    public final boolean isVisibleForDetectedRank() {
        return isAvailableToDetectedRank();
    }

    /** Pure requirement check useful for previews and tests. */
    public final boolean isAvailableFor(StaffRank currentStaffRank, DepartmentRank currentDepartmentRank) {
        if (currentStaffRank == null || !currentStaffRank.isStaff()) return false;
        if (minimumStaffRank.isStaff() && !currentStaffRank.isAtLeast(minimumStaffRank)) return false;
        if (requiredDepartment == null) return true;
        if (currentDepartmentRank == null) return false;
        return minimumDepartmentRank == null
                ? currentDepartmentRank.isAssignedTo(requiredDepartment)
                : currentDepartmentRank.isAtLeast(minimumDepartmentRank);
    }

    /** Checks a hypothetical main rank while using the currently configured department rank. */
    public final boolean isAvailableForStaffRank(StaffRank currentStaffRank) {
        DepartmentRank currentDepartmentRank = requiredDepartment == null
                ? DepartmentRank.NOT_ASSIGNED
                : DMLSConfig.departmentRank(requiredDepartment);
        return isAvailableFor(currentStaffRank, currentDepartmentRank);
    }

    private static StaffDepartment departmentOf(DepartmentRank departmentRank) {
        DepartmentRank rank = Objects.requireNonNull(departmentRank, "minimumDepartmentRank");
        if (rank == DepartmentRank.NOT_ASSIGNED) {
            throw new IllegalArgumentException("NOT_ASSIGNED cannot be a module requirement");
        }
        return rank.department();
    }

    /**
     * Registers the module with the game.
     */
    public abstract void register();
}
