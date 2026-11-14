package com.duperknight.client.commands;

import com.duperknight.client.modules.StaffRank;
import com.duperknight.client.utils.ChatUtils;
import com.duperknight.client.utils.DMLSConfig;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerScoreEntry;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;

/** /dmls scanrank: reads the sidebar scoreboard while in the hub and applies the detected staff rank. */
public final class RankScanCommand {
    private static final String PREFIX = "§8[§6DMLS§8] §7";

    private RankScanCommand() {
    }

    public static int run(Minecraft client) {
        if (client.level == null) {
            return fail(client, "Not in a world", "Join a server, then run /dmls scanrank in the hub.");
        }

        Scoreboard scoreboard = client.level.getScoreboard();
        Objective sidebar = scoreboard.getDisplayObjective(DisplaySlot.SIDEBAR);
        if (sidebar == null) {
            return fail(client, "No scoreboard found", "Go to the hub and wait for the sidebar to load.");
        }

        // Replace with HubStaffRankDetector's own hub check if it has one.
        String title = ChatFormatting.stripFormatting(sidebar.getDisplayName().getString());
        if (title == null || !title.toLowerCase(Locale.ROOT).contains("hub")) {
            return fail(client, "Not in the hub", "Rank scanning only works on the hub scoreboard.");
        }

        List<String> lines = new ArrayList<>();
        for (PlayerScoreEntry entry : scoreboard.listPlayerScores(sidebar)) {
            if (entry.isHidden()) continue;
            PlayerTeam team = scoreboard.getPlayersTeam(entry.owner());
            Component text = PlayerTeam.formatNameForTeam(team, entry.ownerName());
            String line = ChatFormatting.stripFormatting(text.getString());
            if (line != null && !line.isBlank()) lines.add(line.trim());
        }

        Optional<StaffRank> found = findRank(lines);
        if (found.isEmpty()) {
            return fail(client, "No staff rank found", "Your scoreboard doesn't list a recognized staff rank.");
        }

        StaffRank previous = DMLSConfig.staffRank();
        StaffRank rank = found.get();
        boolean saved = DMLSConfig.setDetectedStaffRank(rank);
        sendSuccess(client, previous, rank, saved);
        return 1;
    }

    private static void sendSuccess(Minecraft client, StaffRank previous, StaffRank rank, boolean saved) {
        String rankName = rank.displayName().getString();
        ChatUtils.sendClientMessage(client, PREFIX + ChatUtils.separatorForChatWidth(client, PREFIX));
        ChatUtils.sendClientMessage(client, "§8• §aRank detected§7: §6" + rankName);
        if (previous != rank) {
            ChatUtils.sendClientMessage(client,
                    "§8• §7Changed from §8" + previous.displayName().getString()
                            + " §7→ §6" + rankName);
        }
        ChatUtils.sendClientMessage(client, saved
                ? "§8• §7Saved to your config"
                : "§8• §cApplied, but could not be saved to your config");
        ChatUtils.sendClientMessage(client, "§7" + ChatUtils.separatorForChatWidth(client, ""));
    }

    private static int fail(Minecraft client, String title, String hint) {
        ChatUtils.sendClientMessage(client, PREFIX + "§c✘ " + title);
        ChatUtils.sendClientMessage(client, "§8  " + hint);
        return 0;
    }

    /** Tries each line whole, then the text after a "Label:" prefix. Only staff ranks count. */
    private static Optional<StaffRank> findRank(List<String> lines) {
        for (String line : lines) {
            Optional<StaffRank> whole = DMLSConfig.parseRank(line).filter(StaffRank::isStaff);
            if (whole.isPresent()) return whole;

            int colon = line.lastIndexOf(':');
            if (colon >= 0 && colon < line.length() - 1) {
                Optional<StaffRank> afterLabel = DMLSConfig.parseRank(line.substring(colon + 1))
                        .filter(StaffRank::isStaff);
                if (afterLabel.isPresent()) return afterLabel;
            }
        }
        return Optional.empty();
    }
}