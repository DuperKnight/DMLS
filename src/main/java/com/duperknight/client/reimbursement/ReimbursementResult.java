package com.duperknight.client.reimbursement;

import java.util.List;

/** Typed terminal presentation state for live and simulated reimbursements. */
public record ReimbursementResult(
        Kind kind,
        String log,
        List<String> completed,
        List<String> retainedWithStaff,
        List<String> remaining,
        String message
) {
    public ReimbursementResult {
        log = log == null ? "" : log;
        completed = List.copyOf(completed == null ? List.of() : completed);
        retainedWithStaff = List.copyOf(retainedWithStaff == null ? List.of() : retainedWithStaff);
        remaining = List.copyOf(remaining == null ? List.of() : remaining);
        message = message == null ? "" : message;
    }

    public enum Kind {
        SUCCESS,
        SUCCESS_WITH_WARNINGS,
        DRY_RUN,
        PREFLIGHT_REJECTED,
        PARTIAL_FAILURE
    }

    public boolean requiresLogCopy() {
        return kind == Kind.SUCCESS || kind == Kind.SUCCESS_WITH_WARNINGS;
    }
}
