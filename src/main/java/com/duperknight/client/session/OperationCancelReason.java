package com.duperknight.client.session;

/** Why a managed operation stopped before normal completion. */
public enum OperationCancelReason {
    USER_REQUESTED,
    MODULE_REQUESTED,
    CONNECTION_CHANGED,
    DISPATCH_BLOCKED,
    INTERNAL_ERROR
}
