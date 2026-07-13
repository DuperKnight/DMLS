package com.duperknight.client.modules;

import com.duperknight.client.modules.PunishmentHelperModule.BanRequest;
import com.duperknight.client.modules.PunishmentHelperModule.BanValidation;
import com.duperknight.client.modules.PunishmentHelperModule.Rule;
import com.duperknight.client.session.PendingConfirmation;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PunishmentHelperModuleTest {
    @Test
    void preparesAndNormalizesStrictBanRequests() {
        var preparation = PunishmentHelperModule.prepareBan(" Alice_1 ", " 7D ", " Rule 1.1 grief ");

        assertTrue(preparation.isValid());
        assertEquals(new BanRequest("Alice_1", "7d", "Rule 1.1 grief"), preparation.request());
        assertEquals("ban Alice_1 7d Rule 1.1 grief", preparation.request().command());
    }

    @Test
    void rejectsUnsafeOrAmbiguousBanFields() {
        assertEquals(BanValidation.INVALID_IGN,
                PunishmentHelperModule.prepareBan("bad-name", "7d", "reason").validation());
        assertEquals(BanValidation.INVALID_DURATION,
                PunishmentHelperModule.prepareBan("Alice", "0d", "reason").validation());
        assertEquals(BanValidation.INVALID_DURATION,
                PunishmentHelperModule.prepareBan("Alice", "7d extra", "reason").validation());
        assertEquals(BanValidation.INVALID_DURATION,
                PunishmentHelperModule.prepareBan("Alice", "1h30m", "reason").validation());
        assertEquals(BanValidation.INVALID_REASON,
                PunishmentHelperModule.prepareBan("Alice", "perm", " ").validation());
        assertEquals(BanValidation.INVALID_REASON,
                PunishmentHelperModule.prepareBan("Alice", "perm", "line\nbreak").validation());
        assertEquals(BanValidation.INVALID_REASON,
                PunishmentHelperModule.prepareBan("Alice", "perm", "x".repeat(201)).validation());
        assertThrows(IllegalArgumentException.class, () -> new BanRequest("bad-name", "7d", "reason"));
    }

    @Test
    void confirmationFreezesTheBanRequestAndIsSingleUse() {
        BanRequest request = new BanRequest("Alice", "7d", "Rule 1.1");
        PendingConfirmation<BanRequest> pending = new PendingConfirmation<>(request);

        assertEquals(request, pending.request());
        assertEquals(PendingConfirmation.ConsumeStatus.INVALID_TOKEN,
                pending.consume("wrong").status());
        assertEquals(PendingConfirmation.ConsumeStatus.CONFIRMED,
                pending.consume(pending.token()).status());
        assertEquals(PendingConfirmation.ConsumeStatus.ALREADY_CONSUMED,
                pending.consume(pending.token()).status());
    }

    @Test
    void parsesTypedRulebookAndPreservesJsonEscapes() throws Exception {
        List<Rule> rules = PunishmentHelperModule.parseRules(new StringReader("""
                {"rules":[{"id":"1.1","section":"Section","title":"A \\"quoted\\" title","punishment":"Warn\\nthen ban"}]}
                """));

        assertEquals(List.of(new Rule("1.1", "Section", "A \"quoted\" title", "Warn\nthen ban")), rules);
    }

    @Test
    void rejectsMissingFieldsDuplicateIdsAndMalformedDocuments() {
        assertThrows(IOException.class, () -> PunishmentHelperModule.parseRules(new StringReader("""
                {"rules":[{"id":"1.1","section":"S","title":"T"}]}
                """)));
        assertThrows(IOException.class, () -> PunishmentHelperModule.parseRules(new StringReader("""
                {"rules":[
                  {"id":"1.1","section":"S","title":"T","punishment":"P"},
                  {"id":"1.1","section":"Other","title":"T","punishment":"P"}
                ]}
                """)));
        assertThrows(IOException.class, () -> PunishmentHelperModule.parseRules(new StringReader("[]")));
        assertThrows(IOException.class, () -> PunishmentHelperModule.parseRules(new StringReader("{not json}")));
    }

    @Test
    void rejectsDuplicateJsonMembersAndCaseInsensitiveRuleIds() {
        assertInvalidRulebook("""
                {"rules":[{"id":"1.1","section":"S","title":"T","punishment":"P"}],
                 "rules":[{"id":"2.1","section":"S","title":"T","punishment":"P"}]}
                """);
        assertInvalidRulebook("""
                {"rules":[{"id":"1.1","id":"2.1","section":"S","title":"T","punishment":"P"}]}
                """);
        assertInvalidRulebook("""
                {"rules":[
                  {"id":"Rule-A","section":"S","title":"T","punishment":"P"},
                  {"id":"rule-a","section":"S","title":"Other","punishment":"P"}
                ]}
                """);
    }

    @Test
    void rejectsUnknownNullAndNonStringRuleFields() {
        assertInvalidRulebook("""
                {"rules":[{"id":"1.1","section":"S","title":"T","punishment":"P","extra":"x"}]}
                """);
        assertInvalidRulebook("""
                {"rules":[{"id":1.1,"section":"S","title":"T","punishment":"P"}]}
                """);
        assertInvalidRulebook("""
                {"rules":[{"id":"1.1","section":true,"title":"T","punishment":"P"}]}
                """);
        assertInvalidRulebook("""
                {"rules":[{"id":"1.1","section":"S","title":null,"punishment":"P"}]}
                """);
        assertInvalidRulebook("""
                {"rules":[{"id":"1.1","section":"S","title":" ","punishment":"P"}]}
                """);
    }

    @Test
    void bundledRulebookIsValidAndHasUniqueCompleteRules() throws Exception {
        try (InputStream input = getClass().getResourceAsStream("/assets/dmls/rulebook.json")) {
            assertTrue(input != null, "bundled rulebook resource is missing");
            List<Rule> rules = PunishmentHelperModule.parseRules(
                    new InputStreamReader(input, StandardCharsets.UTF_8));
            assertEquals(145, rules.size());
            assertEquals(rules.size(), rules.stream().map(Rule::id).distinct().count());
            assertTrue(rules.stream().allMatch(rule -> !rule.id().isBlank()
                    && !rule.section().isBlank() && !rule.title().isBlank() && !rule.punishment().isBlank()));
        }
    }

    private static void assertInvalidRulebook(String json) {
        assertThrows(IOException.class, () -> PunishmentHelperModule.parseRules(new StringReader(json)));
    }
}
