package com.duperknight.client.parser;

/** Exact matcher for the Stoneworks LuckPerms lines supplied by staff. */
public final class LuckPermsResponseParser {
    private LuckPermsResponseParser() {
    }

    public static Result parsePermissionAlreadySet(String username, String permission, String message) {
        return equals(message, "[LP] " + username + " already has " + permission + " set in context global.")
                ? Result.CONFIRMED : Result.UNRELATED;
    }

    /**
     * Correlates a donor permission response with the exact username and permission requested.
     * Unknown output remains unrelated and therefore times out as sent-but-unverified.
     */
    public static PermissionResult parsePermissionSet(String username, String permission, String message) {
        if (equals(message, "[LP] Set " + permission + " to true for " + username + " in context global.")
                || equals(message, "[LP] " + username + " already has " + permission
                + " set in context global.")) {
            return PermissionResult.CONFIRMED;
        }
        if (equals(message, "[LP] User " + username + " could not be found.")
                || equals(message, "[LP] User " + username + " does not exist.")
                || equals(message, "[LP] You do not have permission to use this command.")
                || equals(message, "I'm sorry, but you do not have permission to perform this command.")) {
            return PermissionResult.REJECTED;
        }
        return PermissionResult.UNRELATED;
    }

    public static Result parseParentChange(Action action, String username, String group, String message) {
        String expected = switch (action) {
            case ADD -> "[LP] " + username + " now inherits permissions from " + group + " in context global.";
            case REMOVE -> "[LP] " + username + " no longer inherits permissions from " + group + " in context global.";
        };
        if (equals(message, expected)) return Result.CONFIRMED;
        if (equals(message, "[LP] User " + username + " could not be found.")
                || equals(message, "[LP] User " + username + " does not exist.")
                || equals(message, "[LP] Group " + group + " could not be found.")
                || equals(message, "[LP] You do not have permission to use this command.")
                || equals(message, "I'm sorry, but you do not have permission to perform this command.")) {
            return Result.REJECTED;
        }
        return Result.UNRELATED;
    }

    private static boolean equals(String actual, String expected) {
        return actual != null && actual.trim().equalsIgnoreCase(expected);
    }

    public enum Action { ADD, REMOVE }
    public enum Result { CONFIRMED, REJECTED, UNRELATED }
    public enum PermissionResult { CONFIRMED, REJECTED, UNRELATED }
}
