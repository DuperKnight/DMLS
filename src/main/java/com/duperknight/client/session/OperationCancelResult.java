package com.duperknight.client.session;

/** Result of a global or module-scoped cancellation request. */
public enum OperationCancelResult {
    CANCELLED,
    NO_ACTIVE_OPERATION,
    NOT_OWNER
}
