package com.duperknight.client.parser;

import com.duperknight.client.utils.TooltipUtils;
import com.duperknight.client.utils.TooltipUtils.TooltipLine;
import com.duperknight.client.utils.InputValidators;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/** Pure parser for the Lands player and land-list menu tooltip fixtures. */
public final class LandsMenuParser {
    private static final Pattern USERNAME = Pattern.compile("[A-Za-z0-9_]{3,16}");
    private static final int MAX_LAND_NAME_LENGTH = 64;

    private LandsMenuParser() {
    }

    public static LandList parseLands(List<TooltipLine> tooltip) {
        List<String> lands = new ArrayList<>();
        boolean inLands = false;
        for (TooltipLine line : List.copyOf(tooltip)) {
            String stripped = TooltipUtils.stripListMarker(line.text());
            if (!inLands) {
                inLands = stripped.toLowerCase(Locale.ROOT).startsWith("lands");
                continue;
            }
            if (TooltipUtils.isTooltipFooter(stripped)) continue;
            if (stripped.equalsIgnoreCase("none")) return new LandList(ParseStatus.PARSED, List.of());
            if (!stripped.isEmpty()) {
                if (!InputValidators.isSafeCommandArgument(stripped, MAX_LAND_NAME_LENGTH)) {
                    return new LandList(ParseStatus.MALFORMED, List.of());
                }
                lands.add(stripped);
            }
        }
        return inLands && !lands.isEmpty()
                ? new LandList(ParseStatus.PARSED, lands)
                : new LandList(ParseStatus.MALFORMED, List.of());
    }

    public static RankScan parsePlayerRank(List<TooltipLine> tooltip, String targetUsername) {
        boolean inPlayers = false;
        int playerPosition = 0;
        List<String> distinctRanks = new ArrayList<>();
        List<MutableRankStats> mutableStats = new ArrayList<>();
        String previousRank = "";
        RankAssignment targetRank = null;
        boolean hasMorePlayers = false;

        for (TooltipLine line : List.copyOf(tooltip)) {
            String stripped = TooltipUtils.stripListMarker(line.text());
            if (!inPlayers) {
                inPlayers = stripped.toLowerCase(Locale.ROOT).startsWith("players");
                continue;
            }
            if (TooltipUtils.isTooltipFooter(stripped)) continue;
            if (stripped.equals("...")) {
                hasMorePlayers = true;
                continue;
            }

            Optional<String> parsedName = line.grayUsername(USERNAME)
                    .or(() -> TooltipUtils.lastMatch(stripped, USERNAME));
            if (parsedName.isEmpty()) continue;

            playerPosition++;
            String playerName = parsedName.get();
            String role = stripped.substring(0, Math.max(0, stripped.lastIndexOf(playerName))).trim();
            String rankName = rankName(playerPosition, role);
            String formattedRank = formattedRankName(playerPosition, line, playerName, rankName);
            addDistinctRank(distinctRanks, rankName);
            int position = rankPosition(distinctRanks, rankName);
            MutableRankStats stats = getOrCreate(mutableStats, rankName, formattedRank, position);
            stats.visibleCount++;
            previousRank = rankName;

            if (!playerName.equalsIgnoreCase(targetUsername)) continue;
            if (playerPosition == 1) targetRank = RankAssignment.owner();
            else if (role.toLowerCase(Locale.ROOT).contains("admin")) targetRank = RankAssignment.admin();
            else if (role.equalsIgnoreCase("member")) targetRank = RankAssignment.memberOrUnknown();
            else targetRank = RankAssignment.custom(rankName, formattedRank, position);
        }

        if (!inPlayers || playerPosition == 0) return RankScan.malformed();
        if (!previousRank.isEmpty() && hasMorePlayers) {
            for (MutableRankStats stats : mutableStats) {
                if (stats.rank.equalsIgnoreCase(previousRank)) stats.openEnded = true;
            }
        }
        List<RankStats> stats = mutableStats.stream()
                .map(value -> new RankStats(value.rank, value.formattedRank, value.position,
                        value.visibleCount, value.openEnded))
                .toList();
        return new RankScan(ParseStatus.PARSED,
                targetRank == null ? RankAssignment.memberOrUnknown() : targetRank, stats);
    }

    private static String rankName(int playerPosition, String role) {
        if (playerPosition == 1) return "Owner";
        if (role.toLowerCase(Locale.ROOT).contains("admin")) return "Admin";
        return role.isEmpty() ? "Member/Unknown" : role;
    }

    private static String formattedRankName(int playerPosition, TooltipLine line,
                                            String playerName, String fallback) {
        if (playerPosition == 1 || fallback.equals("Admin") || fallback.equals("Member/Unknown")) return fallback;
        return line.formattedRoleBefore(playerName).orElse(fallback);
    }

    private static void addDistinctRank(List<String> ranks, String rank) {
        if (ranks.stream().noneMatch(rank::equalsIgnoreCase)) ranks.add(rank);
    }

    private static int rankPosition(List<String> ranks, String rank) {
        for (int i = 0; i < ranks.size(); i++) {
            if (ranks.get(i).equalsIgnoreCase(rank)) return i + 1;
        }
        return ranks.size() + 1;
    }

    private static MutableRankStats getOrCreate(List<MutableRankStats> stats, String rank,
                                                String formattedRank, int position) {
        for (MutableRankStats value : stats) {
            if (value.rank.equalsIgnoreCase(rank) && value.position == position) return value;
        }
        MutableRankStats created = new MutableRankStats(rank, formattedRank, position);
        stats.add(created);
        return created;
    }

    public enum ParseStatus { PARSED, MALFORMED }

    public enum RankType { OWNER, ADMIN, CUSTOM, MEMBER_OR_UNKNOWN }

    public record LandList(ParseStatus status, List<String> lands) {
        public LandList {
            lands = List.copyOf(lands);
        }
    }

    public record RankScan(ParseStatus status, RankAssignment assignment, List<RankStats> stats) {
        public RankScan {
            stats = List.copyOf(stats);
        }

        private static RankScan malformed() {
            return new RankScan(ParseStatus.MALFORMED, RankAssignment.memberOrUnknown(), List.of());
        }
    }

    public record RankStats(String rank, String formattedRank, int position,
                            int visibleCount, boolean openEnded) {
    }

    public record RankAssignment(RankType type, String customRank, String formattedCustomRank, int position) {
        public static RankAssignment owner() {
            return new RankAssignment(RankType.OWNER, "", "", 1);
        }

        public static RankAssignment admin() {
            return new RankAssignment(RankType.ADMIN, "", "", Integer.MAX_VALUE);
        }

        public static RankAssignment custom(String rank, String formattedRank, int position) {
            return new RankAssignment(RankType.CUSTOM, rank, formattedRank, position);
        }

        public static RankAssignment memberOrUnknown() {
            return new RankAssignment(RankType.MEMBER_OR_UNKNOWN, "", "", Integer.MAX_VALUE);
        }
    }

    private static final class MutableRankStats {
        private final String rank;
        private final String formattedRank;
        private final int position;
        private int visibleCount;
        private boolean openEnded;

        private MutableRankStats(String rank, String formattedRank, int position) {
            this.rank = rank;
            this.formattedRank = formattedRank;
            this.position = position;
        }
    }
}
