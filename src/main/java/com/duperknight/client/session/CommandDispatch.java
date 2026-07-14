package com.duperknight.client.session;

/** Truthful result of attempting to dispatch a command or chat message. */
public enum CommandDispatch {
    /** The payload was sent to the currently connected server. */
    SENT,
    /** The operation captured dry-run mode, so the payload was only shown locally. */
    SIMULATED,
    /** Dispatch was refused because validation or connection safety failed. */
    BLOCKED;

    public boolean accepted() {
        return this != BLOCKED;
    }
}
