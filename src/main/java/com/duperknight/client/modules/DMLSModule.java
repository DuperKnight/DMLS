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

/**
 * Represents a module in the DMLS client. Use this class to create new modules for the DMLS.
 * Each module has a minimum staff rank required to use it, checked against the rank selected with /dmls rank.
 */
public abstract class DMLSModule {
    private static final String PREFIX = "§8[§6DMLS§8] §7";

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

    /** The name displayed on the DMLS home menu. */
    public abstract Text displayName();

    /** The icon displayed on the DMLS home menu. */
    public abstract ItemStack icon();

    /** The description displayed at the top of this module's screen. */
    public abstract List<Text> description();

    /** Opens this module's screen, returning to the supplied DMLS home screen. */
    public abstract void openScreen(MinecraftClient client, Screen parent);

    /**
     * Checks if the selected staff rank meets the minimum rank of this module.
     * Sends a chat message if it doesn't.
     *
     * @param client the Minecraft client
     * @return true if the selected rank is high enough
     */
    protected boolean hasRequiredRank(MinecraftClient client) {
        StaffRank currentRank = DMLSConfig.staffRank();
        if (currentRank.isAtLeast(minimumStaffRank)) {
            return true;
        }

        ChatUtils.sendTranslatedMessage(client, PREFIX, "dmls.chat.rank.required",
                minimumStaffRank.displayName(), currentRank.displayName());
        return false;
    }

    /** The selected rank controls visibility only; the server remains authoritative. */
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

    /** Returns whether the selected staff rank may use this module without producing chat output. */
    public final boolean isAvailableToSelectedRank() {
        return DMLSConfig.staffRank().isAtLeast(minimumStaffRank);
    }

    public final boolean isVisibleForSelectedRank() {
        return isAvailableToSelectedRank();
    }

    /**
     * Registers the module with the game.
     */
    public abstract void register();
}
