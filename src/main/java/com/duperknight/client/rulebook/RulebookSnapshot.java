package com.duperknight.client.rulebook;

import java.time.Instant;

public record RulebookSnapshot(RulebookStatus status, RulebookDocument document, Instant fetchedAt,
                               String error, boolean refreshing, long revision) {
    public boolean hasDocument() {
        return document != null;
    }
}
