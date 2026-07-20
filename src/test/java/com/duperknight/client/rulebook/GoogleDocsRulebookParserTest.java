package com.duperknight.client.rulebook;

import com.duperknight.client.rulebook.DocumentBlock.ImageBlock;
import com.duperknight.client.rulebook.DocumentBlock.InlineItem;
import com.duperknight.client.rulebook.DocumentBlock.RuleBox;
import com.duperknight.client.rulebook.DocumentBlock.TableBlock;
import com.duperknight.client.rulebook.DocumentBlock.TextBlock;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class GoogleDocsRulebookParserTest {
    @Test
    void parsesRichRuleBoxesTablesListsImagesAndSafeLinks() throws Exception {
        RulebookDocument document = GoogleDocsRulebookParser.parse(RulebookFixtures.document());
        RulebookRule rule = document.rule("18.7").orElseThrow();

        assertEquals(2, document.rules().size());
        assertEquals("18. Ban Evasion, Weaponization, and Retaliation", rule.section());
        assertEquals("Ban retaliation harassment", rule.title());
        assertEquals("1 month ban, permanent ban", rule.punishment());
        assertEquals(0xFF0000, rule.color());
        assertTrue(rule.punishable());
        assertFalse(document.rule("S5.1").orElseThrow().punishable());
        assertTrue(rule.matches("nested note"));
        assertFalse(rule.searchableText().contains("not searchable"));
        assertTrue(document.blocks().stream().anyMatch(TableBlock.class::isInstance));
        assertTrue(document.blocks().stream().anyMatch(DocumentBlock.Spacer.class::isInstance));
        assertTrue(document.rule("S5.1").isPresent());
        TextBlock openingTitle = document.blocks().stream().filter(TextBlock.class::isInstance)
                .map(TextBlock.class::cast)
                .filter(block -> block.text().plainText().contains("MC & Discord Rules"))
                .findFirst().orElseThrow();
        assertEquals(DocumentBlock.TextKind.TITLE, openingTitle.kind());
        TextBlock tabTitle = document.blocks().stream().filter(TextBlock.class::isInstance)
                .map(TextBlock.class::cast)
                .filter(block -> block.text().plainText().contains("RP & Extra Systems"))
                .findFirst().orElseThrow();
        assertEquals(DocumentBlock.TextKind.TITLE, tabTitle.kind());
        assertFalse(tabTitle.text().plainText().contains("\uFE0F"));

        TableBlock notes = document.blocks().stream().filter(TableBlock.class::isInstance)
                .map(TableBlock.class::cast)
                .filter(table -> table.rows().stream().flatMap(List::stream)
                        .anyMatch(cell -> cell.plainText().contains("Remember the context")))
                .findFirst().orElseThrow();
        String noteText = notes.rows().stream().flatMap(List::stream)
                .map(DocumentBlock.TableCell::plainText).reduce("", (left, right) -> left + "\n" + right);
        assertTrue(noteText.contains("a. First note"));
        assertTrue(noteText.contains("b. Second note"));
        assertTrue(noteText.contains("c. Third note"));
        assertTrue(notes.columnWeights().get(0) < notes.columnWeights().get(1));
        DocumentBlock.TableCell notesCell = notes.rows().getLast().get(1);
        assertTrue(notesCell.content().stream().anyMatch(ImageBlock.class::isInstance));
        assertTrue(notesCell.content().stream().filter(TextBlock.class::isInstance).map(TextBlock.class::cast)
                .filter(block -> block.kind() == DocumentBlock.TextKind.LIST_ITEM)
                .allMatch(block -> block.indent() == 1));
        List<InlineItem> inlineItems = notesCell.content().stream().filter(TextBlock.class::isInstance)
                .map(TextBlock.class::cast).flatMap(block -> block.text().runs().stream())
                .map(DocumentBlock.TextRun::inlineItem).filter(java.util.Objects::nonNull).toList();
        assertEquals(List.of(InlineItem.GOLD_INGOT, InlineItem.IRON_INGOT, InlineItem.COPPER_INGOT), inlineItems);

        RuleBox box = (RuleBox) document.blocks().get(rule.blockIndex());
        assertTrue(box.body().stream().anyMatch(ImageBlock.class::isInstance));
        ImageBlock image = box.body().stream().filter(ImageBlock.class::isInstance).map(ImageBlock.class::cast)
                .findFirst().orElseThrow();
        assertEquals(100, image.displayWidth());
        assertEquals(50, image.displayHeight());
        List<TextBlock> text = box.body().stream().filter(TextBlock.class::isInstance).map(TextBlock.class::cast).toList();
        assertTrue(text.stream().anyMatch(line -> line.text().plainText().startsWith("a. First condition")));
        assertTrue(text.stream().anyMatch(line -> line.indent() == 1 && line.text().plainText().contains("Nested note")));

        List<DocumentBlock.TextRun> allRuns = document.blocks().stream()
                .filter(TextBlock.class::isInstance).map(TextBlock.class::cast)
                .flatMap(block -> block.text().runs().stream()).toList();
        assertTrue(allRuns.stream().anyMatch(run -> "https://example.com/rules".equals(run.href())));
        assertTrue(allRuns.stream().noneMatch(run -> run.href() != null && run.href().startsWith("javascript:")));
    }

    @Test
    void banLogReasonStopsAfterTheFirstSentence() {
        RulebookRule rule = new RulebookRule("9.9", "Section", "First 1.5 sentence. Second sentence.",
                "Warning, then 7 day ban, permanent ban. Extra action.\nItems removed.", 0xFFFFFF, 0, "search");
        assertEquals("9.9 First 1.5 sentence.", rule.reason());
        assertEquals(List.of("Warning", "7 day ban", "permanent ban"), rule.punishmentChoices());
    }

    @Test
    void rejectsDuplicateRules() {
        String duplicate = RulebookFixtures.document().replace("</body>",
                "<table><tr><td>18.7</td><td><p>Duplicate title</p></td></tr></table></body>");
        IOException error = assertThrows(IOException.class, () -> GoogleDocsRulebookParser.parse(duplicate));
        assertTrue(error.getMessage().contains("Duplicate rule id"));
    }

    @Test
    void doesNotFetchRemoteImagesAndUsesAPlaceholder() throws Exception {
        String remote = RulebookFixtures.document().replace("<p><img alt=\"pixel\"",
                "<p><img src=\"https://example.com/tracker.png\"></p><p><img alt=\"pixel\"");
        RulebookDocument document = GoogleDocsRulebookParser.parse(remote);
        RuleBox box = (RuleBox) document.blocks().get(document.rule("18.7").orElseThrow().blockIndex());
        assertTrue(box.body().stream().filter(TextBlock.class::isInstance).map(TextBlock.class::cast)
                .anyMatch(block -> block.text().plainText().contains("Image unavailable")));
    }
}
