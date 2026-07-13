package com.duperknight.client.parser;

import com.duperknight.client.utils.TooltipUtils;
import com.duperknight.client.utils.TooltipUtils.TooltipLine;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LandsMenuParserTest {
    @Test
    void parsesLandListsNoneAndMalformedFixtures() {
        var parsed = LandsMenuParser.parseLands(lines(
                "Player Alice", "Lands (2)", "- First Land", "- SecondLand", "minecraft:paper"));
        assertEquals(LandsMenuParser.ParseStatus.PARSED, parsed.status());
        assertEquals(List.of("First Land", "SecondLand"), parsed.lands());

        assertEquals(List.of(), LandsMenuParser.parseLands(lines("Lands (0)", "None")).lands());
        assertEquals(LandsMenuParser.ParseStatus.MALFORMED,
                LandsMenuParser.parseLands(lines("Player Alice", "Nothing useful")).status());
        assertEquals(LandsMenuParser.ParseStatus.MALFORMED,
                LandsMenuParser.parseLands(lines("Lands (2)", "minecraft:paper")).status());
        assertEquals(LandsMenuParser.ParseStatus.MALFORMED,
                LandsMenuParser.parseLands(lines("Lands (1)", "bad\nclaim")).status());
        assertEquals(LandsMenuParser.ParseStatus.MALFORMED,
                LandsMenuParser.parseLands(lines("Lands (1)", "x".repeat(65))).status());
    }

    @Test
    void parsesTargetRankCountsFormattingAndTruncation() {
        TooltipLine mayor = TooltipUtils.toTooltipLine(Text.literal("- ")
                .append(Text.literal("Mayor").formatted(Formatting.GOLD, Formatting.BOLD))
                .append(Text.literal(" Alice").formatted(Formatting.GRAY)));
        var parsed = LandsMenuParser.parsePlayerRank(List.of(
                line("Players (4)"), line("- OwnerName"), mayor,
                line("- Admin Bob"), line("- Member Charlie"), line("..."), line("minecraft:paper")), "Alice");

        assertEquals(LandsMenuParser.ParseStatus.PARSED, parsed.status());
        assertEquals(LandsMenuParser.RankType.CUSTOM, parsed.assignment().type());
        assertEquals("Mayor", parsed.assignment().customRank());
        assertEquals(2, parsed.assignment().position());
        assertTrue(parsed.assignment().formattedCustomRank().contains("Mayor"));
        assertEquals(4, parsed.stats().size());
        assertTrue(parsed.stats().getLast().openEnded());
    }

    @Test
    void treatsMissingTargetAsUnknownButRejectsMissingPlayerRows() {
        var unknown = LandsMenuParser.parsePlayerRank(lines("Players (2)", "- Owner", "- Member Bob"), "Alice");
        assertEquals(LandsMenuParser.RankType.MEMBER_OR_UNKNOWN, unknown.assignment().type());
        assertEquals(LandsMenuParser.ParseStatus.MALFORMED,
                LandsMenuParser.parsePlayerRank(lines("Players (2)", "minecraft:paper"), "Alice").status());
        assertEquals(LandsMenuParser.ParseStatus.MALFORMED,
                LandsMenuParser.parsePlayerRank(lines("Unrelated", "- Alice"), "Alice").status());
    }

    private static List<TooltipLine> lines(String... values) {
        return java.util.Arrays.stream(values).map(LandsMenuParserTest::line).toList();
    }

    private static TooltipLine line(String value) {
        return new TooltipLine(value, List.of());
    }
}
