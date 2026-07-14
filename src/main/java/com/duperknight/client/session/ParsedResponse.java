package com.duperknight.client.session;

import java.util.Objects;
import java.util.Optional;

/** A response classification with an optional parser-specific payload. */
public record ParsedResponse<T>(ResponseStatus status, Optional<T> value) {
    public ParsedResponse {
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(value, "value");
    }

    public static <T> ParsedResponse<T> unrelated() {
        return withoutValue(ResponseStatus.UNRELATED);
    }

    public static <T> ParsedResponse<T> progress() {
        return withoutValue(ResponseStatus.PROGRESS);
    }

    public static <T> ParsedResponse<T> progress(T value) {
        return new ParsedResponse<>(ResponseStatus.PROGRESS, Optional.of(value));
    }

    public static <T> ParsedResponse<T> confirmed() {
        return withoutValue(ResponseStatus.CONFIRMED);
    }

    public static <T> ParsedResponse<T> confirmed(T value) {
        return new ParsedResponse<>(ResponseStatus.CONFIRMED, Optional.of(value));
    }

    public static <T> ParsedResponse<T> rejected() {
        return withoutValue(ResponseStatus.REJECTED);
    }

    public static <T> ParsedResponse<T> rejected(T value) {
        return new ParsedResponse<>(ResponseStatus.REJECTED, Optional.of(value));
    }

    private static <T> ParsedResponse<T> withoutValue(ResponseStatus status) {
        return new ParsedResponse<>(status, Optional.empty());
    }
}
