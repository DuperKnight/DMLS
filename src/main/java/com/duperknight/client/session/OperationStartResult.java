package com.duperknight.client.session;

/** Result of trying to acquire the global operation slot. */
public enum OperationStartResult {
    STARTED,
    BUSY,
    SERVER_BLOCKED,
    INVALID,
    FAILED_TO_START
}
