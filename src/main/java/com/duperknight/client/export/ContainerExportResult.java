package com.duperknight.client.export;

import java.util.List;

/** Outcome of one container export run. */
public record ContainerExportResult(
        Kind kind,
        String filePath,
        int exported,
        int skipped,
        int itemCount,
        List<String> notes,
        String message
) {
    public enum Kind { SUCCESS, PARTIAL, DRY_RUN, FAILED, CANCELLED }

    public ContainerExportResult {
        filePath = filePath == null ? "" : filePath;
        notes = notes == null ? List.of() : List.copyOf(notes);
        message = message == null ? "" : message;
    }

    public boolean isError() {
        return kind == Kind.FAILED || kind == Kind.CANCELLED;
    }
}
