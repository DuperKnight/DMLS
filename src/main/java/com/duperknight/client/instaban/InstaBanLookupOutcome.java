package com.duperknight.client.instaban;

public record InstaBanLookupOutcome(Type type, InstaBanCheckResponse response) {
    public enum Type {
        SUCCESS,
        NOT_LINKED,
        INVALID_TOKEN,
        AUTHORIZATION_STALE,
        RATE_LIMITED,
        BAD_REQUEST,
        CONFIGURATION_ERROR,
        TEMPORARY_ERROR,
        MALFORMED_RESPONSE
    }

    public static InstaBanLookupOutcome success(InstaBanCheckResponse response) {
        return new InstaBanLookupOutcome(Type.SUCCESS, response);
    }

    public static InstaBanLookupOutcome failure(Type type) {
        return new InstaBanLookupOutcome(type, null);
    }
}
