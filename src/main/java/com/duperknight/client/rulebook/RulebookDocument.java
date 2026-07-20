package com.duperknight.client.rulebook;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public record RulebookDocument(List<DocumentBlock> blocks, List<RulebookRule> rules,
                               Map<String, RulebookRule> rulesById) {
    public RulebookDocument(List<DocumentBlock> blocks, List<RulebookRule> rules) {
        this(List.copyOf(blocks), List.copyOf(rules), index(rules));
    }

    public RulebookDocument {
        blocks = List.copyOf(blocks);
        rules = List.copyOf(rules);
        rulesById = Map.copyOf(rulesById);
        if (rules.isEmpty()) throw new IllegalArgumentException("Rulebook must contain rules");
    }

    public Optional<RulebookRule> rule(String id) {
        return Optional.ofNullable(rulesById.get(id.toLowerCase(Locale.ROOT)));
    }

    private static Map<String, RulebookRule> index(List<RulebookRule> rules) {
        Map<String, RulebookRule> indexed = new LinkedHashMap<>();
        for (RulebookRule rule : rules) {
            String key = rule.id().toLowerCase(Locale.ROOT);
            if (indexed.putIfAbsent(key, rule) != null) {
                throw new IllegalArgumentException("Duplicate rule id: " + rule.id());
            }
        }
        return indexed;
    }
}
