package com.duperknight.client.instaban;

import java.time.Instant;
import java.util.List;

public record InstaBanCheckResponse(
        String indexStatus,
        Instant indexCheckedAt,
        List<InstaBanResult> results
) {
    public InstaBanCheckResponse {
        results = List.copyOf(results);
    }

    public boolean stale() {
        return "stale".equalsIgnoreCase(indexStatus);
    }
}
