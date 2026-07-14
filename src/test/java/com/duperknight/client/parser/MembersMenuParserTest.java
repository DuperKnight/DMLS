package com.duperknight.client.parser;

import com.duperknight.client.utils.TooltipUtils;
import com.duperknight.client.utils.TooltipUtils.TooltipLine;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MembersMenuParserTest {
    @Test
    void groupsFixturePlayersByRankAndReportsTruncation() {
        TooltipLine mayor = TooltipUtils.toTooltipLine(Text.literal("- ")
                .append(Text.literal("Mayor").formatted(Formatting.AQUA, Formatting.BOLD))
                .append(Text.literal(" Alice").formatted(Formatting.GRAY)));
        MembersMenuParser.Scan scan = MembersMenuParser.parse(List.of(
                line("Players (5)"), line("- OwnerName"), mayor, line("- Mayor Bob"),
                line("- Admin Charlie"), line("- Member Delta"), line("..."), line("minecraft:paper")));

        assertEquals(MembersMenuParser.ParseStatus.PARSED, scan.status());
        assertEquals(List.of("Owner", "Mayor", "Admin", "Member"),
                scan.groups().stream().map(MembersMenuParser.Group::rank).toList());
        assertEquals(List.of("Alice", "Bob"), scan.groups().get(1).players());
        assertTrue(scan.groups().get(1).formattedRank().contains("Mayor"));
        assertTrue(scan.truncated());
    }

    @Test
    void rejectsReadyButMalformedTooltipFixtures() {
        assertEquals(MembersMenuParser.ParseStatus.MALFORMED,
                MembersMenuParser.parse(List.of(line("Players (2)"), line("minecraft:paper"))).status());
        assertEquals(MembersMenuParser.ParseStatus.MALFORMED,
                MembersMenuParser.parse(List.of(line("Unrelated"), line("- Alice"))).status());
    }

    private static TooltipLine line(String value) {
        return new TooltipLine(value, List.of());
    }
}
