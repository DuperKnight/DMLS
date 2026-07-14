package com.duperknight.client.parser;

import com.duperknight.client.utils.TooltipUtils;
import com.duperknight.client.utils.TooltipUtils.TooltipLine;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/** Pure parser for the player-list tooltip in a Lands information menu. */
public final class MembersMenuParser {
    private static final Pattern USERNAME = Pattern.compile("[A-Za-z0-9_]{3,16}");

    private MembersMenuParser() {
    }

    public static Scan parse(List<TooltipLine> tooltip) {
        boolean inPlayers = false;
        int playerPosition = 0;
        boolean truncated = false;
        List<MutableGroup> groups = new ArrayList<>();

        for (TooltipLine line : List.copyOf(tooltip)) {
            String stripped = TooltipUtils.stripListMarker(line.text());
            if (!inPlayers) {
                inPlayers = stripped.toLowerCase(Locale.ROOT).startsWith("players");
                continue;
            }
            if (TooltipUtils.isTooltipFooter(stripped)) continue;
            if (stripped.equals("...")) {
                truncated = true;
                continue;
            }

            Optional<String> parsedName = line.grayUsername(USERNAME)
                    .or(() -> TooltipUtils.lastMatch(stripped, USERNAME));
            if (parsedName.isEmpty()) continue;
            playerPosition++;
            String playerName = parsedName.get();
            String role = stripped.substring(0, Math.max(0, stripped.lastIndexOf(playerName))).trim();
            String rank = rankName(playerPosition, role);
            String formatted = formattedRankName(playerPosition, line, playerName, rank);
            groupFor(groups, rank, formatted).players.add(playerName);
        }

        if (!inPlayers || playerPosition == 0) return Scan.malformed();
        return new Scan(ParseStatus.PARSED, groups.stream()
                .map(group -> new Group(group.rank, group.formattedRank, group.players))
                .toList(), truncated);
    }

    private static String rankName(int position, String role) {
        if (position == 1) return "Owner";
        if (role.toLowerCase(Locale.ROOT).contains("admin")) return "Admin";
        return role.isEmpty() ? "Member/Unknown" : role;
    }

    private static String formattedRankName(int position, TooltipLine line, String playerName, String fallback) {
        if (position == 1) return "§4§lOwner";
        if (fallback.equals("Admin")) return "§c§lAdmin";
        if (fallback.equals("Member/Unknown")) return "§e§lMember/Unknown";
        return line.formattedRoleBefore(playerName).orElse(fallback);
    }

    private static MutableGroup groupFor(List<MutableGroup> groups, String rank, String formatted) {
        for (MutableGroup group : groups) {
            if (group.rank.equalsIgnoreCase(rank)) return group;
        }
        MutableGroup created = new MutableGroup(rank, formatted);
        groups.add(created);
        return created;
    }

    public enum ParseStatus { PARSED, MALFORMED }

    public record Scan(ParseStatus status, List<Group> groups, boolean truncated) {
        public Scan {
            groups = List.copyOf(groups);
        }

        private static Scan malformed() {
            return new Scan(ParseStatus.MALFORMED, List.of(), false);
        }
    }

    public record Group(String rank, String formattedRank, List<String> players) {
        public Group {
            players = List.copyOf(players);
        }
    }

    private static final class MutableGroup {
        private final String rank;
        private final String formattedRank;
        private final List<String> players = new ArrayList<>();

        private MutableGroup(String rank, String formattedRank) {
            this.rank = rank;
            this.formattedRank = formattedRank;
        }
    }
}
